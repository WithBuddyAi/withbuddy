package com.withbuddy.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DocumentBulkDeleteResponse {
    private final boolean confirmAccepted;
    private final String message;
    private final int requestedCount;
    private final int deletedCount;
    private final List<Long> deletedDocumentIds;
    private final List<Long> notFoundDocumentIds;
    private final List<Long> forbiddenDocumentIds;
}
