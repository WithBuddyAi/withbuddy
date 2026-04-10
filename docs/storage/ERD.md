# Storage ERD

**현재 버전: v1.0**  
**최종 수정일: 2026-04-09**

## 개요
TenantA 원본 스토리지와 TenantB 백업 스토리지를 함께 운영하기 위한 **스토리지 메타데이터 ERD**를 정리한 문서이다.

현재 구조는 기존 도메인 테이블(`companies`, `users`, `documents`)을 유지하고, 파일 저장 위치와 백업 이력을 추적하는 확장 테이블을 추가하는 방식으로 설계한다.

---

## 1. companies
### 역할
회사의 기본 식별 정보를 저장하는 기준 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : 회사코드, varchar, UNIQUE
- `name` : 회사명, varchar
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명
- 스토리지 메타데이터에서도 회사 경계는 `company_code`를 기준으로 유지한다.

---

## 2. users
### 역할
로그인 사용자를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : FK → `companies.company_code`
- `name` : 사용자 이름, varchar
- `employee_number` : 사번, varchar
- `hire_date` : 입사일, date
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명
- 문서 업로드/다운로드 권한 검증 시 사용자 소속 회사를 확인하는 기준이 된다.

---

## 3. documents
### 역할
문서 도메인 정보(제목/유형/부서/활성 상태)를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : FK → `companies.company_code`, nullable
- `title` : 문서 제목, varchar
- `file_path` : 레거시 문서 경로, varchar
- `document_type` : 문서 유형, varchar
- `department` : 관련 부서, varchar
- `is_active` : 사용 여부, boolean
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명
- `documents`는 문서 비즈니스 메타데이터 중심으로 유지한다.
- 실제 Object Storage 위치/백업 상태는 `document_files`로 분리해 관리한다.

---

## 4. document_files
### 역할
문서 파일의 실제 저장 위치(Primary/Backup), 무결성, 백업 상태를 저장하는 핵심 테이블이다.

### 컬럼
- `id` : PK, bigint
- `document_id` : FK → `documents.id`, UNIQUE
- `company_code` : FK → `companies.company_code`
- `original_file_name` : 원본 파일명, varchar
- `stored_file_name` : 저장 파일명(UUID 기반), varchar
- `content_type` : MIME type, varchar
- `file_size` : 파일 크기(byte), bigint
- `checksum_sha256` : 파일 체크섬, char(64)
- `primary_namespace` : 원본 namespace, varchar
- `primary_bucket` : 원본 bucket, varchar
- `primary_object_key` : 원본 object key, varchar
- `backup_namespace` : 백업 namespace, varchar
- `backup_bucket` : 백업 bucket, varchar
- `backup_object_key` : 백업 object key, varchar, nullable
- `backup_status` : 백업 상태(`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`)
- `backup_attempt_count` : 백업 재시도 횟수, int
- `backup_last_error` : 최근 실패 메시지, varchar, nullable
- `backup_requested_at` : 백업 요청 시각, datetime
- `backup_completed_at` : 백업 완료 시각, datetime, nullable
- `deleted_at` : 소프트 삭제 시각, datetime, nullable
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명
- TenantA 원본 업로드 성공 후 이 테이블에 저장한다.
- 다운로드 시 Primary 우선, 필요 시 Backup fallback 판단 기준으로 사용한다.
- 파일 URL 자체를 영구 저장하지 않고 `namespace + bucket + object_key`를 저장한다.

---

## 5. document_backup_jobs
### 역할
백업 복제 작업 이력과 재시도 상태를 누적 기록하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `document_file_id` : FK → `document_files.id`
- `status` : 작업 상태(`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`)
- `attempt_no` : 시도 번호, int
- `source_namespace` : 원본 namespace, varchar
- `source_bucket` : 원본 bucket, varchar
- `source_object_key` : 원본 object key, varchar
- `target_namespace` : 백업 namespace, varchar
- `target_bucket` : 백업 bucket, varchar
- `target_object_key` : 백업 object key, varchar, nullable
- `error_message` : 실패 상세, varchar, nullable
- `started_at` : 시작 시각, datetime
- `finished_at` : 종료 시각, datetime, nullable
- `created_at` : 생성 일시, datetime

### 설명
- 비동기 백업 워커(RabbitMQ 소비자)가 작업 단위로 이력을 남긴다.
- `document_files.backup_status`는 현재 상태, `document_backup_jobs`는 이력 관리 용도로 분리한다.

---

## 테이블 관계
#### 관계 요약
- `companies` 1 : N `users`
- `companies` 1 : N `documents`
- `companies` 1 : N `document_files`
- `documents` 1 : 1 `document_files`
- `document_files` 1 : N `document_backup_jobs`

#### 관계 설명
- 회사 1개는 여러 문서와 여러 파일 메타데이터를 가질 수 있다.
- 문서 1개는 활성 파일 메타데이터 1개(`document_files`)를 가진다.
- 파일 1개는 백업 재시도 과정에서 여러 작업 이력(`document_backup_jobs`)을 가진다.

---

## ERD 수정 로그
- v1.0 (2026-04-09): `docs/erd/erd.md` 형식에 맞춰 Storage ERD 문서 재작성, `document_files`/`document_backup_jobs` 중심 관계 및 운영 의미를 정리

---

## 한 줄 정리
본 ERD는 기존 WithBuddy 도메인 구조를 유지하면서 TenantA 원본 저장, TenantB 백업 복제, 백업 이력 추적을 위한 스토리지 확장 데이터 구조를 정의한다.
