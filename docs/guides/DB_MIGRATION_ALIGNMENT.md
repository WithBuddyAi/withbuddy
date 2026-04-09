# DB 마이그레이션 비교 정리

> Sprint 1 DB 마이그레이션 작업 전, 스크럼 설명과 기준 문서를 비교하고 작업 진행 기준과 문서 수정 제안을 정리하는 문서

**최종 업데이트**: 2026-04-09  
**작성일**: 2026-04-09

---

## 1. 목적

이 문서는 다음 목적을 가진다.

- 현재 MVP 구현, 스크럼 설명, 기준 문서를 비교한다.
- `SCRUM-112` ~ `SCRUM-121` 수행 시 어떤 기준으로 작업할지 정한다.
- 문서 간 불일치나 오류가 있으면 수정 제안 대상으로 남긴다.

---

## 2. 현재 구현 기준 요약

현재 백엔드에서 확인된 전제는 다음과 같다.

- 로그인 기능은 이미 구현되어 있음
- `companies`, `users` 엔티티와 로그인 API가 존재함
- 현재 구현은 `company_id`가 아니라 **`company_code` 기준**으로 동작함
- DB 초기화는 Flyway/Liquibase가 아니라 `schema.sql` 자동 실행 방식임

관련 파일:

- [API.md](/C:/Users/aerof/withbuddy/docs/API.md)
- [application.yaml](/C:/Users/aerof/withbuddy/backend/src/main/resources/application.yaml)
- [schema.sql](/C:/Users/aerof/withbuddy/backend/src/main/resources/schema.sql)
- [Company.java](/C:/Users/aerof/withbuddy/backend/src/main/java/com/withbuddy/auth/entity/Company.java)
- [User.java](/C:/Users/aerof/withbuddy/backend/src/main/java/com/withbuddy/auth/entity/User.java)
- [AuthService.java](/C:/Users/aerof/withbuddy/backend/src/main/java/com/withbuddy/auth/service/AuthService.java)

---

## 3. 작업 원칙

이 문서에서는 아래 원칙으로 작업을 진행한다.

- 구현, 스크럼 설명, 기준 문서가 다르면 우선 `docs/erd`, `docs/API.md`를 먼저 참고한다.
- 스크럼 설명은 작업의 출발점으로 보되, 기준 문서와 충돌하면 기준 문서 기준으로 진행한다.
- 기준 문서에도 오류가 있거나 서로 충돌하면 작업을 멈추기보다 오류 내용을 기록하고 수정 제안을 남긴다.

즉, DB 마이그레이션 작업은 스크럼 설명을 참고하되 기준 문서 비교 결과를 우선 반영하는 방식으로 진행한다.

---

## 4. 스크럼별 비교 요약

각 스크럼은 스크럼 설명을 참고하되, 실제 작업은 `docs/erd`, `docs/API.md`와 비교한 결과를 우선 반영한다.

### SCRUM-112 T-05. DB 마이그레이션 스크립트 작성

- 스크럼 설명 비교: 상위 작업 성격이라 기준 문서와 직접 충돌은 적음
- 진행 기준: 하위 테이블별 비교 결과와 문서 수정 제안을 묶어 정리한다.
- 문서 수정 제안: 최종 산출물에 비교 결과와 수정 이력을 함께 남긴다.

### SCRUM-113 ST-001 마이그레이션 도구 선정 및 설정

- 스크럼 설명 비교: 도구 선정은 기준 문서보다 현재 구현 현실의 영향이 큼
- 진행 기준: `schema.sql` 자동 실행 방식과 Flyway 전환 가능성을 비교한 뒤 실행 가능한 방식으로 정한다.
- 문서 수정 제안: 실행 절차가 확정되면 가이드 문서에 초기화 방식과 검증 절차를 명시한다.

### SCRUM-114 ST-002 companies 테이블

- 스크럼 설명 비교: 기준 문서와 큰 충돌 없음
- 진행 기준: `company_code UNIQUE`와 시간 컬럼 구조를 기준 문서에 맞춰 유지한다.
- 문서 수정 제안: 구현과 문서 간 컬럼 설명 차이가 생기면 ERD/API 예시를 함께 보정한다.

### SCRUM-115 ST-003 users 테이블

