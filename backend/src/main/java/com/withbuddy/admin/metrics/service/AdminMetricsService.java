package com.withbuddy.admin.metrics.service;

import com.withbuddy.admin.metrics.dto.FirstInteractionRateResponse;
import com.withbuddy.admin.metrics.dto.RagExperienceRateResponse;
import com.withbuddy.admin.metrics.dto.RevisitRateResponse;
import com.withbuddy.admin.metrics.dto.TtaResponse;
import com.withbuddy.admin.metrics.dto.UnansweredRateResponse;
import com.withbuddy.admin.metrics.repository.AdminMetricsRepository;
import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.user.entity.User;
import com.withbuddy.user.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminMetricsService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

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
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<RagExperienceRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findRagExperienceRateMetrics(companyCode, resolvedAsOfDate, dayAfter).stream()
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

    public FirstInteractionRateResponse getFirstInteractionRate(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<FirstInteractionRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findFirstInteractionRateMetrics(companyCode, resolvedAsOfDate, dayAfter).stream()
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
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<RevisitRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findRevisitRateMetrics(companyCode, resolvedAsOfDate, dayAfter).stream()
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
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<UnansweredRateResponse.CompanyMetric> companies =
                adminMetricsRepository.findUnansweredRateMetrics(companyCode, resolvedAsOfDate, dayAfter).stream()
                        .map(metric -> new UnansweredRateResponse.CompanyMetric(
                                metric.getCompanyCode(),
                                metric.getCompanyName(),
                                defaultLong(metric.getTotalAiAnswers()),
                                defaultLong(metric.getUnansweredAnswers()),
                                calculateRate(metric.getUnansweredAnswers(), metric.getTotalAiAnswers())
                        ))
                        .toList();

        return new UnansweredRateResponse("unanswered_rate", resolvedAsOfDate, companies);
    }

    public TtaResponse getTta(
            JwtAuthenticationPrincipal principal,
            String companyCode,
            LocalDate asOfDate
    ) {
        requireServiceAdmin(principal);
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate dayAfter = resolvedAsOfDate.plusDays(1);

        List<TtaResponse.CompanyMetric> companies =
                adminMetricsRepository.findTtaMetrics(companyCode, resolvedAsOfDate, dayAfter).stream()
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

    private void requireServiceAdmin(JwtAuthenticationPrincipal principal) {
        User currentUser = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));

        if (currentUser.getRole() != UserRole.SERVICE_ADMIN) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "관리자 권한이 필요한 API입니다.");
        }
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
}
