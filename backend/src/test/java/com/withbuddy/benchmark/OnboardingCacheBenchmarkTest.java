package com.withbuddy.benchmark;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.service.ChatMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.onboarding.service.OnboardingSuggestionService;
import com.withbuddy.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis 도입 전후 성능 비교 벤치마크
 *
 * 측정 대상: OnboardingSuggestionService.getMyOnboardingSuggestions()
 *
 * [A] Before Redis
 *     - get()          → 항상 MISS
 *     - putIfAbsent()  → 항상 true
 *     - 결과: 매 요청마다 user + suggestion + nudge(existsBy+save) 전체 DB 경로 실행
 *
 * [B] After Redis (Warm Cache)
 *     - user:profile / buddy:day / quicktap / nudge:sent 모두 캐시됨
 *     - 결과: suggestion DB만 실행, user + nudge DB 쿼리 스킵
 *
 * DB 레이턴시 시뮬레이션: Thread.sleep(DB_LATENCY_MS)으로 실제 MySQL ~5ms 모사
 * 외부 의존성 없음 — Redis/MySQL 없이 로컬 실행 가능
 */
class OnboardingCacheBenchmarkTest {

    // ── 벤치마크 파라미터 ──────────────────────────────────────────────
    private static final int  WARMUP_ITERS  = 10;
    private static final int  MEASURE_ITERS = 200;
    private static final long DB_LATENCY_MS = 5L;   // MySQL 평균 RTT 모사

    // ── 테스트 픽스처 ──────────────────────────────────────────────────
    private static final Long USER_ID       = 1L;
    private static final int  DAY_OFFSET    = 5;    // 입사 5일차
    private static final Long SUGGESTION_ID = 4L;   // resolveSuggestionId(5) == 4L

    private UserRepository                userRepo;
    private OnboardingSuggestionRepository suggestionRepo;
    private ChatMessageService             chatService;

    @BeforeEach
    void setUpMocks() throws Exception {
        userRepo      = mock(UserRepository.class);
        suggestionRepo = mock(OnboardingSuggestionRepository.class);
        chatService   = mock(ChatMessageService.class);

        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockUser.getHireDate()).thenReturn(LocalDate.now().minusDays(DAY_OFFSET));
        when(mockUser.getName()).thenReturn("테스트유저");

        OnboardingSuggestion mockSuggestion = mock(OnboardingSuggestion.class);
        when(mockSuggestion.getId()).thenReturn(SUGGESTION_ID);
        when(mockSuggestion.getTitle()).thenReturn("입사 5일차 온보딩");
        when(mockSuggestion.getContent()).thenReturn("{이름}님, 입사 {N}일이 지났습니다.");
        when(mockSuggestion.getCreatedAt()).thenReturn(LocalDateTime.now());

