package com.withbuddy.storage.event;

public record DocumentDeletedEvent(
        Long documentId,
        String companyCode
) {
}
