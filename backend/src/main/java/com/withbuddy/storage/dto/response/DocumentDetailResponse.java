package com.withbuddy.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentDetailResponse {
    private final Long documentId;
    private final String title;
    private final String documentType;
    private final String department;
    private final boolean isActive;
    private final FileMetadata file;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Getter
    @AllArgsConstructor
    public static class FileMetadata {
        private final String fileName;
        private final String contentType;
        private final Long fileSize;
        private final String checksumSha256;
        private final String source;
        private final String backupStatus;
    }
}

