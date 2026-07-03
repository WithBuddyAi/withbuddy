package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UnansweredQuestionPatternsResponse(
        String metric,
        LocalDate asOfDate,
        int limit,
        int sourceCount,
        List<PatternItem> patterns,
        AiSummary aiSummary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record AiSummary(
            String status,
            String companyCode,
            int questionCount,
            String summary,
            List<AiAction> actions,
            boolean hasSensitive,
            String errorMessage
    ) {
        public AiSummary {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record AiAction(
            String part,
            String items
    ) {
    }

    public record PatternItem(
            int rank,
            String questionContent,
            long totalCount
    ) {
    }
}
