package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UnansweredQuestionPatternsResponse(
        String metric,
        LocalDate asOfDate,
        int limit,
        List<PatternItem> patterns
) {
    public record PatternItem(
            String companyCode,
            String questionContent,
            long totalCount,
            long uniqueUsers,
            long noResultCount,
            long outOfScopeCount,
            LocalDateTime latestOccurredAt
    ) {
    }
}
