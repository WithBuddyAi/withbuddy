package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;

public record AdminDashboardResponse(
        String metric,
        LocalDate asOfDate,
        RagExperienceRateResponse ragExperienceRate,
        FirstInteractionRateResponse firstInteractionRate,
        RevisitRateResponse revisitRate,
        UnansweredRateResponse unansweredRate,
        TtaResponse tta,
        UnansweredQuestionPatternsResponse unansweredQuestionPatterns
) {
}
