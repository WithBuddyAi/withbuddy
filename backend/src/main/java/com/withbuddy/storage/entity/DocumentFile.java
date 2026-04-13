package com.withbuddy.storage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentFile {
    private static final int BACKUP_LAST_ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true)
    private Long documentId;

    @Column(name = "company_code", length = 20)
    private String companyCode;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 255)
    private String storedFileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "primary_namespace", nullable = false, length = 120)
    private String primaryNamespace;

    @Column(name = "primary_bucket", nullable = false, length = 120)
    private String primaryBucket;

    @Column(name = "primary_object_key", nullable = false, length = 500)
    private String primaryObjectKey;

    @Column(name = "backup_namespace", nullable = false, length = 120)
    private String backupNamespace;

    @Column(name = "backup_bucket", nullable = false, length = 120)
    private String backupBucket;

    @Column(name = "backup_object_key", length = 500)
    private String backupObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "backup_status", nullable = false, length = 20)
    private BackupStatus backupStatus;

    @Column(name = "backup_attempt_count", nullable = false)
    private Integer backupAttemptCount;

    @Column(name = "backup_last_error", length = 500)
    private String backupLastError;

    @Column(name = "backup_requested_at", nullable = false)
    private LocalDateTime backupRequestedAt;

    @Column(name = "backup_completed_at")
    private LocalDateTime backupCompletedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public DocumentFile(
            Long documentId,
            String companyCode,
            String originalFileName,
            String storedFileName,
            String contentType,
            Long fileSize,
            String checksumSha256,
            String primaryNamespace,
            String primaryBucket,
            String primaryObjectKey,
            String backupNamespace,
            String backupBucket,
            String backupObjectKey,
            BackupStatus backupStatus,
            Integer backupAttemptCount,
            String backupLastError,
            LocalDateTime backupRequestedAt,
            LocalDateTime backupCompletedAt
    ) {
        this.documentId = documentId;
        this.companyCode = companyCode;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.checksumSha256 = checksumSha256;
        this.primaryNamespace = primaryNamespace;
        this.primaryBucket = primaryBucket;
        this.primaryObjectKey = primaryObjectKey;
        this.backupNamespace = backupNamespace;
        this.backupBucket = backupBucket;
        this.backupObjectKey = backupObjectKey;
        this.backupStatus = backupStatus;
        this.backupAttemptCount = backupAttemptCount == null ? 0 : backupAttemptCount;
        this.backupLastError = backupLastError;
        this.backupRequestedAt = backupRequestedAt == null ? LocalDateTime.now() : backupRequestedAt;
        this.backupCompletedAt = backupCompletedAt;
    }

    public void markBackupCompleted(String backupObjectKey) {
        this.backupStatus = BackupStatus.COMPLETED;
        this.backupObjectKey = backupObjectKey;
        this.backupCompletedAt = LocalDateTime.now();
        this.backupLastError = null;
    }

    public void markBackupInProgress() {
        this.backupStatus = BackupStatus.IN_PROGRESS;
        this.backupRequestedAt = LocalDateTime.now();
    }

    public void markBackupFailed(String errorMessage) {
        this.backupStatus = BackupStatus.FAILED;
        this.backupAttemptCount = this.backupAttemptCount + 1;
        this.backupLastError = truncate(errorMessage, BACKUP_LAST_ERROR_MAX_LENGTH);
        this.backupRequestedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
