package com.withbuddy.storage.event;

public record DocumentUploadedEvent(
        Long documentId,
        String companyCode
) {
}
