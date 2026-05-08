package com.withbuddy.admin.metrics.dto;

import java.time.LocalDate;
import java.util.List;

public record TtaResponse(
        String metric,
        LocalDate asOfDate,
        String unit,
        List<CompanyMetric> companies
) {
    public record CompanyMetric(
            String companyCode,
            String companyName,
            long loggedInUsers,
            long measuredUsers,
            Double averageTtaMinutes
    ) {
    }
}
