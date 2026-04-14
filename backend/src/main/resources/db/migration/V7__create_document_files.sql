-- SCRUM-168 ST-010. document_files 테이블 생성
CREATE TABLE IF NOT EXISTS document_files (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    document_id          BIGINT       NOT NULL,
    company_code         VARCHAR(20)  NULL,
    original_file_name   VARCHAR(255) NOT NULL,
    stored_file_name     VARCHAR(255) NOT NULL,
    content_type         VARCHAR(120) NOT NULL,
    file_size            BIGINT       NOT NULL,
    checksum_sha256      CHAR(64)     NOT NULL,

    primary_namespace    VARCHAR(120) NOT NULL,
    primary_bucket       VARCHAR(120) NOT NULL,
    primary_object_key   VARCHAR(500) NOT NULL,

    backup_namespace     VARCHAR(120) NOT NULL,
    backup_bucket        VARCHAR(120) NOT NULL,
    backup_object_key    VARCHAR(500) NULL,

    backup_status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    backup_attempt_count INT          NOT NULL DEFAULT 0,
    backup_last_error    VARCHAR(500) NULL,
    backup_requested_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    backup_completed_at  DATETIME     NULL,

    deleted_at           DATETIME     NULL,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_document_files PRIMARY KEY (id),
    CONSTRAINT uq_document_files_document UNIQUE (document_id),
    CONSTRAINT uq_document_files_primary_location UNIQUE (primary_namespace, primary_bucket, primary_object_key),
    CONSTRAINT fk_document_files_document
        FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT ck_document_files_backup_status
        CHECK (backup_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_document_files_company_created
    ON document_files (company_code, created_at);

CREATE INDEX idx_document_files_backup_status
    ON document_files (backup_status, backup_requested_at);

-- 회사 코드 FK 호환성 보정:
-- 기존 운영 DB에서 companies.company_code의 collation/charset과 신규 테이블 기본값이 달라
-- FK 생성(에러 3780)이 실패할 수 있으므로, 참조 컬럼 정의를 런타임에 맞춘 뒤 FK를 추가한다.
SET @company_col_type = (
    SELECT COLUMN_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'companies'
      AND COLUMN_NAME = 'company_code'
);
SET @company_charset = (
    SELECT CHARACTER_SET_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'companies'
      AND COLUMN_NAME = 'company_code'
);
SET @company_collation = (
    SELECT COLLATION_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'companies'
      AND COLUMN_NAME = 'company_code'
);

SET @sql_align_company_code = CONCAT(
    'ALTER TABLE document_files MODIFY company_code ', @company_col_type,
    IF(@company_charset IS NOT NULL, CONCAT(' CHARACTER SET ', @company_charset), ''),
    IF(@company_collation IS NOT NULL, CONCAT(' COLLATE ', @company_collation), ''),
    ' NULL'
);
PREPARE stmt_align_company_code FROM @sql_align_company_code;
EXECUTE stmt_align_company_code;
DEALLOCATE PREPARE stmt_align_company_code;

ALTER TABLE document_files
    ADD CONSTRAINT fk_document_files_company
        FOREIGN KEY (company_code) REFERENCES companies (company_code);
