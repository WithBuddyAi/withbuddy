package com.withbuddy.admin.metrics.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record NoResultQuestionPatternRefreshResponse(
        LocalDate analysisDate,
        int topN,
        List<CompanyResult> companies
) {
    public record CompanyResult(
            String companyCode,
            int sourceCount,
            int patternCount,
            String status,
            String errorMessage,
            LocalDateTime updatedAt
    ) {
    }
}
