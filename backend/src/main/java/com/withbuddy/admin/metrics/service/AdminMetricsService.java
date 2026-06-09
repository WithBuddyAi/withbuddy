package com.withbuddy.admin.metrics.service;

import com.withbuddy.admin.metrics.dto.response.AdminDashboardResponse;
import com.withbuddy.admin.metrics.dto.response.FirstInteractionRateResponse;
import com.withbuddy.admin.metrics.dto.response.InternalAdminDashboardResponse;
import com.withbuddy.admin.metrics.dto.response.RagExperienceRateResponse;
import com.withbuddy.admin.metrics.dto.response.RevisitRateResponse;
import com.withbuddy.admin.metrics.dto.response.TtaResponse;
import com.withbuddy.admin.metrics.dto.response.UnansweredRateResponse;
import com.withbuddy.admin.metrics.dto.response.UnansweredQuestionPatternsResponse;
import com.withbuddy.admin.metrics.repository.AdminMetricsRepository;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminMetricsService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_PATTERN_LIMIT = 5;
    private static final int MAX_PATTERN_LIMIT = 20;

    private final AdminMetricsRepository adminMetricsRepository;
    private final UserRepository userRepository;

    public AdminMetricsService(
            AdminMetricsRepository adminMetricsRepository,
            UserRepository userRepository
    ) {
        this.adminMetricsRepository = adminMetricsRepository;
        this.userRepository = userRepository;
    }

    public RagExperienceRateResponse getRagExperienceRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<RagExperienceRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findRagExperienceRateMetrics(scopedCompanyCode, resolvedAsOfDate, dayAfter).stream()
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
                getFirstInteractionRate(principal, companyCode, resolvedAsOfDate),
                getRevisitRate(principal, companyCode, resolvedAsOfDate),
                getUnansweredRate(principal, companyCode, resolvedAsOfDate),
                getTta(principal, companyCode, resolvedAsOfDate),
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
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<FirstInteractionRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findFirstInteractionRateMetrics(scopedCompanyCode, resolvedAsOfDate, dayAfter).stream()
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
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<RevisitRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findRevisitRateMetrics(scopedCompanyCode, resolvedAsOfDate, dayAfter).stream()
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
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<UnansweredRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findUnansweredRateMetrics(scopedCompanyCode, resolvedAsOfDate, dayAfter).stream()
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

    public TtaResponse getTta(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<TtaResponse.CompanyMetric> companies =
                adminMetricsRepository.findTtaMetrics(scopedCompanyCode, resolvedAsOfDate, dayAfter).stream()
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

    public UnansweredQuestionPatternsResponse getUnansweredQuestionPatterns(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate,
            Integer limit
    ) {
        String scopedCompanyCode = resolveCompanyScope(principal, companyCode);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);
        int resolvedLimit = resolvePatternLimit(limit);

        List<UnansweredQuestionPatternsResponse.PatternItem> patterns =
                adminMetricsRepository.findUnansweredQuestionPatterns(
                                scopedCompanyCode,
                                dayAfter,
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
                patterns
        );
    }

    public InternalAdminDashboardResponse getInternalDashboard(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);

        UnansweredRateResponse unansweredRate = getUnansweredRate(principal, companyCode, resolvedAsOfDate);
        FirstInteractionRateResponse firstInteractionRate = getFirstInteractionRate(principal, companyCode, resolvedAsOfDate);
        RevisitRateResponse revisitRate = getRevisitRate(principal, companyCode, resolvedAsOfDate);

        Map<String, InternalAdminDashboardResponse.CompanyMetric> companies = new LinkedHashMap<>();

        unansweredRate.companies().forEach(metric -> companies.put(
                metric.companyCode(),
                new InternalAdminDashboardResponse.CompanyMetric(
                        metric.companyCode(),
                        metric.companyName(),
                        metric.totalAiAnswers(),
                        metric.noResultAnswers() + metric.outOfScopeAnswers() + metric.sensitiveAnswers(),
                        calculateRate(
                                metric.noResultAnswers() + metric.outOfScopeAnswers() + metric.sensitiveAnswers(),
                                metric.totalAiAnswers()
                        ),
                        metric.sensitiveAnswers(),
                        metric.sensitiveRate(),
                        0L,
                        0L,
                        0.0,
                        0L,
                        0L,
                        0.0
                )
        ));

        firstInteractionRate.companies().forEach(metric -> companies.compute(
                metric.companyCode(),
                (companyCodeKey, existing) -> new InternalAdminDashboardResponse.CompanyMetric(
                        metric.companyCode(),
                        metric.companyName(),
                        existing == null ? 0L : existing.totalAiAnswers(),
                        existing == null ? 0L : existing.nonRagAnswers(),
                        existing == null ? 0.0 : existing.nonRagProcessingRate(),
                        existing == null ? 0L : existing.sensitiveAnswers(),
                        existing == null ? 0.0 : existing.sensitiveRate(),
                        metric.targetUsers(),
                        metric.firstInteractionUsers(),
                        metric.firstInteractionRate(),
                        existing == null ? 0L : existing.d0Users(),
                        existing == null ? 0L : existing.revisitUsers(),
                        existing == null ? 0.0 : existing.revisitRate()
                )
        ));

        revisitRate.companies().forEach(metric -> companies.compute(
                metric.companyCode(),
                (companyCodeKey, existing) -> new InternalAdminDashboardResponse.CompanyMetric(
                        metric.companyCode(),
                        metric.companyName(),
                        existing == null ? 0L : existing.totalAiAnswers(),
                        existing == null ? 0L : existing.nonRagAnswers(),
                        existing == null ? 0.0 : existing.nonRagProcessingRate(),
                        existing == null ? 0L : existing.sensitiveAnswers(),
                        existing == null ? 0.0 : existing.sensitiveRate(),
                        existing == null ? 0L : existing.targetUsers(),
                        existing == null ? 0L : existing.firstInteractionUsers(),
                        existing == null ? 0.0 : existing.firstInteractionRate(),
                        metric.d0Users(),
                        metric.revisitUsers(),
                        metric.revisitRate()
                )
        ));

        return new InternalAdminDashboardResponse(
                "internal_admin_dashboard",
                resolvedAsOfDate,
                List.copyOf(companies.values())
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

    private int resolvePatternLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PATTERN_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_PATTERN_LIMIT);
    }

    private String normalizeCompanyCode(String companyCode) {
        if (!StringUtils.hasText(companyCode)) {
            return null;
        }
        return companyCode.trim();
    }
}
