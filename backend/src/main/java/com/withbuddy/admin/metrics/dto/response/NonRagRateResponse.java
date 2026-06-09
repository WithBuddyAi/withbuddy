package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;
import java.util.List;

public record NonRagRateResponse(
        String metric,
        LocalDate asOfDate,
        List<CompanyMetric> companies
) {
    public record CompanyMetric(
            String companyCode,
            String companyName,
            long totalBotAnswers,
            long nonRagAnswers,
            double nonRagProcessingRate,
            long noResultAnswers,
            long outOfScopeAnswers,
            long sensitiveAnswers,
            double sensitiveRate
    ) {
    }
}
