# DB 마이그레이션 가이드

**버전**: 0.7.0
**최종 수정일**: 2026-04-14  
**대상 SCRUM**: SCRUM-112 ~ SCRUM-121, SCRUM-168 ~ SCRUM-169 (스토리지 테이블 추가)

---

## 1. 개요

이 문서는 WithBuddy 백엔드의 DB 마이그레이션 방식, 파일 구조, 실행 절차, 운영 규칙을 정리한다.

기존 `schema.sql` 자동 실행 방식에서 **Flyway 버전 관리형 마이그레이션**으로 전환하였다.  
`schema.sql`은 참고용으로 유지하되 자동 실행은 비활성화되어 있다.

---

## 2. 도구

| 항목 | 내용 |
|---|---|
| 도구 | Flyway |
| 의존성 | `flyway-core`, `flyway-mysql` |
| 마이그레이션 위치 | `backend/src/main/resources/db/migration/` |
| 네이밍 규칙 | `V{n}__{snake_case_description}.sql` |
| 실행 시점 | Spring Boot 기동 시 자동 실행 |
| 이력 관리 | `flyway_schema_history` 테이블 (자동 생성) |

---

## 3. 마이그레이션 파일 목록

| 버전 | 파일명 | 내용 | SCRUM |
|---|---|---|---|
| V1 | `V1__create_companies.sql` | companies 테이블 생성 | SCRUM-114 |
| V2 | `V2__create_users.sql` | users 테이블 생성 | SCRUM-115 |
| V3 | `V3__create_documents.sql` | documents 테이블 생성 | SCRUM-116 |
| V4 | `V4__create_onboarding_suggestions.sql` | onboarding_suggestions 테이블 생성 | SCRUM-119 |
| V5 | `V5__create_chat_messages.sql` | chat_messages 테이블 생성 | SCRUM-117 |
| V6 | `V6__create_user_activity_logs.sql` | user_activity_logs 테이블 생성 | SCRUM-118 |
| V7 | `V7__create_document_files.sql` | document_files 테이블 생성 | SCRUM-168 |
| V8 | `V8__create_document_backup_jobs.sql` | document_backup_jobs 테이블 생성 | SCRUM-169 |
| V9 | `V9__seed_data.sql` | 초기 테스트 시드 데이터 삽입 | SCRUM-120 |
| V10 | `V10__backfill_seed_rows_idempotent.sql` | V9 시드 누락 보정 (행 단위 idempotent) | SCRUM-120 |

> V4(onboarding_suggestions)가 V5(chat_messages)보다 먼저 실행되는 이유는 FK 의존성 때문이다.  
> V7(document_files)은 documents(V3)를 참조하므로 V3 이후에 실행되어야 한다.  
> V8(document_backup_jobs)은 document_files(V7)를 참조하므로 V7 이후에 실행되어야 한다.  
> V9(seed_data)는 모든 DDL 완료 후 실행된다.  
> V10은 V9의 테이블 단위 조건으로 인해 누락될 수 있는 시드를 행 단위 존재 체크로 보정한다.

---

## 4. 테이블 스키마

### 4.1 companies

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자 |
| `company_code` | VARCHAR(20) | NOT NULL, UNIQUE | 회사 식별 코드 (멀티테넌시 키) |
| `name` | VARCHAR(100) | NOT NULL | 회사명 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 수정 일시 |

### 4.2 users

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자 |
| `company_code` | VARCHAR(20) | NOT NULL, FK → companies | 소속 회사 코드 |
| `name` | VARCHAR(20) | NOT NULL | 사용자 이름 |
| `employee_number` | VARCHAR(20) | NOT NULL | 사번 |
| `hire_date` | DATE | NOT NULL | 입사일 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 수정 일시 |

- `(company_code, employee_number)` 복합 UNIQUE 제약

### 4.3 documents

