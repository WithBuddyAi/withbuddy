CREATE TABLE IF NOT EXISTS `companies` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT uq_companies_company_code UNIQUE (company_code)
);

CREATE TABLE IF NOT EXISTS `users` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20) NOT NULL,
    name VARCHAR(20) NOT NULL,
    employee_number VARCHAR(20) NOT NULL,
    hire_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_company_code
        FOREIGN KEY (company_code) REFERENCES companies(company_code),
    CONSTRAINT uq_users_company_employee UNIQUE (company_code, employee_number)
);

CREATE TABLE IF NOT EXISTS `documents` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20) NULL,
    title VARCHAR(200) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    department VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_company_code
        FOREIGN KEY (company_code) REFERENCES companies(company_code)
);

CREATE TABLE IF NOT EXISTS `document_files` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    company_code VARCHAR(20) NULL,
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
    CONSTRAINT uq_document_files_document_id UNIQUE (document_id),
    CONSTRAINT fk_document_files_document
    FOREIGN KEY (document_id) REFERENCES documents(id),
    CONSTRAINT fk_document_files_company_code
    FOREIGN KEY (company_code) REFERENCES companies(company_code)
    );

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
    CONSTRAINT fk_document_backup_jobs_document_file
    FOREIGN KEY (document_file_id) REFERENCES document_files(id)
    );

CREATE TABLE IF NOT EXISTS `onboarding_suggestions` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    day_offset INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_onboarding_suggestions PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS `chat_messages` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    suggestion_id BIGINT NULL,
    sender_type VARCHAR(20) NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_user
        FOREIGN KEY (user_id) REFERENCES `users`(id),
    CONSTRAINT fk_chat_messages_suggestion
        FOREIGN KEY (suggestion_id) REFERENCES onboarding_suggestions(id)
);

CREATE TABLE IF NOT EXISTS `user_activity_logs` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    event_target VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_activity_logs PRIMARY KEY (id),
    CONSTRAINT fk_user_activity_logs_user
        FOREIGN KEY (user_id) REFERENCES `users`(id)
);

-- Dummy seed data
INSERT INTO `companies` (company_code, name)
SELECT 'WB0001', '테크 주식회사'
WHERE NOT EXISTS (
    SELECT 1 FROM `companies` WHERE company_code = 'WB0001'
);

INSERT INTO `users` (company_code, name, employee_number, hire_date)
SELECT 'WB0001', '김지원', '20260001', '2026-03-01'
WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE company_code = 'WB0001' AND employee_number = '20260001'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '남녀고용평등과 일 · 가정 양립 지원에 관한 법률', '/docs/common-law/남녀고용평등과 일 · 가정 양립 지원에 관한 법률(법률)(제21065호)(20251001).pdf', 'LEGAL', 'LEGAL', TRUE
    WHERE NOT EXISTS (
    SELECT 1 FROM documents
    WHERE title = '남녀고용평등과 일 · 가정 양립 지원에 관한 법률'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '근로자퇴직급여 보장법', '/docs/common-law/근로자퇴직급여 보장법(법률)(제21135호)(20251111).pdf', 'LEGAL', 'LEGAL', TRUE
    WHERE NOT EXISTS (
    SELECT 1 FROM documents
    WHERE title = '근로자퇴직급여 보장법'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '최저임금법', '/docs/common-law/최저임금법(법률)(제17326호)(20200526).pdf', 'LEGAL', 'LEGAL', TRUE
    WHERE NOT EXISTS (
    SELECT 1 FROM documents
    WHERE title = '최저임금법'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '근로기준법', '/docs/common-law/근로기준법(법률)(제20520호)(20251023).pdf', 'LEGAL', 'LEGAL', TRUE
    WHERE NOT EXISTS (
    SELECT 1 FROM documents
    WHERE title = '근로기준법'
);

INSERT INTO `onboarding_suggestions` (title, content, day_offset)
SELECT '입사 1일차 안내', '당신의 입사를 진심으로 축하드려요. 복지 제도와 사내 규정 문서를 먼저 확인해보세요.', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `onboarding_suggestions` WHERE title = '입사 1일차 안내' AND day_offset = 1
);

INSERT INTO `onboarding_suggestions` (title, content, day_offset)
SELECT '입사 3일차 안내', '업무에 조금씩 익숙해지고 있을 시점이에요. 자주 사용하는 시스템과 꼭 알아야 할 사내 규정을 다시 한 번 확인해보세요.', 3
    WHERE NOT EXISTS (
    SELECT 1
    FROM `onboarding_suggestions`
    WHERE title = '입사 3일차 안내'
      AND day_offset = 3
);

INSERT INTO `onboarding_suggestions` (title, content, day_offset)
SELECT '입사 7일차 안내', '한 주 동안 확인한 업무 흐름과 아직 헷갈리는 부분을 정리해보세요.', 7
    WHERE NOT EXISTS (
    SELECT 1
    FROM `onboarding_suggestions`
    WHERE title = '입사 7일차 안내'
      AND day_offset = 7
);

INSERT INTO `onboarding_suggestions` (title, content, day_offset)
SELECT '입사 30일차 안내', '입사한 지 한 달이 됐네요. 한 달간 적응한 내용을 돌아보고, 자주 묻는 업무나 개선이 필요한 부분을 정리해보세요.', 30
    WHERE NOT EXISTS (
    SELECT 1
    FROM `onboarding_suggestions`
    WHERE title = '입사 30일차 안내'
      AND day_offset = 30
);