package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;

public record InternalAdminDashboardResponse(
        String metric,
        LocalDate asOfDate,
        FirstInteractionRateResponse firstInteractionRate,
        RevisitRateResponse revisitRate,
        TtaResponse tta,
        TtaUnreachedResponse ttaUnreached,
        NonRagRateResponse nonRagRate
) {
}