| 컬럼 | 타입 | 제약 | 설명                                    |
|---|---|---|---------------------------------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자                                   |
| `company_code` | VARCHAR(20) | NULL, FK → companies | 소속 회사 코드 (NULL = 공통 문서)               |
| `title` | VARCHAR(200) | NOT NULL | 문서 제목                                 |
| `file_path` | VARCHAR(500) | NOT NULL | 스토리지 오브젝트 경로                          |
| `document_type` | VARCHAR(50) | NOT NULL | 문서 유형 (HR, ADMIN, WELFARE, IT, LEGAL) |
| `department` | VARCHAR(50) | NOT NULL | 부서                                    |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | 활성 여부                                 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시                                 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 수정 일시                                 |

- `company_code = NULL`인 문서는 전체 회사 공통 문서로 처리한다.
- `file_path`는 스토리지 업로드 메타데이터 기반 구조로 유지한다 (SCRUM 스펙의 `form_file_url`에 해당).

### 4.4 onboarding_suggestions

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자 |
| `title` | VARCHAR(255) | NOT NULL | 제안 제목 |
| `content` | TEXT | NOT NULL | 제안 내용 |
| `day_offset` | INT | NOT NULL | 노출 기준 일수 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 수정 일시 |

- `day_offset` 계산식: `(KST 오늘 - hire_date) + 1`
- MVP 기준 회사 구분 없이 공통 사용

| day_offset 값 | 의미 |
|---|---|
| 1 | 입사 당일 (D+0) |
| 4 | 입사 3일 후 (D+3) |
| 8 | 입사 7일 후 (D+7) |
| 31 | 입사 30일 후 (D+30) |

### 4.5 chat_messages

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자 |
| `user_id` | BIGINT | NOT NULL, FK → users | 사용자 |
| `document_id` | BIGINT | NULL, FK → documents | 참조 문서 (RAG 출처 추적용) |
| `suggestion_id` | BIGINT | NULL, FK → onboarding_suggestions | 연결된 온보딩 제안 |
| `sender_type` | VARCHAR(20) | NOT NULL | 발신자 유형 (`USER` \| `BOT`) |
| `message_type` | VARCHAR(30) | NOT NULL | 메시지 유형 |
| `content` | TEXT | NOT NULL | 메시지 내용 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시 |

**message_type 값**

| 값 | 설명 |
|---|---|
| `user_question` | 사용자 질문 |
| `rag_answer` | 문서 기반 AI 답변 |
| `no_result` | 답변 불가 (문서 부족) |
| `out_of_scope` | 서비스 범위 외 질문 |
| `suggestion` | 온보딩 제안 메시지 |

- `document_id`는 현재 Entity에 미반영 상태. 향후 RAG 응답 출처 추적 기능 구현 시 반영 예정.

### 4.6 user_activity_logs

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자 |
| `user_id` | BIGINT | NOT NULL, FK → users | 사용자 |
| `event_type` | VARCHAR(30) | NOT NULL | 이벤트 유형 |
| `event_target` | VARCHAR(100) | NULL | 이벤트 대상 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시 |

**event_type 값 (현행)**

| 값 | 설명 |
|---|---|
| `SESSION_START` | 세션 시작 |
| `BUTTON_CLICK` | 버튼 클릭 |

- `event_type`은 ENUM 대신 VARCHAR로 관리하여 이후 이벤트 유형 추가 시 마이그레이션 없이 확장 가능.

