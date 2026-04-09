CREATE DATABASE IF NOT EXISTS withbuddy;
USE withbuddy;

CREATE TABLE `companies` (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             company_code VARCHAR(20) NOT NULL,
                             name VARCHAR(100) NOT NULL,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             CONSTRAINT pk_companies PRIMARY KEY (id),
                             CONSTRAINT uq_companies_company_code UNIQUE (company_code)

);

CREATE TABLE `users` (
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

CREATE TABLE `documents` (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             company_code VARCHAR(20) NULL,
                             title VARCHAR(200) NOT NULL,
                             content MEDIUMTEXT NOT NULL,
                             document_type VARCHAR(50) NOT NULL,
                             department VARCHAR(50) NOT NULL,
                             is_active BOOLEAN NOT NULL DEFAULT TRUE,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             CONSTRAINT pk_documents PRIMARY KEY (id),
                             CONSTRAINT fk_documents_company_code
                                 FOREIGN KEY (company_code) REFERENCES companies(company_code)
);

CREATE TABLE `onboarding_suggestions` (
                                          id BIGINT NOT NULL AUTO_INCREMENT,
                                          title VARCHAR(255) NOT NULL,
                                          content TEXT NOT NULL,
                                          day_offset INT NOT NULL,
                                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          CONSTRAINT pk_onboarding_suggestions PRIMARY KEY (id)
);

CREATE TABLE `chat_messages` (
                                 id BIGINT NOT NULL AUTO_INCREMENT,
                                 user_id BIGINT NOT NULL,
                                 document_id BIGINT NULL,
                                 suggestion_id BIGINT NULL,
                                 sender_type VARCHAR(20) NOT NULL,
                                 message_type VARCHAR(30) NOT NULL,
                                 content TEXT NOT NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT pk_chat_messages PRIMARY KEY (id),
                                 CONSTRAINT fk_chat_messages_user
                                     FOREIGN KEY (user_id) REFERENCES `users`(id),
                                 CONSTRAINT fk_chat_messages_document
                                     FOREIGN KEY (document_id) REFERENCES documents(id),
                                 CONSTRAINT fk_chat_messages_suggestion
                                     FOREIGN KEY (suggestion_id) REFERENCES onboarding_suggestions(id)
);

CREATE TABLE `user_activity_logs` (
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
SELECT 'WB-0001', '위드버디(주)'
WHERE NOT EXISTS (
    SELECT 1 FROM `companies` WHERE company_code = 'WB-0001'
);

INSERT INTO `users` (company_code, name, employee_number, hire_date)
SELECT 'WB-0001', '홍성하', 'WB-001', '2026-03-04'
WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE company_code = 'WB-0001' AND employee_number = 'WB-001'
);

INSERT INTO `users` (company_code, name, employee_number, hire_date)
SELECT 'WB-0001', '김지원', 'WB-002', '2026-03-04'
WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE company_code = 'WB-0001' AND employee_number = 'WB-002'
);

INSERT INTO `users` (company_code, name, employee_number, hire_date)
SELECT 'WB-0001', '장수민', 'WB-003', '2026-03-04'
WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE company_code = 'WB-0001' AND employee_number = 'WB-003'
);

INSERT INTO `users` (company_code, name, employee_number, hire_date)
SELECT 'WB-0001', '신수민', 'WB-004', '2026-03-04'
WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE company_code = 'WB-0001' AND employee_number = 'WB-004'
);

INSERT INTO `users` (company_code, name, employee_number, hire_date)
SELECT 'WB-0001', '김준수', 'WB-005', '2026-03-04'
WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE company_code = 'WB-0001' AND employee_number = 'WB-005'
);

INSERT INTO `users` (company_code, name, employee_number, hire_date)
SELECT 'WB-0001', '박혜진', 'WB-006', '2026-03-04'
WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE company_code = 'WB-0001' AND employee_number = 'WB-006'
);

INSERT INTO `documents` (company_code, title, content, document_type, department, is_active)
SELECT 'WB-0001', '온보딩 안내서', '입사 첫 달 필수 확인 사항과 업무 적응 가이드입니다.', 'GUIDE', 'HR', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM `documents` WHERE company_code = 'WB-0001' AND title = '온보딩 안내서'
);