        // ── DB 레이턴시 시뮬레이션 ──────────────────────────────────────
        when(userRepo.findById(USER_ID)).thenAnswer(inv -> {
            Thread.sleep(DB_LATENCY_MS);
            return Optional.of(mockUser);
        });
        when(suggestionRepo.findById(SUGGESTION_ID)).thenAnswer(inv -> {
            Thread.sleep(DB_LATENCY_MS);
            return Optional.of(mockSuggestion);
        });
        // saveSuggestionMessageIfNotExists = existsBy + (optional) save
        doAnswer(inv -> {
            Thread.sleep(DB_LATENCY_MS);
            return null;
        }).when(chatService).saveSuggestionMessageIfNotExists(anyLong(), anyLong(), anyString());
    }

    // ── 메인 벤치마크 ─────────────────────────────────────────────────

    @Test
    void benchmark_before_and_after_redis() throws Exception {
        long[] before = measure(buildNoCache());
        long[] after  = measure(buildWarmCache());

        printReport(before, after);
    }

    // ── RedisCacheService 구현 ─────────────────────────────────────────

    /**
     * Redis 없음 (Before).
     * get()         → 항상 Optional.empty()
     * putIfAbsent() → 항상 true  (nudge 중복 방지 없음 → 매번 DB 호출)
     */
    private RedisCacheService buildNoCache() {
        RedisCacheService svc = mock(RedisCacheService.class);
        when(svc.get(anyString())).thenReturn(Optional.empty());
        when(svc.putIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        return svc;
    }

    /**
     * Warm Cache (After).
     * user:profile / buddy:day / quicktap / nudge:sent 모두 사전 적재.
     * putIfAbsent("nudge:sent:...") → false (이미 존재)
     *   → chatService.saveSuggestionMessageIfNotExists() 호출 안 함
     */
    private RedisCacheService buildWarmCache() {
        Map<String, String> store = new ConcurrentHashMap<>();
        store.put("user:profile:" + USER_ID, "{\"name\":\"테스트유저\",\"hireDate\":\"" + LocalDate.now().minusDays(DAY_OFFSET) + "\"}");
        store.put("buddy:day:"  + USER_ID,                    String.valueOf(DAY_OFFSET));
        store.put("quicktap:"   + DAY_OFFSET,                 String.valueOf(SUGGESTION_ID));
        store.put("nudge:sent:" + USER_ID + ":" + DAY_OFFSET, "1");

        RedisCacheService svc = mock(RedisCacheService.class);
        when(svc.get(anyString())).thenAnswer(inv ->
            Optional.ofNullable(store.get((String) inv.getArgument(0))));
        when(svc.putIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            return store.putIfAbsent(key, val) == null; // 이미 존재 → false
        });
        return svc;
    }

    // ── 실행 ──────────────────────────────────────────────────────────

    private long[] measure(RedisCacheService redis) throws Exception {
        OnboardingSuggestionService service =
            new OnboardingSuggestionService(userRepo, suggestionRepo, chatService, redis, new ObjectMapper());

        // 웜업 (JIT 컴파일 안정화)
        for (int i = 0; i < WARMUP_ITERS; i++) {
            service.getMyOnboardingSuggestions(USER_ID);
        }

        long[] times = new long[MEASURE_ITERS];
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long start = System.nanoTime();
            service.getMyOnboardingSuggestions(USER_ID);
            times[i] = (System.nanoTime() - start) / 1_000_000; // ns → ms
        }
        return times;
    }

    // ── 결과 출력 ─────────────────────────────────────────────────────

    private void printReport(long[] before, long[] after) {
        long avgBefore = avg(before);
        long avgAfter  = avg(after);
        long saved     = avgBefore - avgAfter;
        double pct     = (double) saved / avgBefore * 100.0;

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║        Redis 도입 전후 성능 비교 — OnboardingSuggestion               ║");
        System.out.printf ("║        DB 레이턴시 %dms 시뮬레이션  /  측정 %d회                       ║%n",
                DB_LATENCY_MS, MEASURE_ITERS);
        System.out.println("╠══════════════╦══════════╦══════════╦══════════╦══════════╦════════════╣");
        System.out.println("║   시나리오    ║   AVG    ║   P50    ║   P95    ║   P99    ║ DB 쿼리 수 ║");
        System.out.println("╠══════════════╬══════════╬══════════╬══════════╬══════════╬════════════╣");
        printRow("Before (A)  ", before, "3 쿼리");
        printRow("After  (B)  ", after,  "2 쿼리");
        System.out.println("╠══════════════╩══════════╩══════════╩══════════╩══════════╩════════════╣");
        System.out.printf ("║  개선: %dms → %dms  |  요청당 ~%dms 절감  |  %.1f%% 빨라짐              %n",
                avgBefore, avgAfter, saved, pct);
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  [절감 근거]                                                          ║");
        System.out.println("║    nudge:sent SETNX HIT → saveSuggestionMessageIfNotExists() 스킵     ║");
        System.out.println("║    (chatMessageRepository.existsBy + optional save 생략)              ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  [미절감 — 항상 DB 호출]                                              ║");
        System.out.println("║    suggestionRepo.findById()  → suggestion 캐싱 미구현                 ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  [추가 개선 가능]                                                     ║");
        System.out.println("║    suggestion 읽기 캐싱 시 1쿼리 → 0쿼리                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printRow(String label, long[] times, String dbNote) {
        long[] sorted = Arrays.copyOf(times, times.length);
        Arrays.sort(sorted);
        System.out.printf("║ %-12s ║ %6dms ║ %6dms ║ %6dms ║ %6dms ║ %-10s ║%n",
            label,
            avg(times),
            sorted[sorted.length / 2],
            sorted[(int) (sorted.length * 0.95)],
            sorted[(int) (sorted.length * 0.99)],
            dbNote);
    }

    private long avg(long[] times) {
        return (long) Arrays.stream(times).average().orElse(0);
    }
}
