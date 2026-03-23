# ERD

**현재 버전: v1.0**  
**최종 수정일: 2026-03-23**


## 개요
**MVP 기준 ERD(Entity Relationship Diagram)** 를 텍스트로 정리한 문서이다.

현재 MVP에서는 **로그인 사용자 관리**, **사내 문서 기반 Q&A**, **입사 시점별 온보딩 가이드 제공**, **채팅형 메시지 기록 저장**을 중심으로 최소한의 데이터 구조를 설계한다.


---

## 1. companies
### 역할
회사의 기본 정보를 저장하는 테이블이다.

### 컬럼
- `id` : PK, int
- `name` : 회사명, varchar
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime
### 설명
- 회사의 기본 식별 정보와 회사명을 관리한다.

---

## 2. users
### 역할
로그인하는 사용자 정보를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_id` : FK → `companies.id`
- `name` : 사용자 이름, varchar
- `employee_number` : 사번, varchar
- `hire_date` : 입사일, date
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime
### 설명
- 사용자를 식별하기 위한 기본 정보를 저장한다.
- 로그인 시 회사 정보, 이름, 사번을 기준으로 사용자를 확인한다.

---

## 3. documents
### 역할
사내 문서 기반 Q&A의 답변 근거가 되는 문서 저장 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_id` : FK → `companies.id`, nullable
- `title` : 문서 제목, varchar
- `content` : 문서 내용, text
- `document_type` : 문서 유형, varchar
- `department` : 관련 부서, varchar
- `is_active` : 사용 여부, boolean
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime
### 설명
- 인사, 행정, 정책, 사규 등 사내 문서 데이터를 저장한다.
- `company_id`가 `null`인 경우 회사 공통 문서로 사용한다.
- `company_id`에 특정 회사 ID가 들어 있는 경우 해당 회사 전용 문서로 사용한다.
- 사용자의 질문에 대해 어떤 문서를 근거로 답변했는지 연결할 수 있다.

---

## 4. onboarding_suggestions

### 역할
입사 시점별로 사용자에게 보여줄 온보딩 가이드를 저장하는 테이블이다.

### 컬럼

- `id` : PK, bigint
- `title` : 가이드 제목, varchar
- `content` : 가이드 내용, text
- `day_offset` : 입사 후 노출 시점, int
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명

- 입사 1일차, 2일차, 7일차 등 시점별로 보여줄 온보딩 안내 문구를 저장한다.
- 실제 채팅창에서는 이 테이블의 내용을 기반으로 제안 메시지를 생성하거나 노출할 수 있다.

---

## 5. chat_messages

### 역할
실제 채팅창에 표시되는 질문, 답변, 제안 메시지를 저장하는 테이블이다.

### 컬럼

- `id` : PK, bigint
- `user_id` : FK → `users.id`
- `document_id` : FK → `documents.id`, nullable
- `suggestion_id` : FK → `onboarding_suggestions.id`, nullable
- `sender_type` : 발신 주체 구분값, varchar
- `message_type` : 메시지 유형 구분값, varchar
- `content` : 메시지 내용, text
- `created_at` : 메시지 생성 일시, datetime

### 설명

- 사용자의 질문, 챗봇의 답변, 챗봇의 선제 제안 메시지를 모두 저장한다.
- 문서 기반 답변인 경우 어떤 문서를 근거로 답변했는지 `document_id`로 연결할 수 있다.
- 온보딩 가이드 기반 제안인 경우 어떤 온보딩 가이드를 참조했는지 `suggestion_id`로 연결할 수 있다.
- 채팅형 UI에서 시간순 메시지 조회 및 대화 이력 관리에 활용할 수 있다.

---

## 테이블 관계
#### 관계 요약

- `companies` 1 : N `users`
- `companies` 1 : N `documents`
- `users` 1 : N `chat_messages`
- `documents` 1 : N `chat_messages` _(선택적 연결)_
- `onboarding_suggestions` 1 : N `chat_messages` _(선택적 연결)_

#### 관계 설명

- 회사 1개는 여러 명의 사용자를 가질 수 있다.
- 회사 1개는 여러 개의 문서를 가질 수 있다.
- 사용자 1명은 여러 개의 채팅 메시지를 남길 수 있다.
- 문서 1개는 여러 답변 메시지의 근거로 사용될 수 있다.
- 온보딩 가이드 1개는 여러 제안 메시지의 원본으로 사용될 수 있다.
- 각 채팅 메시지는 1명의 사용자와 연결되며, 상황에 따라 1개의 문서 또는 1개의 온보딩 가이드와 연결될 수 있다.
- 문서는 `company_id`를 기준으로 회사별 문서로 구분할 수 있으며, `company_id`가 없는 경우 공통 문서로 간주한다.

---  

## ERD 수정 로그
- v0.1 (2026-03-16): ERD 초안 작성
- v0.2 (2026-03-17): `users`, `documents`, `questions` 테이블에 시간 관련 컬럼(`created_at`, `updated_at`, `answered_at`) 반영
- v0.3 (2026-03-20): `questions` 테이블을 `chat_messages` 구조로 변경하고, `onboarding_suggestions` 테이블 추가
- v1.0 (2026-03-23): `companies` 테이블 추가, 회사 식별 구조를 `company_id` 기준으로 정리, 회사 공통 문서/회사별 문서 구분 구조 반영 및 MVP 기준 ERD 확정

---  

## ERD 원본 링크
- dbdiagram: https://dbdiagram.io/d/69b7a6f0fb2db18e3b896ae2

---  

## 한 줄 정리
본 ERD는 로그인 사용자 관리, 사내 문서 기반 Q&A, 입사 시점별 온보딩 가이드 제공, 채팅형 메시지 기록 저장에 필요한 최소 데이터 구조를 중심으로 설계하였다.