- 스크럼 설명 비교: 기준 문서와 일치
- 진행 기준: `company_code` FK와 `(company_code, employee_number)` 식별 제약을 유지한다.
- 문서 수정 제안: 로그인 흐름과 사용자 식별 문구가 다르면 API 문서를 기준에 맞게 수정한다.

### SCRUM-116 ST-004 documents 테이블

- 스크럼 설명 비교: `content`, `form_file_url` 중심
- 기준 문서 비교: `documents.content`는 전제되며 `file_path`는 기준 문서에 없음
- 진행 기준: `documents.content`는 유지하고, `form_file_url`와 `is_active`는 기준 문서 반영 상태를 확인하며 진행한다.
- 문서 수정 제안: ERD/API에 `form_file_url`, `is_active`가 누락되어 있으면 명시적으로 보강하고, `file_path`는 구현 참고 사항으로만 정리한다.

### SCRUM-117 ST-005 chat_messages 테이블

- 스크럼 설명 비교: `company_code` 추가 검토 여지가 남아 있음
- 기준 문서 비교: ERD 기준 컬럼은 `user_id`, `suggestion_id`, `sender_type`, `message_type`, `content`, `created_at`
- 진행 기준: 이번 작업은 기준 문서를 따라 `user_id` 중심 구조로 진행하고 `company_code`는 추가하지 않는다.
- 문서 수정 제안: 스크럼 설명에 `company_code` 논의가 남아 있다면 참고 항목으로 낮추고, 필요성이 생기면 ERD/API를 먼저 수정한다.

### SCRUM-118 ST-006 user_activity_logs 테이블

- 스크럼 설명 비교: `event_type` 중심으로 축약될 수 있음
- 기준 문서 비교: ERD에는 `event_target`이 포함되어 있음
- 진행 기준: `event_target`을 유지하는 방향으로 작업한다.
- 문서 수정 제안: 스크럼 설명이 축약돼 있으면 `event_target` 포함 여부를 보강한다.

### SCRUM-119 ST-007 onboarding_suggestions 테이블

- 스크럼 설명 비교: 큰 충돌 없음
- 기준 문서 비교: `day_offset`와 콘텐츠 구조는 기준 문서와 API 예시를 함께 봐야 함
- 진행 기준: 시드와 API 예시가 같은 기준을 사용하도록 맞춘다.
- 문서 수정 제안: `day_offset` 계산 기준이 모호하면 API 문서에 명시한다.

### SCRUM-120 ST-008 초기 테스트 계정 시드 데이터

- 스크럼 설명 비교: 범위는 스크럼에 있으나 구체 예시는 문서에 더 의존함
- 진행 기준: 회사 수, 사용자 수, 입사일 분포, 예시 데이터가 API 문서와 어긋나지 않게 작성한다.
- 문서 수정 제안: 시드 기준이 반복 사용된다면 별도 가이드로 분리한다.

### SCRUM-121 ST-009 전체 마이그레이션 스크립트 실행 및 검증

- 스크럼 설명 비교: 완료 기준은 스크럼에 있으나 실행 환경은 현재 구현을 함께 봐야 함
- 진행 기준: 실제로 실행 가능한 범위에서 MySQL + Backend 검증 절차를 먼저 정리한다.
- 문서 수정 제안: `docker-compose` 기준 검증 절차가 불명확하면 실행 순서를 문서화한다.

---

## 5. 항목별 진행 기준

### 5.1 documents 테이블

비교 기준:

- 스크럼 표기: `content`, `form_file_url`
- 기준 문서: `docs/erd`, `docs/API.md`
- 구현 참고: 일부 `schema.sql`은 `file_path` 기준

진행 기준:

- `documents.content`는 유지한다.
- `form_file_url`는 기준 문서 반영 여부를 확인하며 진행한다.
- `file_path`는 기준 문서에 없으므로 이번 작업 기준에서는 채택하지 않는다.
- `is_active`는 기준 문서 반영 여부를 확인하고 필요 시 문서 수정 제안을 남긴다.

문서 수정 제안:

- ERD와 API 문서에 `form_file_url`, `is_active` 반영 상태가 불명확하거나 누락되어 있으면 명시적으로 정리한다.
- `file_path`는 기준 스키마가 아니라 구현 참고 사항으로만 남긴다.

### 5.2 chat_messages 테이블

비교 기준:

- 스크럼 설명: `company_code` 추가 검토
- 기준 문서: `docs/erd`, `docs/API.md`

진행 기준:

