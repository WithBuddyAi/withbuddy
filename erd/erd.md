# ERD

## 개요
**MVP 기준 ERD(Entity Relationship Diagram)** 를 텍스트로 정리한 문서이다.
  
---

## 1. users
### 역할
로그인하는 사용자 정보를 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `company_code` : 회사 식별값, int
- `name` : 사용자 이름, varchar
- `employee_number` : 사번, varchar
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
- `department` : 관련 부서, varchar
- `created_at` : 생성 일시, datetime
- `updated_at` : 수정 일시, datetime
### 설명
- 사내 문서 기반 질문 응답에 활용되는 문서 데이터를 저장한다.  
- 질문에 대해 어떤 문서를 근거로 답변했는지 연결할 수 있다.  
- 추후 문서 카테고리, 문서 유형(공식/비공식), 작성자, 접근 권한 등의 컬럼을 확장할 수 있다.

---

## 3. questions
### 역할
사용자의 질문과 시스템의 답변을 저장하는 테이블이다.

### 컬럼
- `id` : PK, bigint
- `user_id` : FK → `users.id`
- `document_id` : FK → `documents.id`
- `question_text` : 질문 내용, text
- `answer_text` : 답변 내용, text
- `created_at` : 질문 생성 일시, datetime
- `answered_at` : 답변 완료 일시, datetime
### 설명  
- 어떤 사용자가 질문했는지 저장한다.  
- 질문에 대해 어떤 문서를 근거로 답변했는지 저장한다.  
- 사용자 질문 이력 및 최근 질문 내역 조회에 활용할 수 있다.  
- 현재 MVP에서는 질문과 답변을 하나의 테이블에서 관리한다.

---

## 테이블 관계
#### 관계 요약
- `users` 1 : N `questions`
- `documents` 1 : N `questions`

#### 관계 설명
- 사용자 1명은 여러 개의 질문을 작성할 수 있다.  
- 문서 1개는 여러 질문의 답변 근거로 사용될 수 있다.  
- 각 질문은 1명의 사용자와 연결되며, 1개의 근거 문서와 연결된다.

---

## 설계 범위  
현재 ERD는 MVP 범위에 맞춰 최소 구조로 설계하였다.  
  
포함 범위:  
- 사용자 식별  
- 사내 문서 저장  
- 질문/답변 이력 저장  
  
미포함 범위:  
- 권한(Role) 관리  
- 문서 카테고리 세분화  
- 공식/비공식 문서 구분  
- 슬랙 연동 로그  
- 기록 페이지 및 회고 데이터  
- 온보딩 로드맵 및 체크리스트 데이터  
  
위 항목들은 추후 기능 확장 단계에서 추가 설계할 수 있다.  
  
---  
  
## 한 줄 정리  
본 ERD는 로그인 및 문서 기반 Q&A 기능 구현에 필요한 최소 데이터 구조로, `users`, `documents`, `questions` 테이블을 중심으로 설계하였다.