### 4.7 document_files

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자 |
| `document_id` | BIGINT | NOT NULL, UNIQUE, FK → documents | 연결 문서 |
| `company_code` | VARCHAR(20) | NULL, FK → companies | 소속 회사 (NULL = 공통) |
| `original_file_name` | VARCHAR(255) | NOT NULL | 원본 파일명 |
| `stored_file_name` | VARCHAR(255) | NOT NULL | 저장 파일명 |
| `content_type` | VARCHAR(120) | NOT NULL | MIME 타입 |
| `file_size` | BIGINT | NOT NULL | 파일 크기 (bytes) |
| `checksum_sha256` | CHAR(64) | NOT NULL | SHA-256 체크섬 |
| `primary_namespace` | VARCHAR(120) | NOT NULL | 주 스토리지 네임스페이스 |
| `primary_bucket` | VARCHAR(120) | NOT NULL | 주 스토리지 버킷 |
| `primary_object_key` | VARCHAR(500) | NOT NULL, UNIQUE (namespace+bucket+key) | 주 오브젝트 키 |
| `backup_namespace` | VARCHAR(120) | NOT NULL | 백업 스토리지 네임스페이스 |
| `backup_bucket` | VARCHAR(120) | NOT NULL | 백업 스토리지 버킷 |
| `backup_object_key` | VARCHAR(500) | NULL | 백업 오브젝트 키 |
| `backup_status` | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | 백업 상태 |
| `backup_attempt_count` | INT | NOT NULL, DEFAULT 0 | 백업 시도 횟수 |
| `backup_last_error` | VARCHAR(500) | NULL | 마지막 오류 메시지 |
| `backup_requested_at` | DATETIME | NOT NULL, DEFAULT NOW | 백업 요청 일시 |
| `backup_completed_at` | DATETIME | NULL | 백업 완료 일시 |
| `deleted_at` | DATETIME | NULL | 소프트 삭제 일시 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 수정 일시 |

**backup_status 값**: `PENDING` | `IN_PROGRESS` | `COMPLETED` | `FAILED`

**인덱스**
- `idx_document_files_company_created`: (company_code, created_at)
- `idx_document_files_backup_status`: (backup_status, backup_requested_at)

### 4.8 document_backup_jobs

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 식별자 |
| `document_file_id` | BIGINT | NOT NULL, FK → document_files | 대상 파일 |
| `status` | VARCHAR(20) | NOT NULL | 작업 상태 |
| `attempt_no` | INT | NOT NULL | 시도 순번 |
| `source_namespace` | VARCHAR(120) | NOT NULL | 소스 네임스페이스 |
| `source_bucket` | VARCHAR(120) | NOT NULL | 소스 버킷 |
| `source_object_key` | VARCHAR(500) | NOT NULL | 소스 오브젝트 키 |
| `target_namespace` | VARCHAR(120) | NOT NULL | 대상 네임스페이스 |
| `target_bucket` | VARCHAR(120) | NOT NULL | 대상 버킷 |
| `target_object_key` | VARCHAR(500) | NULL | 대상 오브젝트 키 |
| `error_message` | VARCHAR(1000) | NULL | 오류 메시지 |
| `started_at` | DATETIME | NOT NULL | 작업 시작 일시 |
| `finished_at` | DATETIME | NULL | 작업 완료 일시 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성 일시 |

**status 값**: `PENDING` | `IN_PROGRESS` | `COMPLETED` | `FAILED` | `CANCELLED`

**인덱스**
- `idx_backup_jobs_file_attempt`: (document_file_id, attempt_no)
- `idx_backup_jobs_status_created`: (status, created_at)

---

## 5. 시드 데이터 (V9, V10 보정 포함)

### 삽입 조건

테이블별로 **데이터가 하나라도 존재하면 해당 테이블의 시드 전체를 건너뛴다**.

```sql
INSERT INTO {table} (...)
SELECT ...
WHERE NOT EXISTS (SELECT 1 FROM {table} LIMIT 1);
```

### companies

| company_code | name |
|---|---|
| WB0001 | 테크 주식회사 |
| WB0002 | 넥스트스텝 주식회사 |

### users

기준일: 2026-04-14

