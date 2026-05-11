package com.withbuddy.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DocumentBulkDeleteCheckResponse {
    private final boolean confirmRequired;
    private final String message;
    private final int requestedCount;
    private final int deletableCount;
    private final List<Long> deletableDocumentIds;
    private final List<Long> notFoundDocumentIds;
    private final List<Long> forbiddenDocumentIds;
}