- ERD 기준 컬럼인 `user_id`, `document_id`, `suggestion_id`, `sender_type`, `message_type`, `content`, `created_at` 중심으로 작업한다.
- `company_code`는 기준 문서에 없으므로 추가하지 않는다.

문서 수정 제안:

- 스크럼 설명에 `company_code` 검토가 남아 있다면 기준 문서와 불일치하는 참고 항목으로 표시한다.
- `company_code`가 실제로 필요해지면 ERD와 API 문서를 먼저 수정한 뒤 구현 범위를 넓힌다.

### 5.3 상태값 컬럼 타입

비교 기준:

- 대상 컬럼: `documents.document_type`, `chat_messages.sender_type`, `chat_messages.message_type`, `user_activity_logs.event_type`
- 기준 문서: 값의 의미와 표준값은 문서에 있으나 DB 타입 정책은 별도 확인 필요

진행 기준:

- 우선 `VARCHAR` 기반으로 진행하고 애플리케이션 enum 검증으로 맞춘다.

문서 수정 제안:

- 타입 정책이 반복 논의되지 않도록 DB 타입 선택 원칙을 별도 문서나 가이드에 남긴다.

### 5.4 user_activity_logs 테이블

비교 기준:

- 스크럼 설명: `event_type` 중심일 수 있음
- 기준 문서: `event_target` 포함

진행 기준:

- `event_target`을 유지한다.

문서 수정 제안:

- 스크럼 설명에 `event_target`이 빠져 있으면 보완한다.

### 5.5 onboarding_suggestions 시드 기준

비교 기준:

- API 기준: D+0 = 1, D+3 = 4, D+7 = 8, D+30 = 31

진행 기준:

- 시드 데이터와 API 예시를 위 기준으로 통일한다.

문서 수정 제안:

- 기준이 분산되어 있으면 한 곳에 대표 기준을 모아 적는다.

### 5.6 마이그레이션 도구

비교 기준:

- 현재 구현: `schema.sql` 자동 실행
- 작업 요구: 버전 관리형 마이그레이션 가능성 검토

진행 기준:

- 현재 구현 제약과 협업 방식을 같이 고려해 도구를 정한다.

문서 수정 제안:

- 도구가 확정되면 초기화 방식, 실행 순서, 검증 절차를 함께 문서화한다.

### 5.7 검증 범위

비교 기준:

- 스크럼 설명: 완료 기준 확인 필요
- 현재 구현: `docker-compose`에 DB/Backend 구성이 완비되지 않았을 수 있음

진행 기준:

- 우선 MySQL + Backend 기준으로 실행 가능한 검증 절차를 정리한다.

문서 수정 제안:

- `docker-compose` 검증 범위와 성공 기준을 명시적으로 적는다.

---

## 6. 진행 순서

1. `SCRUM-113`에서 마이그레이션 도구와 실행 방식을 정리한다.
2. `SCRUM-114` ~ `SCRUM-119`를 스크럼 설명과 기준 문서 비교 후 DDL로 옮긴다.
3. `SCRUM-120` 시드 데이터를 API 예시와 맞춰 작성한다.
4. `SCRUM-121`에서 실행 가능한 범위의 검증 절차를 확인한다.
5. `SCRUM-112`에서 비교 결과와 문서 수정 제안을 최종 정리한다.

---

## 7. 공유용 메시지 초안

```text
DB 마이그레이션 작업은 스크럼 설명을 참고하되, 실제 작업 기준은 docs/erd와 docs/API.md를 우선 적용합니다.

작업 중 기준 문서와 구현이 다르면 기준 문서 기준으로 진행하고, 기준 문서에도 누락이나 오류가 있으면 수정 제안을 함께 남깁니다.

현재 주요 비교 결과는 아래와 같습니다.
- documents는 content 기준을 유지하고 file_path는 기준 스키마에서 제외합니다.
- chat_messages는 ERD 기준으로 user_id 중심 구조로 진행하고 company_code는 추가하지 않습니다.
- user_activity_logs는 event_target을 유지합니다.
- 시드와 검증 절차는 API 예시와 실제 실행 가능 범위를 같이 맞춥니다.
```

---

## 8. 변경 이력

- 2026-04-09: 스크럼 설명과 기준 문서를 비교해 작업 기준과 문서 수정 제안을 정리하는 방향으로 문서 전체 재구성
