package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;
import java.util.List;

public record UnansweredRateResponse(
        String metric,
        LocalDate asOfDate,
        List<CompanyMetric> companies
) {
    public record CompanyMetric(
            String companyCode,
            String companyName,
            long totalAiAnswers,
            long unansweredAnswers,
            double unansweredRate
    ) {
    }
}