INSERT INTO `documents` (company_code, title, content, document_type, department, is_active)
SELECT 'WB-0001', '복리후생 정책', '연차, 교육비, 장비지원 등 복리후생 정책 문서입니다.', 'POLICY', 'HR', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM `documents` WHERE company_code = 'WB-0001' AND title = '복리후생 정책'
);

INSERT INTO `documents` (company_code, title, content, document_type, department, is_active)
SELECT 'WB-0001', '개발 환경 세팅', '로컬 개발환경 구성과 브랜치 전략을 정리한 문서입니다.', 'TECH', 'ENGINEERING', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM `documents` WHERE company_code = 'WB-0001' AND title = '개발 환경 세팅'
);

INSERT INTO `onboarding_suggestions` (title, content, day_offset)
SELECT '첫 출근 체크리스트', '계정 발급, 필수 시스템 로그인, 팀 채널 입장을 완료하세요.', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `onboarding_suggestions` WHERE title = '첫 출근 체크리스트' AND day_offset = 1
);

INSERT INTO `onboarding_suggestions` (title, content, day_offset)
SELECT '첫 주 목표', '팀 온보딩 미팅 참여 후 현재 스프린트 이슈를 파악하세요.', 7
WHERE NOT EXISTS (
    SELECT 1 FROM `onboarding_suggestions` WHERE title = '첫 주 목표' AND day_offset = 7
);

INSERT INTO `onboarding_suggestions` (title, content, day_offset)
SELECT '첫 달 회고', '한 달간 배운 점과 개선 아이디어를 간단히 정리해 공유하세요.', 30
WHERE NOT EXISTS (
    SELECT 1 FROM `onboarding_suggestions` WHERE title = '첫 달 회고' AND day_offset = 30
);

INSERT INTO `chat_messages` (user_id, document_id, suggestion_id, sender_type, message_type, content)
SELECT u.id, d.id, NULL, 'USER', 'TEXT', '복리후생 정책에서 교육비 지원 기준이 궁금해요.'
FROM `users` u
JOIN `documents` d ON d.title = '복리후생 정책'
WHERE u.employee_number = 'WB-001'
  AND NOT EXISTS (
      SELECT 1 FROM `chat_messages` cm
      WHERE cm.user_id = u.id
        AND cm.sender_type = 'USER'
        AND cm.message_type = 'TEXT'
        AND cm.content = '복리후생 정책에서 교육비 지원 기준이 궁금해요.'
  );

INSERT INTO `chat_messages` (user_id, document_id, suggestion_id, sender_type, message_type, content)
SELECT u.id, NULL, s.id, 'BOT', 'SUGGESTION', '첫 주 목표를 확인하고 팀 온보딩 미팅 일정을 캘린더에 등록해보세요.'
FROM `users` u
JOIN `onboarding_suggestions` s ON s.title = '첫 주 목표'
WHERE u.employee_number = 'WB-002'
  AND NOT EXISTS (
      SELECT 1 FROM `chat_messages` cm
      WHERE cm.user_id = u.id
        AND cm.sender_type = 'BOT'
        AND cm.message_type = 'SUGGESTION'
        AND cm.content = '첫 주 목표를 확인하고 팀 온보딩 미팅 일정을 캘린더에 등록해보세요.'
  );

INSERT INTO `user_activity_logs` (user_id, event_type, event_target)
SELECT u.id, 'DOCUMENT_VIEW', '복리후생 정책'
FROM `users` u
WHERE u.employee_number = 'WB-003'
  AND NOT EXISTS (
      SELECT 1 FROM `user_activity_logs` l
      WHERE l.user_id = u.id
        AND l.event_type = 'DOCUMENT_VIEW'
        AND l.event_target = '복리후생 정책'
  );

INSERT INTO `user_activity_logs` (user_id, event_type, event_target)
SELECT u.id, 'SUGGESTION_READ', '첫 출근 체크리스트'
FROM `users` u
WHERE u.employee_number = 'WB-004'
  AND NOT EXISTS (
      SELECT 1 FROM `user_activity_logs` l
      WHERE l.user_id = u.id
        AND l.event_type = 'SUGGESTION_READ'
        AND l.event_target = '첫 출근 체크리스트'
  );
