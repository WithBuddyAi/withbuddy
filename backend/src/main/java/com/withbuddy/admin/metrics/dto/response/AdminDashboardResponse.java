package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;

public record AdminDashboardResponse(
        String metric,
        LocalDate asOfDate,
        RagExperienceRateResponse ragExperienceRate,
        DocumentGapRateResponse documentGapRate,
        UnstartedUsersResponse unstartedUsers,
        UnansweredQuestionPatternsResponse unansweredQuestionPatterns
) {
}
