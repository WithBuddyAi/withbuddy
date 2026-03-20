# ERD

## 개요
**MVP 기준 ERD(Entity Relationship Diagram)** 를 텍스트로 정리한 문서이다.

현재 MVP에서는 **로그인 사용자 관리**, **사내 문서 기반 Q&A**, **입사 시점별 온보딩 가이드 제공**, **채팅형 메시지 기록 저장**을 중심으로 최소한의 데이터 구조를 설계한다.
  
---

## 1. users
### 역할
로그인하는 사용자 정보를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : 회사 식별값, int
- `name` : 사용자 이름, varchar
- `employee_number` : 사번, varchar
- `hire_date` : 입사일, date
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime
### 설명
- 사용자를 식별하기 위한 기본 정보를 저장한다.
- 로그인 시 회사 식별값, 이름, 사번을 기준으로 사용자를 확인한다.
- 추후 권한(Role) 관리가 필요할 경우 관련 컬럼을 추가할 수 있다.

---

## 2. documents
### 역할
사내 문서 기반 Q&A의 답변 근거가 되는 문서 저장 테이블이다.

### 컬럼
- `id` : PK, bigint
- `title` : 문서 제목, varchar
- `content` : 문서 내용, text
- `document_type` : 문서 종류, varchar
- `department` : 관련 부서, varchar
- `status` : 문서 사용 유무, varchar
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime
### 설명
- 인사, 행정, 정책, 사규 등 사내 문서 데이터를 저장한다.
- 사용자의 질문에 대해 어떤 문서를 근거로 답변했는지 연결할 수 있다.
- 추후 문서 카테고리, 문서 유형, 시행일, 상태값 등의 컬럼을 확장할 수 있다.

---

## 3. onboarding_suggestions

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

## 4. chat_messages

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

- `users` 1 : N `chat_messages`
- `documents` 1 : N `chat_messages`
- `onboarding_suggestions` 1 : N `chat_messages`

#### 관계 설명

- 사용자 1명은 여러 개의 채팅 메시지를 남길 수 있다.
- 문서 1개는 여러 답변 메시지의 근거로 사용될 수 있다.
- 온보딩 가이드 1개는 여러 제안 메시지의 원본으로 사용될 수 있다.
- 각 채팅 메시지는 1명의 사용자와 연결되며, 상황에 따라 1개의 문서 또는 1개의 온보딩 가이드와 연결될 수 있다.

---  

## ERD 수정 로그
- 2026-03-16: ERD 초안 작성
- 2026-03-17: users, documents, questions 테이블에 시간 관련 컬럼(created_at, updated_at, answered_at) 반영
- 2026-03-20: `questions` 테이블을 `chat_messages` 구조로 변경하고, `onboarding_suggestions` 테이블 추가

---  

## ERD 원본 링크
- dbdiagram: https://dbdiagram.io/d/69b7a6f0fb2db18e3b896ae2

---  

## 한 줄 정리
본 ERD는 로그인 사용자 관리, 사내 문서 기반 Q&A, 입사 시점별 온보딩 가이드 제공, 채팅형 메시지 기록 저장에 필요한 최소 데이터 구조를 중심으로 설계하였다.
