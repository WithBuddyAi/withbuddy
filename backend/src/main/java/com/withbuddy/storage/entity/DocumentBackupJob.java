package com.withbuddy.storage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_backup_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentBackupJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_file_id", nullable = false)
    private Long documentFileId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "source_namespace", nullable = false, length = 120)
    private String sourceNamespace;

    @Column(name = "source_bucket", nullable = false, length = 120)
    private String sourceBucket;

    @Column(name = "source_object_key", nullable = false, length = 500)
    private String sourceObjectKey;

    @Column(name = "target_namespace", nullable = false, length = 120)
    private String targetNamespace;

    @Column(name = "target_bucket", nullable = false, length = 120)
    private String targetBucket;

    @Column(name = "target_object_key", length = 500)
    private String targetObjectKey;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DocumentBackupJob(
            Long documentFileId,
            String status,
            Integer attemptNo,
            String sourceNamespace,
            String sourceBucket,
            String sourceObjectKey,
            String targetNamespace,
            String targetBucket,
            String targetObjectKey,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        this.documentFileId = documentFileId;
        this.status = status;
        this.attemptNo = attemptNo;
        this.sourceNamespace = sourceNamespace;
        this.sourceBucket = sourceBucket;
        this.sourceObjectKey = sourceObjectKey;
        this.targetNamespace = targetNamespace;
        this.targetBucket = targetBucket;
        this.targetObjectKey = targetObjectKey;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }
}

