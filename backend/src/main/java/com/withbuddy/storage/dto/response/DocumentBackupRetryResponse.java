package com.withbuddy.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentBackupRetryResponse {
    private final Long documentId;
    private final String backupStatus;
    private final Integer backupAttemptCount;
    private final String backupLastError;
    private final LocalDateTime backupCompletedAt;
}
