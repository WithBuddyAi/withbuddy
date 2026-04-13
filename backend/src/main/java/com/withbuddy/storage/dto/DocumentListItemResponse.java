package com.withbuddy.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentListItemResponse {
    private final Long documentId;
    private final String title;
    private final String documentType;
    private final String department;
    private final String fileName;
    private final String contentType;
    private final Long fileSize;
    private final String backupStatus;
    private final LocalDateTime createdAt;
}

