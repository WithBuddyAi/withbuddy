# ERD

**현재 버전: v1.5**  
**최종 수정일: 2026-04-07**

## 개요
**MVP 기준 ERD(Entity Relationship Diagram)** 를 텍스트로 정리한 문서이다.

현재 MVP에서는 **로그인 사용자 관리**, **사내 문서 기반 Q&A**, **입사 시점별 온보딩 가이드 제공**, **채팅형 메시지 기록 저장**, **사용자 활동 로그 저장**을 중심으로 최소한의 데이터 구조를 설계한다.

---

## 1. companies
### 역할
회사의 기본 정보를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : 회사코드, varchar, UNIQUE
- `name` : 회사명, varchar
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명
- 회사의 기본 식별 정보와 회사명을 관리한다.
- `company_code`는 중복될 수 없으며, 로그인 시 회사 식별값으로 사용한다.

---

## 2. users
### 역할
로그인하는 사용자 정보를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : FK → `companies.company_code`
- `name` : 사용자 이름, varchar
- `employee_number` : 사번, varchar
- `hire_date` : 입사일, date
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명
- 사용자를 식별하기 위한 기본 정보를 저장한다.
- 사용자는 로그인 시 회사코드, 이름, 사번을 입력한다.
- 서버는 입력된 회사코드로 회사를 조회한 뒤, 해당 회사의 `company_code`와 사용자 이름, 사번을 기준으로 사용자를 확인한다.

---

## 3. documents
### 역할
사내 문서 기반 Q&A의 답변 근거가 되는 문서 저장 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : FK → `companies.company_code`, nullable
- `title` : 문서 제목, varchar
- `file_path` : 문서 경로, varchar
- `document_type` : 문서 유형, varchar
- `department` : 관련 부서, varchar
- `is_active` : 사용 여부, boolean
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime

### 설명
- 인사, 행정, 정책, 사규 등 사내 문서 데이터를 저장한다.
- `company_code`가 `null`인 경우 회사 공통 문서로 사용한다.
- `company_code`에 특정 회사코드가 들어 있는 경우 해당 회사 전용 문서로 사용한다.
- 사용자의 질문에 대해 어떤 문서를 근거로 답변했는지 연결할 수 있다.
- `document_type`은 문서의 유형을 구분하기 위한 표준 분류값이며, `POLICY`, `GUIDE`, `FORM`, `NOTICE`, `FAQ`, `LEGAL`, `TEMPLATE` 값을 사용한다.
- `department`는 문서의 관련 부서 또는 업무 영역을 구분하기 위한 표준 분류값이며, `HR`, `FINANCE`, `IT`, `OPS`, `LEGAL`, `GENERAL` 값을 사용한다.

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
- `suggestion_id` : FK → `onboarding_suggestions.id`, nullable
- `sender_type` : 발신 주체 구분값, varchar
- `message_type` : 메시지 유형 구분값, varchar
- `content` : 메시지 내용, text
- `created_at` : 메시지 생성 일시, datetime

### 설명
- 사용자의 질문, 챗봇의 답변, 챗봇의 선제 제안 메시지를 모두 저장한다.
- `sender_type`은 메시지 발신 주체를 구분하며, `USER`, `BOT` 값을 사용한다.
- `message_type`은 메시지 유형을 구분하며, 아래 값을 기준으로 통일해서 사용한다.
  - `user_question` : 신입 사용자가 입력한 질문
  - `rag_answer` : 문서 기반으로 답변이 생성된 메시지
  - `no_result` : 질문 범위는 맞지만 근거 문서나 정보가 없어 답변하지 못한 메시지
  - `out_of_scope` : 서비스 범위를 벗어난 질문에 대한 안내 메시지
  - `suggestion` : 온보딩 가이드 기반 Buddy Nudge 카드 또는 제안 메시지
- `rag_answer`는 답변이 생성된 경우로 간주한다.
- `no_result`는 질문 범위는 맞지만 답변 가능한 정보가 없는 경우로 간주한다.
- 온보딩 가이드 기반 제안인 경우 어떤 온보딩 가이드를 참조했는지 `suggestion_id`로 연결할 수 있다.
- 채팅형 UI에서 시간순 메시지 조회 및 대화 이력 관리에 활용할 수 있다.
---

