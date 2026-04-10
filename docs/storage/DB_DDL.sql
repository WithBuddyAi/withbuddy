-- Storage metadata schema for WithBuddy
-- 기준: 기존 companies/users/documents 테이블은 유지하고, 스토리지 메타데이터만 확장
-- DB: MySQL 8.x

-- 1) 문서-파일 메타데이터 (원본/백업 위치 + 무결성 + 상태)
CREATE TABLE IF NOT EXISTS `document_files` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    company_code VARCHAR(20) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,

    primary_namespace VARCHAR(120) NOT NULL,
    primary_bucket VARCHAR(120) NOT NULL,
    primary_object_key VARCHAR(500) NOT NULL,

    backup_namespace VARCHAR(120) NOT NULL,
    backup_bucket VARCHAR(120) NOT NULL,
    backup_object_key VARCHAR(500) NULL,

    backup_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    backup_attempt_count INT NOT NULL DEFAULT 0,
    backup_last_error VARCHAR(500) NULL,
    backup_requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    backup_completed_at DATETIME NULL,

    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_document_files PRIMARY KEY (id),
    CONSTRAINT uq_document_files_document UNIQUE (document_id),
    CONSTRAINT uq_document_files_primary_location UNIQUE (primary_namespace, primary_bucket, primary_object_key),
    CONSTRAINT fk_document_files_document
        FOREIGN KEY (document_id) REFERENCES documents(id),
    CONSTRAINT fk_document_files_company
        FOREIGN KEY (company_code) REFERENCES companies(company_code),
    CONSTRAINT ck_document_files_backup_status
        CHECK (backup_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_document_files_company_created
    ON `document_files` (company_code, created_at);

CREATE INDEX idx_document_files_backup_status
    ON `document_files` (backup_status, backup_requested_at);

-- 2) 백업 작업 이력/재시도 추적
CREATE TABLE IF NOT EXISTS `document_backup_jobs` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_file_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_no INT NOT NULL,
    source_namespace VARCHAR(120) NOT NULL,
    source_bucket VARCHAR(120) NOT NULL,
    source_object_key VARCHAR(500) NOT NULL,
    target_namespace VARCHAR(120) NOT NULL,
    target_bucket VARCHAR(120) NOT NULL,
    target_object_key VARCHAR(500) NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_document_backup_jobs PRIMARY KEY (id),
    CONSTRAINT fk_document_backup_jobs_file
        FOREIGN KEY (document_file_id) REFERENCES document_files(id),
    CONSTRAINT ck_document_backup_jobs_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_backup_jobs_file_attempt
    ON `document_backup_jobs` (document_file_id, attempt_no);

CREATE INDEX idx_backup_jobs_status_created
    ON `document_backup_jobs` (status, created_at);

-- 3) 선택: 기존 documents에서 file_path를 legacy로 쓴 경우 migration 힌트
-- UPDATE documents d
-- JOIN document_files f ON f.document_id = d.id
-- SET d.file_path = CONCAT('/', f.primary_bucket, '/', f.primary_object_key)
-- WHERE d.file_path IS NULL;

