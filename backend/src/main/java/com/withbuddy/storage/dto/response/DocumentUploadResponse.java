package com.withbuddy.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentUploadResponse {
    private final Long documentId;
    private final String title;
    private final String fileName;
    private final String contentType;
    private final Long fileSize;
    private final String source;
    private final String backupStatus;
    private final LocalDateTime uploadedAt;
}