## 6. user_activity_logs
### 역할
사용자의 서비스 접속 기록과 버튼 클릭 로그를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `user_id` : FK → `users.id`
- `event_type` : 이벤트 유형, varchar
- `event_target` : 이벤트 대상, varchar, nullable
- `created_at` : 이벤트 발생 시각, datetime

### 설명
- 사용자의 서비스 접속 시작 기록과 버튼 클릭 기록을 저장한다.
- `event_type`은 아래 표준값으로 구분한다.
  - `SESSION_START` : 사용자가 로그인에 성공하여 세션이 시작된 시점
  - `BUTTON_CLICK` : 사용자가 특정 버튼 또는 UI 요소를 클릭한 시점
- `SESSION_START` 로그는 로그인 성공 시 서버 내부 로직에서 자동 저장할 수 있다.
- `BUTTON_CLICK` 로그는 프론트엔드 이벤트 수집 또는 별도 로그 수집 API 호출을 통해 저장할 수 있다.
- `event_target`은 `BUTTON_CLICK`인 경우 클릭된 버튼 또는 UI 요소를 저장하며, `SESSION_START`인 경우 `null`일 수 있다.

---

## 테이블 관계
#### 관계 요약
- `companies` 1 : N `users`
- `companies` 1 : N `documents` _(회사별 문서 기준)_
- `users` 1 : N `chat_messages`
- `onboarding_suggestions` 1 : N `chat_messages` _(선택적 연결)_
- `users` 1 : N `user_activity_logs`

#### 관계 설명
- 회사 1개는 여러 명의 사용자를 가질 수 있다.
- 회사 1개는 여러 개의 문서를 가질 수 있다.
- 사용자 1명은 여러 개의 채팅 메시지를 남길 수 있다.
- 온보딩 가이드 1개는 여러 제안 메시지의 원본으로 사용될 수 있다.
- 각 채팅 메시지는 1명의 사용자와 연결되며, 상황에 따라 1개의 문서 또는 1개의 온보딩 가이드와 연결될 수 있다.
- 문서는 `company_code`를 기준으로 회사별 문서로 구분할 수 있으며, `company_code`가 없는 경우 공통 문서로 간주한다.
- 사용자 1명은 여러 개의 활동 로그를 가질 수 있다.
- 활동 로그는 서비스 접속 기록과 버튼 클릭 기록을 포함할 수 있다.

---

## ERD 수정 로그
- v0.1 (2026-03-16): ERD 초안 작성
- v0.2 (2026-03-17): `users`, `documents`, `questions` 테이블에 시간 관련 컬럼(`created_at`, `updated_at`, `answered_at`) 반영
- v0.3 (2026-03-20): `questions` 테이블을 `chat_messages` 구조로 변경하고, `onboarding_suggestions` 테이블 추가
- v1.0 (2026-03-23): `companies` 테이블 추가, 회사 식별 구조를 `company_id` 기준으로 정리, 회사 공통 문서/회사별 문서 구분 구조 반영 및 MVP 기준 ERD 확정
- v1.1 (2026-03-26): `companies` 테이블에 `company_code` 컬럼 추가 및 로그인 식별 기준 반영, `user_activity_logs` 테이블 추가, `chat_messages.message_type` 표준값 정의 및 답변 여부 해석 기준 반영
- v1.2 (2026-03-26): `user_activity_logs`의 이벤트 유형 및 로그 수집 방식 설명 보강
- v1.3 (2026-03-30): `documents` 테이블 `content` 수정
- v1.4 (2026-04-01): `users`와 `documents`의 회사 참조 기준을 `company_code`로 통일하고, 관련 설명 및 관계 문구를 정리
- v1.5 (2026-04-07): `documents.document_type`, `documents.department` 표준 분류값 및 분류 기준 설명 추가, `chat_messages` 테이블 수정, `document_id` 컬럼 삭제
---

## ERD 원본 링크
- dbdiagram: https://dbdiagram.io/d/69b7a6f0fb2db18e3b896ae2

---

## 한 줄 정리
본 ERD는 로그인 사용자 관리, 사내 문서 기반 Q&A, 입사 시점별 온보딩 가이드 제공, 채팅형 메시지 기록 저장, 사용자 활동 로그 저장에 필요한 최소 데이터 구조를 중심으로 설계하였다.
