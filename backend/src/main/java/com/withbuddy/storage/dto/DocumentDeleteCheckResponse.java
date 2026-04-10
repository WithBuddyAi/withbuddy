package com.withbuddy.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentDeleteCheckResponse {
    private final Long documentId;
    private final String title;
    private final String companyCode;
    private final boolean active;
    private final String fileName;
    private final Long fileSize;
    private final String backupStatus;
    private final LocalDateTime createdAt;
    private final boolean confirmRequired;
}
