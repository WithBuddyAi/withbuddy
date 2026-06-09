package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;
import java.util.List;

public record InternalAdminDashboardResponse(
        String metric,
        LocalDate asOfDate,
        List<CompanyMetric> companies
) {
    public record CompanyMetric(
            String companyCode,
            String companyName,
            long totalAiAnswers,
            long nonRagAnswers,
            double nonRagProcessingRate,
            long sensitiveAnswers,
            double sensitiveRate,
            long targetUsers,
            long firstInteractionUsers,
            double firstInteractionRate,
            long d0Users,
            long revisitUsers,
            double revisitRate
    ) {
    }
}
