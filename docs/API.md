# WithBuddy API 명세서

> WithBuddy MVP 기준 REST API 문서

**버전**: 1.4.0  
**최종 업데이트**: 2026-03-26

---

## 1. 문서 범위

이 문서는 **MVP 개발 범위에서 현재 구현했거나 즉시 구현 대상으로 확정된 API만** 다룬다.

### 포함 범위
- 로그인
- 채팅 메시지 목록 조회
- 질문 전송 및 답변 생성
- 온보딩 제안 조회
- 내부 AI 답변 생성 요청 규격
- 토큰 만료 처리 규격

### 제외 범위:
- 토큰 재발급
- 로그아웃
- 내 정보 조회
- 관리자 기능
- 문서 등록/수정/삭제
- 체크리스트
- 기타 미구현 기능

---

## 2. ERD 기준 정리

현재 API 설계는 아래 ERD를 기준으로 한다.

- `companies`
- `users`
- `documents`
- `onboarding_suggestions`
- `chat_messages`
- `user_activity_logs`

---

## 3. API 개요

### Backend Base URL

```text
Development: http://localhost:8080
Production:  https://{withbuddy_api}
```

### Frontend Development Server
```text
Frontend Development: http://localhost:5173
```

### Prefix

모든 API는 아래 prefix를 사용한다.

```text
/api/v1
```

내부 AI 연동 API는 `/internal` 경로를 사용한다.

### 데이터 범위

모든 데이터 조회 및 저장은 로그인한 사용자의 회사 기준으로 처리한다.  
문서 기반 Q&A는 로그인한 사용자의 회사 문서와 공통 문서(`company_id = null`)를 함께 대상으로 처리한다.

### 공통 헤더

```http
Content-Type: application/json
Authorization: Bearer {accessToken}
```

로그인 API는 `Authorization` 헤더가 필요하지 않다.

---

## 4. 표준 응답 형식

### 성공 응답
성공 응답은 각 API 목적에 맞는 JSON 데이터를 반환한다.

### 에러 응답

```json
{
  "timestamp": "2026-03-25T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "message": "잘못된 요청입니다",
  "path": "/api/v1/auth/login"
}
```

### HTTP 상태 코드

- `200 OK`: 성공
- `201 Created`: 리소스 생성 성공
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패 또는 토큰 만료
- `404 Not Found`: 리소스 없음
- `500 Internal Server Error`: 서버 오류

### 공통 에러 코드
- `BAD_REQUEST`: 잘못된 요청
- `UNAUTHORIZED`: 인증 실패
- `TOKEN_EXPIRED`: 로그인 세션 만료
- `TOKEN_MISSING`: 인증 토큰 누락
- `INVALID_TOKEN`: 유효하지 않은 인증 정보
- `USER_NOT_FOUND`: 사용자 정보를 찾을 수 없음
- `ACCESS_DENIED`: 요청 권한 없음
- `NOT_FOUND`: 리소스 없음
- `INTERNAL_SERVER_ERROR`: 서버 내부 오류

---

## 5. 인증 (Authentication)

로그인 성공 시 `accessToken`을 발급한다.  
인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 사용한다.

