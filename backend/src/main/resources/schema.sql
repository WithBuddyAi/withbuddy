CREATE TABLE IF NOT EXISTS companies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    probation_period INT NOT NULL DEFAULT 90,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT uq_companies_company_code UNIQUE (company_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS company_organization_units (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20) NOT NULL,
    department VARCHAR(100) NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_company_organization_units PRIMARY KEY (id),
    CONSTRAINT fk_company_organization_units_company_code
        FOREIGN KEY (company_code) REFERENCES companies (company_code),
    CONSTRAINT uq_company_organization_units_company_department_team
        UNIQUE (company_code, department, team_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    department VARCHAR(100) NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    name VARCHAR(20) NOT NULL,
    employee_number VARCHAR(20) NOT NULL,
    hire_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_company_code
        FOREIGN KEY (company_code) REFERENCES companies (company_code),
    CONSTRAINT fk_users_company_organization_unit
        FOREIGN KEY (company_code, department, team_name)
            REFERENCES company_organization_units (company_code, department, team_name)
            ON UPDATE CASCADE
            ON DELETE RESTRICT,
    CONSTRAINT uq_users_company_employee UNIQUE (company_code, employee_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_users_company_department_team
    ON users (company_code, department, team_name);

CREATE TABLE IF NOT EXISTS documents (
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
        FOREIGN KEY (company_code) REFERENCES companies (company_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS document_files (
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
    CONSTRAINT uq_document_files_document UNIQUE (document_id),
    CONSTRAINT uq_document_files_primary_location
        UNIQUE (primary_namespace, primary_bucket, primary_object_key),
    CONSTRAINT fk_document_files_document
        FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_document_files_company
        FOREIGN KEY (company_code) REFERENCES companies (company_code),
    CONSTRAINT ck_document_files_backup_status
        CHECK (backup_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_document_files_company_created
    ON document_files (company_code, created_at);

CREATE INDEX idx_document_files_backup_status
    ON document_files (backup_status, backup_requested_at);

CREATE TABLE IF NOT EXISTS document_backup_jobs (
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
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

CREATE TABLE IF NOT EXISTS onboarding_suggestions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    day_offset INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_onboarding_suggestions PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    suggestion_id BIGINT NULL,
    answer_to_message_id BIGINT NULL,
    sender_type VARCHAR(20) NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    recommended_contacts_json TEXT NULL,
    latency_ms BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_messages_suggestion
        FOREIGN KEY (suggestion_id) REFERENCES onboarding_suggestions (id),
    CONSTRAINT fk_chat_messages_answer_to_message
        FOREIGN KEY (answer_to_message_id) REFERENCES chat_messages (id) ON DELETE SET NULL,
    CONSTRAINT uq_chat_messages_user_suggestion_type
        UNIQUE (user_id, suggestion_id, message_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_chat_messages_answer_to_message_id
    ON chat_messages (answer_to_message_id);

CREATE TABLE IF NOT EXISTS chat_message_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_message_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_chat_message_documents PRIMARY KEY (id),
    CONSTRAINT uq_chat_message_documents_message_document
        UNIQUE (chat_message_id, document_id),
    CONSTRAINT fk_chat_message_documents_chat_message
        FOREIGN KEY (chat_message_id) REFERENCES chat_messages (id),
    CONSTRAINT fk_chat_message_documents_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS user_activity_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    event_target VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_activity_logs PRIMARY KEY (id),
    CONSTRAINT fk_user_activity_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS messaging_event_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_messaging_event_logs PRIMARY KEY (id),
    CONSTRAINT uk_messaging_event_logs_event_id UNIQUE (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO companies (company_code, name, probation_period)
SELECT 'WB0001', '테크 주식회사', 90
WHERE NOT EXISTS (
    SELECT 1 FROM companies WHERE company_code = 'WB0001'
);

INSERT INTO companies (company_code, name, probation_period)
SELECT 'WB0002', '스튜디오 프리즘 (Studio Prism)', 90
WHERE NOT EXISTS (
    SELECT 1 FROM companies WHERE company_code = 'WB0002'
);

INSERT INTO company_organization_units (company_code, department, team_name)
SELECT 'WB0001', '미지정', '미지정'
WHERE NOT EXISTS (
    SELECT 1 FROM company_organization_units
    WHERE company_code = 'WB0001' AND department = '미지정' AND team_name = '미지정'
);

INSERT INTO company_organization_units (company_code, department, team_name)
SELECT 'WB0002', '미지정', '미지정'
WHERE NOT EXISTS (
    SELECT 1 FROM company_organization_units
    WHERE company_code = 'WB0002' AND department = '미지정' AND team_name = '미지정'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0001', 'USER', 'ACTIVE', '미지정', '미지정', '김민준', '20260001', '2026-04-14'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0001' AND employee_number = '20260001'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0001', 'USER', 'ACTIVE', '미지정', '미지정', '이서연', '20260002', '2026-04-11'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0001' AND employee_number = '20260002'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0001', 'USER', 'ACTIVE', '미지정', '미지정', '박도현', '20260003', '2026-04-07'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0001' AND employee_number = '20260003'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0001', 'USER', 'ACTIVE', '미지정', '미지정', '최지아', '20260004', '2026-03-15'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0001' AND employee_number = '20260004'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0002', 'USER', 'ACTIVE', '미지정', '미지정', '정하은', '20260001', '2026-04-14'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0002' AND employee_number = '20260001'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0002', 'USER', 'ACTIVE', '미지정', '미지정', '강준서', '20260002', '2026-04-11'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0002' AND employee_number = '20260002'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0001', 'ADMIN', 'ACTIVE', '미지정', '미지정', '김하늘', '20250001', '2025-01-01'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0001' AND employee_number = '20250001'
);

INSERT INTO users (company_code, role, account_status, department, team_name, name, employee_number, hire_date)
SELECT 'WB0002', 'ADMIN', 'ACTIVE', '미지정', '미지정', '이서윤', '20240001', '2024-01-01'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE company_code = 'WB0002' AND employee_number = '20240001'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '남녀고용평등과 일 · 가정 양립 지원에 관한 법률',
       '/docs/common-law/남녀고용평등과 일 · 가정 양립 지원에 관한 법률(법률)(제21065호)(20251001).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '남녀고용평등과 일 · 가정 양립 지원에 관한 법률'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '근로자퇴직급여 보장법',
       '/docs/common-law/근로자퇴직급여 보장법(법률)(제21135호)(20251111).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '근로자퇴직급여 보장법'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '최저임금법',
       '/docs/common-law/최저임금법(법률)(제17326호)(20200526).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '최저임금법'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '근로기준법',
       '/docs/common-law/근로기준법(법률)(제20520호)(20251023).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '근로기준법'
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 7일 전~4일 전',
       '입사까지 딱 {N}일 남았네요 🎉 설레는 마음, 저도 느껴져요.\n첫날 당황하지 않도록 미리 알아두면 좋은 것들이에요.',
       -7
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = -7
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 3일~1일 전',
       '드디어 입사 {N}일전이에요 😊 입사 첫날, 이것만 알고 가도 훨씬 편할 거예요.',
       -3
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = -3
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 당일',
       '안녕하세요! 저는 위드버디예요 🙂 입사 첫날, 설레기도 하고 낯설기도 하죠?\n{회사명}에서 궁금한 게 생기면 언제든 물어보세요. 사소한 것도 괜찮아요.',
       0
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 0
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 2일차',
       '이틀째, 어떠세요? 😊 아직 낯선 게 많겠지만, 조금씩 익숙해지고 있죠?',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 1
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 3일차',
       '3일째, {회사명}에 조금 익숙해졌나요? 🌱 이쯤 되면 이런 게 궁금해지더라고요.',
       2
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 2
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 5일차',
       '첫 주가 거의 다 됐어요 🙂 이번 주말 전에 알아두면 좋을 것들이에요.',
       4
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 4
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 7일차',
       '첫 주를 마쳤어요, 수고했어요 👏 이번 주엔 업무 흐름이 조금씩 보이기 시작할 거예요.',
       6
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 6
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 10일차',
       '열흘째! 이제 흐름이 좀 보이죠? 💪 슬슬 이런 것들도 궁금해질 거예요.',
       9
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 9
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 14일차',
       '벌써 2주예요! 많이 적응됐죠? 😄 이제 슬슬 이런 것들도 챙겨봐요.',
       13
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 13
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 21일차',
       '3주 달성! 이제 진짜 팀원 같은 느낌이죠? 🙌 한 달이 얼마 안 남았어요.',
       20
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 20
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 30일차',
       '한 달을 해냈어요! 정말 잘하고 있어요 🎊 한 달간의 수습 기간이 지났어요. 궁금한 거 미리 챙겨봐요.',
       29
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 29
);