| company_code | name | employee_number | hire_date | day_offset |
|---|---|---|---|---|
| WB0001 | 김민준 | 20260001 | 2026-04-14 | 1 (D+0) |
| WB0001 | 이서연 | 20260002 | 2026-04-11 | 4 (D+3) |
| WB0001 | 박도현 | 20260003 | 2026-04-07 | 8 (D+7) |
| WB0001 | 최지아 | 20260004 | 2026-03-15 | 31 (D+30) |
| WB0002 | 정하은 | 20260001 | 2026-04-14 | 1 (D+0) |
| WB0002 | 강준서 | 20260002 | 2026-04-11 | 4 (D+3) |

### documents (공통 법령 문서)

| title | document_type | department |
|---|---|---|
| 남녀고용평등과 일 · 가정 양립 지원에 관한 법률 | LEGAL | LEGAL |
| 근로자퇴직급여 보장법 | LEGAL | LEGAL |
| 최저임금법 | LEGAL | LEGAL |
| 근로기준법 | LEGAL | LEGAL |

### onboarding_suggestions

| title | day_offset |
|---|---|
| 입사 1일차 안내 | 1 |
| 입사 4일차 안내 | 4 |
| 입사 8일차 안내 | 8 |
| 입사 31일차 안내 | 31 |

---

## 6. 로컬 실행 및 검증

### 사전 조건

- MySQL 서버 실행 중
- `application-local.yml` DB 연결 정보 확인

### 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Spring Boot 기동 시 Flyway가 자동으로 V1~V10을 순서대로 실행한다.

### 검증 쿼리

```sql
-- 1. Flyway 실행 이력 확인 (V1~V10 모두 success=1이어야 함)
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- 2. 테이블 생성 확인
SHOW TABLES;

-- 3. 시드 데이터 확인
SELECT COUNT(*) FROM companies;               -- 2 이상
SELECT COUNT(*) FROM users;                  -- 6 이상
SELECT COUNT(*) FROM documents;              -- 4 이상
SELECT COUNT(*) FROM onboarding_suggestions; -- 4

-- 4. FK 제약 조건 확인
SHOW CREATE TABLE chat_messages;
SHOW CREATE TABLE user_activity_logs;
SHOW CREATE TABLE document_files;
SHOW CREATE TABLE document_backup_jobs;
```

### 로그인 API 테스트

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"companyCode":"WB0001","employeeNumber":"20260001","name":"김민준"}'
```

---

## 7. 운영 규칙

### 마이그레이션 파일 수정 금지

Flyway는 적용된 마이그레이션 파일의 체크섬을 검증한다.  
**이미 DB에 적용된 파일을 수정하면 기동 시 오류가 발생한다.**

- 스키마 변경이 필요한 경우 반드시 새 버전 파일을 추가한다.
- 예: `V10__add_column_to_users.sql`

### 파일 작성 시 주의사항

- 한글이 포함된 SQL 파일은 UTF-8로 저장한다.
- DDL은 `CREATE TABLE IF NOT EXISTS`를 사용한다.
- 시드 데이터는 `WHERE NOT EXISTS (SELECT 1 FROM {table} LIMIT 1)` 조건을 사용한다.

---

## 8. DB_DDL.sql과의 관계

`docs/storage/DB_DDL.sql`은 기존 DB 환경에서 수동 실행을 위해 작성된 참고용 파일이다.  
Flyway 마이그레이션 도입 이후 해당 파일의 DDL은 V7, V8로 이관되었으며 아래 차이가 있다.

| 항목 | DB_DDL.sql | Flyway 마이그레이션 |
|---|---|---|
| 인덱스 생성 방식 | 동적 SQL (SET @sql/PREPARE/EXECUTE) | 일반 CREATE INDEX |
| 실행 대상 | 기존 DB (멱등성 보장 필요) | 신규 DB (Flyway 버전 관리) |
| 관리 주체 | 수동 실행 | Spring Boot 기동 시 자동 실행 |

---

## 9. 관련 문서

- [ERD](../erd/erd.md)
- [API 명세](../API.md)