### 5-1. 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json
```

#### Request Body

```json
{
  "companyCode": "1001",
  "employeeNumber": "20260001",
  "name": "김지원"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 예시값 | 설명 |
|------|------|------|--------|------|
| `companyCode` | `String` | Y | `"1001"` | 회사 식별 코드 |
| `employeeNumber` | `String` | Y | `"20260001"` | 사용자 사번 |
| `name` | `String` | Y | `"김지원"` | 사용자 이름 |


#### 필드 설명

- `companyCode`: 회사코드
- `employeeNumber`: 사번
- `name`: 사용자 이름

#### 동작 규칙
- 사용자는 로그인 시 회사코드, 사번, 이름을 입력한다.
- 서버는 `companyCode`로 `companies`를 조회한다.
- 조회된 회사의 `id`를 기준으로 `users`에서 사용자를 확인한다.
- 일치하는 사용자가 존재하면 로그인에 성공하고 `accessToken`을 발급한다.

#### Response (200 OK)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "companyId": 1,
    "companyCode": "1001",
    "companyName": "테크 주식회사",
    "employeeNumber": "20260001",
    "name": "김지원",
    "hireDate": "2026-03-01"
  }
}
```

#### Error Response (401 Unauthorized)

```json
{
  "timestamp": "2026-03-25T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "회사코드, 사번 또는 이름이 올바르지 않습니다",
  "path": "/api/v1/auth/login"
}
```

### 5-2. 인증 오류 및 토큰 만료 처리

인증이 필요한 API에서 인증 정보가 없거나 올바르지 않으면 아래와 같이 응답한다.

#### Error Response (401 Unauthorized)

```json
{
  "timestamp": "2026-03-25T10:35:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "인증 정보가 올바르지 않습니다",
  "path": "/api/v1/chat/messages"
}
```

액세스 토큰이 만료된 경우 아래와 같이 응답한다.

#### Error Response (401 Unauthorized - Token Expired)

```json
{
  "timestamp": "2026-03-25T10:35:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "TOKEN_EXPIRED",
  "message": "액세스 토큰이 만료되었습니다",
  "path": "/api/v1/chat/messages"
}
```

#### 처리 규칙
- 프론트엔드는 401 응답과 함께 아래 코드 중 하나를 받으면 로그인 페이지로 리다이렉트 처리한다.
  - `TOKEN_MISSING`
  - `TOKEN_EXPIRED`
  - `INVALID_TOKEN`
  - `USER_NOT_FOUND`

---

## 6. MyBuddy

### 6-1. 채팅 메시지 목록 조회

현재 로그인한 사용자의 채팅 메시지를 `createdAt` 오름차순으로 조회한다.

```http
GET /api/v1/chat/messages
Authorization: Bearer {accessToken}
```

#### Response (200 OK)

```json
{
  "messages": [
    {
      "id": 101,
      "senderType": "USER",
      "messageType": "user_question",
      "content": "복지카드는 어떻게 신청하나요?",
      "documentId": null,
      "suggestionId": null,
      "createdAt": "2026-03-24T10:00:00Z"
    },
    {
      "id": 102,
      "senderType": "BOT",
      "messageType": "rag_answer",
      "content": "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.",
      "documentId": 10,
      "suggestionId": null,
      "createdAt": "2026-03-24T10:00:02Z"
    },
    {
      "id": 103,
      "senderType": "BOT",
      "messageType": "suggestion",
      "content": "입사 1일차 안내를 먼저 확인해보세요.",
      "documentId": null,
      "suggestionId": 3,
      "createdAt": "2026-03-24T10:10:00Z"
    }
  ]
}
```

#### 빈 결과 예시 (200 OK)

```json
{
  "messages": []
}
```

#### 설명
- 현재 로그인한 사용자 기준으로 `chat_messages`를 조회한다.
- 문서 기반 답변인 경우 `documentId`가 포함될 수 있다.
- 온보딩 제안 메시지인 경우 `suggestionId`가 포함될 수 있다.
- `senderType`은 `USER`, `BOT` 값을 사용한다.
- `messageType`은 아래 표준값을 사용한다.
  - `user_question`: 신입 사용자가 입력한 질문
  - `rag_answer`: 문서 기반으로 답변이 생성된 메시지
  - `no_result`: 질문 범위는 맞지만 근거 문서나 정보가 없어 답변하지 못한 메시지
  - `out_of_scope`: 서비스 범위를 벗어난 질문에 대한 안내 메시지
  - `suggestion`: 온보딩 가이드 기반 Buddy Nudge 카드 또는 제안 메시지
- 인증 오류와 토큰 만료 처리 방식은 **5-2. 인증 오류 및 토큰 만료 처리**를 따른다.


### 6-2. 질문 전송

사용자가 질문을 보내면 사용자 질문 메시지를 저장하고, AI 답변 메시지를 생성하여 저장한 뒤, 두 메시지를 함께 응답으로 반환한다.

```http
POST /api/v1/chat/messages
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### Request Body
```json
{
  "content": "복지카드는 어떻게 신청하나요?"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 예시값 | 설명 |
|------|------|------|--------|------|
| `content` | `String` | Y | `"복지카드는 어떻게 신청하나요?"` | 사용자가 입력한 질문 내용 |

#### Response (201 Created)
```json
{
  "question": {
    "id": 201,
    "senderType": "USER",
    "messageType": "user_question",
    "content": "복지카드는 어떻게 신청하나요?",
    "documentId": null,
    "suggestionId": null,
    "createdAt": "2026-03-24T10:00:00Z"
  },
  "answer": {
    "id": 202,
    "senderType": "BOT",
    "messageType": "rag_answer",
    "content": "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.",
    "documentId": 10,
    "suggestionId": null,
    "createdAt": "2026-03-24T10:00:02Z"
  }
}
```

#### Error Response (400 Bad Request)

```json
{
  "timestamp": "2026-03-25T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "message": "질문 내용은 비어 있을 수 없습니다",
  "path": "/api/v1/chat/messages"
}
```

#### 값 설명

- `senderType`
  - `USER` : 사용자
  - `BOT` : 챗봇

- `messageType`
  - `user_question`: 사용자 질문
  - `rag_answer`: 문서 기반 답변 생성
  - `no_result`: 질문 범위는 맞지만 문서/정보 부족으로 답변 불가
  - `out_of_scope`: 서비스 범위를 벗어난 질문에 대한 안내
  - `suggestion`: 온보딩 제안 메시지

#### 동작 규칙

- 질문 메시지는 `chat_messages`에 `sender_type = USER`, `message_type = user_question`으로 저장한다.
- 백엔드는 질문 저장 후, 회사 문서와 공통 문서만 필터링하여 `/internal/ai/answer`를 호출한다.
- 내부 AI 응답의 `messageType`은 `rag_answer`, `no_result`, `out_of_scope` 중 하나를 반환해야 한다.
- 답변 메시지는 `chat_messages`에 `sender_type = BOT`으로 저장하고, `message_type`은 내부 AI 응답의 `messageType` 값을 그대로 사용한다.
- 별도의 `isAnswered` 필드는 두지 않으며, 응답 유형은 `messageType` 값으로 해석한다.
- `rag_answer`로 생성되었고 특정 문서를 근거로 삼은 경우 `document_id`를 저장한다.
- `no_result`, `out_of_scope`인 경우 `document_id`는 `null`이다.
- 온보딩 제안 메시지는 이 API가 아니라 온보딩 제안 조회/노출 흐름에서 생성되며, `message_type = suggestion`을 사용한다.
- 인증 오류와 토큰 만료 처리 방식은 **5-2. 인증 오류 및 토큰 만료 처리**를 따른다.

### 6-3. 온보딩 제안 조회
현재 로그인한 사용자의 `hireDate`를 기준으로 노출 대상 온보딩 제안을 조회한다.

```http
GET /api/v1/onboarding-suggestions/me
Authorization: Bearer {accessToken}
```
#### 동작 기준

- `dayOffset = (KST 기준 오늘 날짜 - hireDate) + 1`
- 일치하는 제안이 있으면 반환한다.
- 일치하는 제안이 없으면 빈 배열을 반환한다.

#### Response (200 OK)

```json
{
  "suggestions": [
    {
      "id": 3,
      "title": "입사 1일차 안내",
      "content": "복지 제도와 사내 규정 문서를 먼저 확인해보세요.",
      "dayOffset": 1,
      "createdAt": "2026-03-20T09:00:00Z",
      "updatedAt": "2026-03-23T12:00:00Z"
    }
  ]
}
```

#### 설명

- `users.hire_date`를 기준으로 입사 후 경과 일수를 계산한다.
- 날짜 계산 기준은 **Asia/Seoul(KST)** 로 한다.
- `onboarding_suggestions.day_offset`와 일치하는 데이터를 조회한다.
- 현재 MVP 기준으로 `onboarding_suggestions`는 회사 구분 없이 공통으로 사용한다.
- 인증 오류와 토큰 만료 처리 방식은 **5-2. 인증 오류 및 토큰 만료 처리**를 따른다.

---

## 7. 내부 AI 연동 규격

이 항목은 백엔드 서버와 생성형 AI 서버 간 내부 통신 규격이다.
프론트엔드가 직접 호출하지 않는다.

### 7-1. 연동 흐름

- 사용자가 질문 전송
- 백엔드가 `/api/v1/chat/messages` 요청을 받음
- 백엔드가 `question`, `documents`, `messageHistory`를 구성해서 `/internal/ai/answer` 호출
- AI가 `answer`, `sourceDocumentId`를 반환
- 백엔드가 답변 메시지를 저장하고 최종 응답 반환

### 7-2. 답변 생성 요청

```http
POST /internal/ai/answer
Content-Type: application/json
```

#### Request Body
```json
{
  "user": {
    "id": 1,
    "companyId": 1,
    "companyCode": "1001",
    "name": "김지원",
    "hireDate": "2026-03-01"
  },
  "question": "복지카드는 어떻게 신청하나요?",
  "messageHistory": [
    {
      "senderType": "USER",
      "messageType": "user_question",
      "content": "출근 기록은 어디서 확인하나요?"
    },
    {
      "senderType": "BOT",
      "messageType": "rag_answer",
      "content": "근태 시스템에서 확인할 수 있습니다."
    }
  ],
  "documents": [
    {
      "id": 10,
      "title": "복지카드 신청 안내",
      "content": "복지카드는 관련 안내 문서를 기준으로 신청한다.",
      "documentType": "HR",
      "department": "인사팀"
    }
  ]
}
```

#### Request Field (Top-level)

| 필드 | 타입 | 필수 | 예시값 | 설명 |
|------|------|------|--------|------|
| `user` | `Object` | Y | `{...}` | 현재 로그인한 사용자 정보 |
| `question` | `String` | Y | `"복지카드는 어떻게 신청하나요?"` | 사용자가 입력한 질문 |
| `messageHistory` | `Array<Object>` | Y | `[{...}]` | 이전 대화 이력 |
| `documents` | `Array<Object>` | Y | `[{...}]` | 답변 생성에 사용할 문서 목록 |

#### `user` Field

| 필드 | 타입 | 필수 | 예시값 | 설명 |
|------|------|------|--------|------|
| `id` | `Long` | Y | `1` | 사용자 ID |
| `companyId` | `Integer` | Y | `1` | 사용자 소속 회사 ID |
| `companyCode` | `String` | Y | `"1001"` | 사용자 소속 회사 코드 |
| `name` | `String` | Y | `"김지원"` | 사용자 이름 |
| `hireDate` | `String` | Y | `"2026-03-01"` | 사용자 입사일 (`YYYY-MM-DD`) |

#### `messageHistory` Field

| 필드 | 타입 | 필수 | 예시값 | 설명 |
|------|------|------|--------|------|
| `senderType` | `String` | Y | `"USER"` | 메시지 발신 주체 |
| `messageType` | `String` | Y | `"QUESTION"` | 메시지 유형 |
| `content` | `String` | Y | `"출근 기록은 어디서 확인하나요?"` | 이전 대화 메시지 내용 |

#### `documents` Item Field

| 필드 | 타입 | 필수 | 예시값 | 설명 |
|------|------|------|--------|------|
| `id` | `Long` | Y | `10` | 문서 ID |
| `title` | `String` | Y | `"복지카드 신청 안내"` | 문서 제목 |
| `content` | `String` | Y | `"복지카드는 관련 안내 문서를 기준으로 신청한다."` | 문서 본문 |
| `documentType` | `String` | Y | `"HR"` | 문서 유형 |
| `department` | `String` | Y | `"인사팀"` | 관련 부서 |

#### Response (200 OK)
```json
{
  "answer": "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.",
  "messageType": "rag_answer",
  "sourceDocumentId": 10
}
```

#### 설명

- 백엔드는 현재 로그인한 사용자 기준으로 회사 문서와 공통 문서만 필터링한 뒤 AI 서버에 전달한다.
- AI 서버는 답변 문자열과 답변 유형, 대표 근거 문서 ID 1개를 반환한다.
- `messageType`은 아래 값 중 하나를 사용한다.
  - `rag_answer`: 문서 기반 답변 생성
  - `no_result`: 질문 범위는 맞지만 문서/정보 부족으로 답변 불가
  - `out_of_scope`: 서비스 범위를 벗어난 질문
- 반환된 `sourceDocumentId`는 저장되는 답변 메시지의 `document_id`에 기록한다.
- `rag_answer`인 경우 대표 근거 문서가 있으면 `sourceDocumentId`를 반환할 수 있다.
- `no_result`, `out_of_scope`인 경우 `sourceDocumentId`는 `null`이다.
- `suggestion`은 온보딩 가이드 기반 메시지 유형이므로 내부 AI 답변 응답값으로 사용하지 않는다.

---

## 8. 변경 이력

- **v1.0.0 (2026-03-10)**: 초기 버전 작성
- **v1.1.0 (2026-03-17)**:
  - 멀티 테넌시 구조 적용, 로그인 API 변경, 회사 식별 정보 응답 구조 추가, 회사 관리 API 추가, 데이터 격리 설명 추가, JWT 페이로드 구조 업데이트, 비밀번호 제거
- **v1.2.0 (2026-03-17)**:
  - MVP 기준 구현 범위만 남기도록 문서 범위 재정리, 미구현·변경 가능성이 큰 API를 별도 관리 대상으로 분리, 커스텀 에러코드·Rate Limiting·페이지네이션 공통 정책을 계획 문서로 이동
- **v1.3.0 (2026-03-24)**:
  - API 명세 단순화, `companyId` 기준으로 응답 예시 수정, `auth/refresh`·`users/me`·체크리스트 API 제거, `conversationId` 제거, `MyBuddy` 기준으로 채팅/온보딩 API 재정리, 내부 AI 연동 규격 최소화
- **v1.4.0 (2026-03-26)**:
  - 로그인 기준과 인증 흐름을 수정된 ERD에 맞게 정리, `companyCode` 반영, 토큰 만료 처리, 프론트 개발 서버 주소 추가, 백엔드 서버와 생성형 AI 서버 간 연동 흐름 추가