package com.withbuddy.admin.metrics.service;

import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.admin.metrics.dto.response.AdminDashboardResponse;
import com.withbuddy.admin.metrics.dto.response.DocumentGapRateResponse;
import com.withbuddy.admin.metrics.dto.response.FirstInteractionRateResponse;
import com.withbuddy.admin.metrics.dto.response.InternalAdminDashboardResponse;
import com.withbuddy.admin.metrics.dto.response.NonRagRateResponse;
import com.withbuddy.admin.metrics.dto.response.RagExperienceRateResponse;
import com.withbuddy.admin.metrics.dto.response.RevisitRateResponse;
import com.withbuddy.admin.metrics.dto.response.TtaResponse;
import com.withbuddy.admin.metrics.dto.response.TtaUnreachedResponse;
import com.withbuddy.admin.metrics.dto.response.UnansweredQuestionPatternsResponse;
import com.withbuddy.admin.metrics.dto.response.UnansweredRateResponse;
import com.withbuddy.admin.metrics.dto.response.UnstartedUsersResponse;
import com.withbuddy.admin.metrics.repository.AdminMetricsRepository;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.infrastructure.ai.client.AiNoResultSummaryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional(readOnly = true)
@Slf4j
public class AdminMetricsService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_PATTERN_LIMIT = 5;
    private static final int MAX_PATTERN_LIMIT = 20;

    private final AdminMetricsRepository adminMetricsRepository;
    private final UserRepository userRepository;
    private final AiNoResultSummaryClient aiNoResultSummaryClient;

    public AdminMetricsService(
            AdminMetricsRepository adminMetricsRepository,
            UserRepository userRepository,
            AiNoResultSummaryClient aiNoResultSummaryClient
    ) {
        this.adminMetricsRepository = adminMetricsRepository;
        this.userRepository = userRepository;
        this.aiNoResultSummaryClient = aiNoResultSummaryClient;
    }

    public RagExperienceRateResponse getRagExperienceRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate cohortStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<RagExperienceRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findRagExperienceRateMetrics(scopedCompanyCode, cohortStartDate, resolvedAsOfDate).stream()
                        .map(metric -> new RagExperienceRateResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getTargetUsers()),
                                defaultLong(metric.getRagReceivedUsers()),
                                calculateRate(metric.getRagReceivedUsers(), metric.getTargetUsers())
                        ))
                        .toList();

        return new RagExperienceRateResponse("rag_experience_rate", resolvedAsOfDate, companies);
    }

    public DocumentGapRateResponse getDocumentGapRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate windowStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<DocumentGapRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findDocumentGapRateMetrics(scopedCompanyCode, windowStartDate, resolvedAsOfDate).stream()
                        .map(metric -> new DocumentGapRateResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getAnswerableBotAnswers()),
                                defaultLong(metric.getNoResultAnswers()),
                                calculateRate(metric.getNoResultAnswers(), metric.getAnswerableBotAnswers())
                        ))
                        .toList();

        return new DocumentGapRateResponse("document_gap_rate", resolvedAsOfDate, companies);
    }

    public UnstartedUsersResponse getUnstartedUsers(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);

        List<UnstartedUsersResponse.CompanyMetric> companies =
                adminMetricsRepository.findUnstartedUsersMetrics(scopedCompanyCode, resolvedAsOfDate).stream()
                        .map(metric -> new UnstartedUsersResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getActiveNewUsers()),
                                defaultLong(metric.getUnstartedUsers())
                        ))
                        .toList();

        return new UnstartedUsersResponse("unstarted_users", resolvedAsOfDate, companies);
    }

    public AdminDashboardResponse getDashboard(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate,
            Integer unansweredPatternLimit
    ) {
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);

        return new AdminDashboardResponse(
                "admin_dashboard",
                resolvedAsOfDate,
                getRagExperienceRate(principal, companyCode, resolvedAsOfDate),
                getDocumentGapRate(principal, companyCode, resolvedAsOfDate),
                getUnstartedUsers(principal, companyCode, resolvedAsOfDate),
                getUnansweredQuestionPatterns(principal, companyCode, resolvedAsOfDate, unansweredPatternLimit)
        );
    }

    public FirstInteractionRateResponse getFirstInteractionRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate cohortStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<FirstInteractionRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findFirstInteractionRateMetrics(scopedCompanyCode, cohortStartDate, resolvedAsOfDate).stream()
                        .map(metric -> new FirstInteractionRateResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getTargetUsers()),
                                defaultLong(metric.getFirstInteractionUsers()),
                                calculateRate(metric.getFirstInteractionUsers(), metric.getTargetUsers())
                        ))
                        .toList();

        return new FirstInteractionRateResponse("first_interaction_rate", resolvedAsOfDate, companies);
    }

    public RevisitRateResponse getRevisitRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate cohortStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<RevisitRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findRevisitRateMetrics(scopedCompanyCode, cohortStartDate, resolvedAsOfDate).stream()
                        .map(metric -> new RevisitRateResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getD0Users()),
                                defaultLong(metric.getRevisitUsers()),
                                calculateRate(metric.getRevisitUsers(), metric.getD0Users())
                        ))
                        .toList();

        return new RevisitRateResponse("revisit_rate", resolvedAsOfDate, companies);
    }

    public UnansweredRateResponse getUnansweredRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate windowStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<UnansweredRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findUnansweredRateMetrics(scopedCompanyCode, windowStartDate, resolvedAsOfDate).stream()
                        .map(metric -> new UnansweredRateResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getTotalAiAnswers()),
                                defaultLong(metric.getNoResultAnswers()),
                                calculateRate(metric.getNoResultAnswers(), metric.getTotalAiAnswers()),
                                defaultLong(metric.getOutOfScopeAnswers()),
                                calculateRate(metric.getOutOfScopeAnswers(), metric.getTotalAiAnswers()),
                                defaultLong(metric.getSensitiveAnswers()),
                                calculateRate(metric.getSensitiveAnswers(), metric.getTotalAiAnswers())
                        ))
                        .toList();

        return new UnansweredRateResponse("unanswered_rate", resolvedAsOfDate, companies);
    }

    public NonRagRateResponse getNonRagRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate windowStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<NonRagRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findUnansweredRateMetrics(normalizeCompanyCode(companyCode), windowStartDate, resolvedAsOfDate).stream()
                        .map(metric -> {
                            long nonRagAnswers = defaultLong(metric.getNoResultAnswers())
                                    + defaultLong(metric.getOutOfScopeAnswers())
                                    + defaultLong(metric.getSensitiveAnswers());
                            return new NonRagRateResponse.CompanyMetric(
                                    metric.getCompanyCode(),
                                    metric.getCompanyName(),
                                    defaultLong(metric.getTotalAiAnswers()),
                                    nonRagAnswers,
                                    calculateRate(nonRagAnswers, metric.getTotalAiAnswers()),
                                    defaultLong(metric.getNoResultAnswers()),
                                    defaultLong(metric.getOutOfScopeAnswers()),
                                    defaultLong(metric.getSensitiveAnswers()),
                                    calculateRate(metric.getSensitiveAnswers(), metric.getTotalAiAnswers())
                            );
                        })
                        .toList();

        return new NonRagRateResponse("non_rag_rate", resolvedAsOfDate, companies);
    }

    public TtaResponse getTta(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate windowStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<TtaResponse.CompanyMetric> companies =
                adminMetricsRepository.findTtaMetrics(scopedCompanyCode, windowStartDate, resolvedAsOfDate).stream()
                        .map(metric -> new TtaResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getLoggedInUsers()),
                                defaultLong(metric.getMeasuredUsers()),
                                metric.getAverageTtaMinutes()
                        ))
                        .toList();

        return new TtaResponse("tta", resolvedAsOfDate, "minutes", companies);
    }

    public TtaUnreachedResponse getTtaUnreached(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate windowStartDate = resolveRollingStartDate(resolvedAsOfDate);

        List<TtaUnreachedResponse.CompanyMetric> companies =
                adminMetricsRepository.findTtaUnreachedMetrics(normalizeCompanyCode(companyCode), windowStartDate, resolvedAsOfDate).stream()
                        .map(metric -> new TtaUnreachedResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getLoggedInUsers()),
                                defaultLong(metric.getUnreachedUsers()),
                                calculateRate(metric.getUnreachedUsers(), metric.getLoggedInUsers())
                        ))
                        .toList();

        return new TtaUnreachedResponse("tta_unreached", resolvedAsOfDate, companies);
    }

    public UnansweredQuestionPatternsResponse getUnansweredQuestionPatterns(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate,
            Integer limit
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate windowStartDate = resolveTopFiveStartDate(resolvedAsOfDate);
        int resolvedLimit = resolvePatternLimit(limit);

        List<UnansweredQuestionPatternsResponse.PatternItem> patterns =
                adminMetricsRepository.findUnansweredQuestionPatterns(
                                scopedCompanyCode,
                                windowStartDate,
                                resolvedAsOfDate,
                                PageRequest.of(0, resolvedLimit)
                        ).stream()
                        .map(pattern -> new UnansweredQuestionPatternsResponse.PatternItem(
                                pattern.getCompanyCode(),
                                pattern.getQuestionContent(),
                                defaultLong(pattern.getTotalCount()),
                                defaultLong(pattern.getUniqueUsers()),
                                defaultLong(pattern.getNoResultCount()),
                                defaultLong(pattern.getOutOfScopeCount()),
                                pattern.getLatestOccurredAt()
                        ))
                        .toList();

        return new UnansweredQuestionPatternsResponse(
                "unanswered_question_patterns",
                resolvedAsOfDate,
                resolvedLimit,
                patterns,
                buildAiSummary(scopedCompanyCode, patterns)
        );
    }

    public InternalAdminDashboardResponse getInternalDashboard(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);

        return new InternalAdminDashboardResponse(
                "internal_admin_dashboard",
                resolvedAsOfDate,
                getFirstInteractionRate(principal, companyCode, resolvedAsOfDate),
                getRevisitRate(principal, companyCode, resolvedAsOfDate),
                getTta(principal, companyCode, resolvedAsOfDate),
                getTtaUnreached(principal, companyCode, resolvedAsOfDate),
                getNonRagRate(principal, companyCode, resolvedAsOfDate)
        );
    }

    private void requireServiceAdmin(JwtAuthenticationPrincipal principal) {
        User currentUser = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));

        if (currentUser.getRole() != UserRole.SERVICE_ADMIN) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "서비스 관리자 권한이 필요한 API입니다.");
        }
    }

    private String resolveCompanyScope(JwtAuthenticationPrincipal principal, String requestedCompanyCode) {
        User currentUser = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));

        if (currentUser.getRole() == UserRole.SERVICE_ADMIN) {
            return normalizeCompanyCode(requestedCompanyCode);
        }

        if (!currentUser.isActiveAdmin()) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "관리자 권한이 필요한 API입니다.");
        }

        String principalCompanyCode = principal.companyCode();
        if (!StringUtils.hasText(principalCompanyCode)) {
            throw new UnauthorizedException("사용자 회사 정보를 확인할 수 없습니다.");
        }

        String currentUserCompanyCode = currentUser.getCompany().getCompanyCode();
        if (!currentUserCompanyCode.equals(principalCompanyCode)) {
            throw new UnauthorizedException("사용자 회사 정보가 일치하지 않습니다.");
        }

        String normalizedRequestedCompanyCode = normalizeCompanyCode(requestedCompanyCode);
        if (StringUtils.hasText(normalizedRequestedCompanyCode)
                && !currentUserCompanyCode.equals(normalizedRequestedCompanyCode)) {
            throw new ForbiddenException("ACCESS_DENIED", "companyCode", "다른 회사 지표는 조회할 수 없습니다.");
        }

        return currentUserCompanyCode;
    }

    private LocalDate resolveAsOfDate(LocalDate asOfDate) {
        if (asOfDate != null) {
            return asOfDate;
        }
        return LocalDate.now(KOREA_ZONE_ID);
    }

    private LocalDate resolveRollingStartDate(LocalDate asOfDate) {
        return asOfDate.minusDays(29);
    }

    private LocalDate resolveTopFiveStartDate(LocalDate asOfDate) {
        return asOfDate.minusDays(6);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private double calculateRate(Long numerator, Long denominator) {
        long resolvedDenominator = defaultLong(denominator);
        if (resolvedDenominator == 0L) {
            return 0.0;
        }

        double ratio = defaultLong(numerator) * 100.0 / resolvedDenominator;
        return Math.round(ratio * 10.0) / 10.0;
    }

    private double calculateRate(long numerator, Long denominator) {
        return calculateRate(Long.valueOf(numerator), denominator);
    }

    private int resolvePatternLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PATTERN_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_PATTERN_LIMIT);
    }

    private UnansweredQuestionPatternsResponse.AiSummary buildAiSummary(
            String scopedCompanyCode,
            List<UnansweredQuestionPatternsResponse.PatternItem> patterns
    ) {
        String summaryCompanyCode = resolveSummaryCompanyCode(scopedCompanyCode, patterns);
        List<String> questions = patterns.stream()
                .map(UnansweredQuestionPatternsResponse.PatternItem::questionContent)
                .filter(StringUtils::hasText)
                .toList();

        if (questions.isEmpty()) {
            return new UnansweredQuestionPatternsResponse.AiSummary(
                    "SKIPPED",
                    summaryCompanyCode,
                    0,
                    null,
                    List.of(),
                    false,
                    "NO_PATTERNS"
            );
        }

        try {
            AiNoResultSummaryClient.Top5AnalysisResponse response =
                    aiNoResultSummaryClient.analyzeTop5(summaryCompanyCode, questions);
            return new UnansweredQuestionPatternsResponse.AiSummary(
                    "READY",
                    response.companyCode(),
                    questions.size(),
                    response.summary(),
                    response.actions().stream()
                            .map(action -> new UnansweredQuestionPatternsResponse.AiAction(
                                    action.part(),
                                    action.items()
                            ))
                            .toList(),
                    response.hasSensitive(),
                    null
            );
        } catch (RuntimeException e) {
            log.warn("Failed to build unanswered question AI summary. companyCode={}, questionCount={}",
                    summaryCompanyCode, questions.size(), e);
            return new UnansweredQuestionPatternsResponse.AiSummary(
                    "FAILED",
                    summaryCompanyCode,
                    questions.size(),
                    null,
                    List.of(),
                    false,
                    "AI 요약 생성에 실패했습니다."
            );
        }
    }

    private String resolveSummaryCompanyCode(
            String scopedCompanyCode,
            List<UnansweredQuestionPatternsResponse.PatternItem> patterns
    ) {
        if (StringUtils.hasText(scopedCompanyCode)) {
            return scopedCompanyCode.trim();
        }

        List<String> companyCodes = patterns.stream()
                .map(UnansweredQuestionPatternsResponse.PatternItem::companyCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (companyCodes.size() == 1) {
            return companyCodes.getFirst();
        }
        return "ALL";
    }

    private String normalizeCompanyCode(String companyCode) {
        if (!StringUtils.hasText(companyCode)) {
            return null;
        }
        return companyCode.trim();
    }
}
