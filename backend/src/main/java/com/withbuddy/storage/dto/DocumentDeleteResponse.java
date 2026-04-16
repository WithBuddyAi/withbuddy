package com.withbuddy.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentDeleteResponse {
    private final Long documentId;
    private final String message;
}
