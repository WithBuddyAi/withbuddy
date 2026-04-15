-- SCRUM-169 ST-011. document_backup_jobs 테이블 생성
CREATE TABLE IF NOT EXISTS document_backup_jobs (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    document_file_id  BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    attempt_no        INT           NOT NULL,
    source_namespace  VARCHAR(120)  NOT NULL,
    source_bucket     VARCHAR(120)  NOT NULL,
    source_object_key VARCHAR(500)  NOT NULL,
    target_namespace  VARCHAR(120)  NOT NULL,
    target_bucket     VARCHAR(120)  NOT NULL,
    target_object_key VARCHAR(500)  NULL,
    error_message     VARCHAR(1000) NULL,
    started_at        DATETIME      NOT NULL,
    finished_at       DATETIME      NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_document_backup_jobs PRIMARY KEY (id),
    CONSTRAINT fk_document_backup_jobs_file
        FOREIGN KEY (document_file_id) REFERENCES document_files (id),
    CONSTRAINT ck_document_backup_jobs_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_backup_jobs_file_attempt
    ON document_backup_jobs (document_file_id, attempt_no);

CREATE INDEX idx_backup_jobs_status_created
    ON document_backup_jobs (status, created_at);
