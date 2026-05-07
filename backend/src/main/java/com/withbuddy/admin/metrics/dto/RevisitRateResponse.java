package com.withbuddy.admin.metrics.dto;

import java.time.LocalDate;
import java.util.List;

public record RevisitRateResponse(
        String metric,
        LocalDate asOfDate,
        List<CompanyMetric> companies
) {
    public record CompanyMetric(
            String companyCode,
            String companyName,
            long d0Users,
            long revisitUsers,
            double revisitRate
    ) {
    }
}
