# WithBuddy API 명세서

> WithBuddy MVP 기준 REST API 문서
> 
**버전**: 0.9.1
**최종 업데이트**: 2026-04-15

---

## 1. 문서 범위

이 문서는 **MVP 개발 범위에서 현재 구현했거나 즉시 구현 대상으로 확정된 API만** 다룬다.

### 포함 범위
- 로그인
- 채팅 메시지 목록 조회
- 질문 전송 및 답변 생성
- 온보딩 제안 조회
- 빠른 질문 목록 조회
- 내부 AI 답변 생성 요청 규격
- 토큰 만료 처리 규격
- 스토리지 문서 API

### 제외 범위
- 토큰 재발급
- 로그아웃
- 내 정보 조회
- 관리자 기능
- 체크리스트
- 기타 미구현 기능

---

## 2. ERD 기준 정리

현재 API 설계는 아래 ERD를 기준으로 한다.

- `companies`
- `users`
- `documents`
- `document_files`
- `document_backup_jobs`
- `onboarding_suggestions`
- `chat_messages`
- `chat_message_documents`
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

모든 **공개 API**는 아래 prefix를 사용한다.

```text
/api/v1
```

내부 AI 연동 API는 `/internal` 경로를 사용한다.

### 데이터 범위

모든 데이터 조회 및 저장은 로그인한 사용자의 회사 기준으로 처리한다.  
문서 기반 Q&A는 로그인한 사용자의 회사 문서와 공통 문서(`company_code = null`)를 함께 대상으로 처리한다.

### 공통 헤더

```http
Content-Type: application/json
Authorization: Bearer {accessToken}
```

로그인 API는 `Authorization` 헤더가 필요하지 않다.

### Swagger (OpenAPI)

본 프로젝트의 REST API는 Swagger(OpenAPI) 기반으로 확인할 수 있다.  
실행 중인 백엔드 서버에서 Swagger UI를 통해 요청/응답 스키마와 엔드포인트를 확인한다.

```text
Local Swagger UI: http://localhost:8080/swagger-ui/index.html
OpenAPI Docs:     http://localhost:8080/v3/api-docs
```

- Swagger UI는 현재 구현된 API 기준으로 동작한다.
- 본 문서는 MVP 범위, 정책, 동작 규칙, 내부 연동 기준을 함께 설명하기 위한 문서다.
- 상세 요청/응답 스키마 및 테스트는 Swagger UI를 우선 확인한다.

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
  "errors": [
    {
      "field": "companyCode",
      "message": "회사 코드는 필수입니다."
    },
    {
      "field": "employeeNumber",
      "message": "사번은 필수입니다."
    },
    {
      "field": "name",
      "message": "이름은 필수입니다."
    }
  ],
  "path": "/api/v1/auth/login"
}
```

### 에러 응답 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| `timestamp` | `String` | 에러 발생 시각 |
| `status` | `Number` | HTTP 상태 코드 |
| `error` | `String` | HTTP 상태 이름 |
| `code` | `String` | 서비스 에러 코드 |
| `errors` | `Array<Object>` | 상세 에러 목록 |
| `errors[].field` | `String` | 오류가 발생한 필드명 또는 오류 대상 |
| `errors[].message` | `String` | 상세 오류 메시지 |
| `path` | `String` | 요청 경로 |

### HTTP 상태 코드

- `200 OK`: 성공
- `201 Created`: 리소스 생성 성공
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패 또는 토큰 만료
- `403 Forbidden`: 요청 권한 없음
- `404 Not Found`: 리소스 없음
- `500 Internal Server Error`: 서버 오류

#### 로그인 API (`POST /api/v1/auth/login`) 상태 코드

- `200 OK`: 로그인 성공
- `400 Bad Request`: 요청값 검증 실패
- `401 Unauthorized`: 로그인 실패(회사코드/사번/이름 불일치)

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
  "companyCode": "WB1001",
  "employeeNumber": "20260001",
  "name": "김지원"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 예시값          | 설명 | 상세 규칙 |
|------|------|------|--------------|------|-----------|
| `companyCode` | `String` | Y | `"WB1001"`   | 회사 식별 코드 | 길이: 1~20자 / 허용 문자: 영문 대소문자 + 숫자 / 특수문자·공백 불가 |
| `employeeNumber` | `String` | Y | `"20260001"` | 사용자 사번 | 길이: 1~20자 / 허용 문자: 영문 대소문자 + 숫자 / 특수문자·공백 불가 |
| `name` | `String` | Y | `"김지원"`      | 사용자 이름 | 길이: 1~20자 / 허용 문자: 한글 + 영문 대소문자 / 특수문자·공백·숫자 불가 |

#### 동작 규칙
- 사용자는 로그인 시 회사코드, 사번, 이름을 입력한다.
- 서버는 입력된 `companyCode`로 `companies`를 조회한다.
- 서버는 조회된 `company_code`와 사용자 이름, 사번을 기준으로 `users`에서 사용자를 확인한다.
- 일치하는 사용자가 존재하면 로그인에 성공하고 `accessToken`을 발급한다.
- 로그인에 성공하면 `user_activity_logs`에 `event_type = SESSION_START`, `event_target = LOGIN` 로그를 기록한다.
- 로그인 성공 시의 `SESSION_START` 로그는 재로그인 시점 추적 용도로 사용한다.

#### Response (200 OK)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "companyCode": "WB1001",
    "companyName": "테크 주식회사",
    "employeeNumber": "20260001",
    "name": "김지원",
    "hireDate": "2026-03-01"
  }
}
```

#### Error Response (400 Bad Request)

```json
{
  "timestamp": "2026-04-03T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "errors": [
    {
      "field": "companyCode",
      "message": "회사 코드는 필수입니다."
    },
    {
      "field": "employeeNumber",
      "message": "사번은 필수입니다."
    },
    {
      "field": "name",
      "message": "이름은 필수입니다."
    }
  ],
  "path": "/api/v1/auth/login"
}
```

#### 검증 규칙
- `companyCode`, `employeeNumber`, `name`은 필수값이다.
- 각 필드는 공백만 입력할 수 없다.
- 길이 제한을 초과하면 `400 Bad Request`를 반환한다.
- 입력값 검증 실패 시 각 필드별 오류 메시지는 `errors` 배열에 담아 반환한다.
- 응답 형식은 **4. 표준 응답 형식 > 에러 응답**을 따른다.

#### Error Response (401 Unauthorized)

```json
{
  "timestamp": "2026-03-25T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "errors": [
    {
      "field": "login",
      "message": "회사코드, 사번 또는 이름이 올바르지 않습니다."
    }
  ],
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
  "errors": [
    {
      "field": "auth",
      "message": "인증 정보가 올바르지 않습니다."
    }
  ],
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
  "errors": [
    {
      "field": "token",
      "message": "액세스 토큰이 만료되었습니다."
    }
  ],
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
`date`가 없으면 전체 메시지를 조회하고, `date`가 있으면 해당 날짜의 메시지만 `createdAt` 오름차순으로 조회한다.

```http
GET /api/v1/chat/messages?date=2026-03-24
Authorization: Bearer {accessToken}
```

### Query Parameter
`date` (optional, `yyyy-MM-dd`)
- 지정하지 않으면 현재 로그인한 사용자의 전체 채팅 메시지를 조회한다.
- 지정하면 해당 날짜의 채팅 메시지만 조회한다.

#### Response (200 OK)

```json
{
  "messages": [
    {
      "id": 101,
      "suggestionId": null,
      "documentIds": [],
      "documents": [],
      "senderType": "USER",
      "messageType": "user_question",
      "content": "복지카드 신청 양식은 어디서 받나요?",
      "createdAt": "2026-03-24T10:00:00Z"
    },
    {
      "id": 102,
      "suggestionId": null,
      "documentIds": [10, 11],
      "documents": [
        {
          "documentId": 10,
          "title": "복지카드 신청 안내",
          "documentType": "GUIDE",
          "file": null
        },
        {
          "documentId": 11,
          "title": "복지카드 신청서",
          "documentType": "TEMPLATE",
          "file": {
            "fileName": "welfare-card-application.docx",
            "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "downloadUrl": "/api/v1/documents/11/download",
            "fileApiPath": "/api/v1/documents/11/file",
            "directReturn": true
          }
        }
      ],
      "senderType": "BOT",
      "messageType": "rag_answer",
      "content": "복지카드 신청은 안내 문서를 참고하고, 신청서는 바로 내려받아 작성할 수 있습니다.",
      "createdAt": "2026-03-24T10:00:02Z"
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
- 온보딩 제안 메시지인 경우 `suggestionId`가 포함될 수 있다.
- `senderType`은 `USER`, `BOT` 값을 사용한다.
- `messageType`은 아래 표준값을 사용한다.
  - `user_question`: 신입 사용자가 입력한 질문
  - `rag_answer`: 문서 기반으로 답변이 생성된 메시지
  - `no_result`: 질문 범위는 맞지만 근거 문서나 정보가 없어 답변하지 못한 메시지
  - `out_of_scope`: 서비스 범위를 벗어난 질문에 대한 안내 메시지
  - `suggestion`: 온보딩 가이드 기반 Buddy Nudge 카드 또는 제안 메시지
- 인증 오류와 토큰 만료 처리 방식은 **5-2. 인증 오류 및 토큰 만료 처리**를 따른다.
- `rag_answer` 메시지인 경우, 근거 문서는 `chat_message_documents`를 기준으로 조회한다.
- 근거 문서 ID 목록은 `documentIds` 배열로 반환하며, 이는 답변 메시지와 연결된 문서 ID 목록을 의미한다.
- 근거 문서 상세 정보는 `documents` 배열로 반환한다.
- `documents[].title`은 `documents.title` 값을 의미하며, 프론트엔드에서 근거 문서명 표시용으로 사용한다.
- `documents[].documentType = TEMPLATE`인 경우 `documents[].file` 객체를 포함한다.
- 프론트엔드는 `documents[].file.downloadUrl` 또는 `documents[].file.fileApiPath`를 통해 문서를 다운로드하거나 파일 응답으로 받을 수 있다.
- `documents[].documentType != TEMPLATE`인 경우 `documents[].file`은 `null`일 수 있다.
- `user_question`, `suggestion`, `no_result`, `out_of_scope` 메시지는 일반적으로 근거 문서를 포함하지 않으므로 `documentIds`와 `documents`는 빈 배열(`[]`)이다.


### 6-2. 질문 전송

사용자가 질문을 보내면 사용자 질문 메시지를 저장하고, 내부 AI 서버에 답변 생성을 요청한 뒤, 생성된 AI 답변 메시지를 저장하여 두 메시지를 함께 응답으로 반환한다.

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

| 필드 | 타입 | 필수 | 예시값 | 설명 | 상세 규칙 |
|------|------|------|--------|------|-----------|
| `content` | `String` | Y | `"복지카드는 어떻게 신청하나요?"` | 사용자가 입력한 질문 내용 | 길이: 1~500자 / 공백만 입력 불가 / 일반 문장 입력 가능 / 특수문자 허용 |

#### Response (201 Created)
```json
{
  "question": {
    "id": 201,
    "suggestionId": null,
    "documentIds": [],
    "documents": [],
    "senderType": "USER",
    "messageType": "user_question",
    "content": "복지카드 신청 양식은 어디서 받나요?",
    "createdAt": "2026-03-24T10:00:00Z"
  },
  "answer": {
    "id": 202,
    "suggestionId": null,
    "documentIds": [10, 11],
    "documents": [
      {
        "documentId": 10,
        "title": "복지카드 신청 안내",
        "documentType": "GUIDE",
        "file": null
      },
      {
        "documentId": 11,
        "title": "복지카드 신청서",
        "documentType": "TEMPLATE",
        "file": {
          "fileName": "welfare-card-application.docx",
          "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "downloadUrl": "/api/v1/documents/11/download",
          "fileApiPath": "/api/v1/documents/11/file",
          "directReturn": true
        }
      }
    ],
    "senderType": "BOT",
    "messageType": "rag_answer",
    "content": "복지카드 신청은 안내 문서를 참고하고, 신청서는 바로 내려받아 작성할 수 있습니다.",
    "createdAt": "2026-03-24T10:00:02Z"
  }
}
```

#### Response (201 Created, 답변 문서 없음 예시)

```json
{
  "question": {
    "id": 201,
    "suggestionId": null,
    "documentIds": [],
    "documents": [],
    "senderType": "USER",
    "messageType": "user_question",
    "content": "복지카드 신청 양식은 어디서 받나요?",
    "createdAt": "2026-03-24T10:00:00Z"
  },
  "answer": {
    "id": 202,
    "suggestionId": null,
    "documentIds": [],
    "documents": [],
    "senderType": "BOT",
    "messageType": "no_result",
    "content": "관련 안내 문서를 찾지 못했습니다.",
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
  "errors": [
    {
      "field": "content",
      "message": "질문 내용은 비어 있을 수 없습니다."
    }
  ],
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
- 백엔드는 질문 저장 후 생성된 질문 메시지의 `id`를 `questionId`로 사용한다.
- 백엔드는 질문 저장 후, 로그인한 사용자의 `companyCode`, 질문 메시지의 `id(questionId)`, 질문 `content`를 사용하여 `/internal/ai/answer`를 호출한다.
- 내부 AI 서버는 로그인한 사용자의 회사 문서와 공통 문서만을 대상으로 답변을 생성한다.
- 내부 AI 응답의 `messageType`은 `rag_answer`, `no_result`, `out_of_scope` 중 하나를 반환해야 한다.
- 백엔드는 내부 AI 응답의 `questionId`, `content`, `messageType`을 사용하여 답변 메시지를 `chat_messages`에 `sender_type = BOT`으로 저장한다.
- 내부 AI 응답에 근거 문서 목록(`document[].documentId`)이 포함된 경우, 백엔드는 답변 메시지 저장 후 `chat_message_documents`에 답변 메시지 ID와 문서 ID를 매핑하여 저장한다.
- 백엔드는 AI 응답의 `document[].documentId` 목록을 기준으로 `documents`를 조회한다.
- 답변 메시지 응답에는 근거 문서 ID 목록 `documentIds`와 근거 문서 상세 정보 `documents`를 포함한다.
- `documentIds`는 답변 메시지와 연결된 문서 ID 목록을 의미한다.
- `documents[].title`은 `documents.title` 값을 의미하며, 프론트엔드에서 근거 문서명 표시용으로 사용한다.
- 문서의 `document_type = TEMPLATE`인 경우, 파일 접근 정보는 `document_files`를 기준으로 조회하며 `documents[].file`에 포함한다.
- 프론트엔드는 `documents[].file.downloadUrl` 또는 `documents[].file.fileApiPath`를 통해 문서를 다운로드하거나 파일 응답으로 받을 수 있다.
- `document_type != TEMPLATE`인 경우, `documents[].file`은 일반적으로 포함하지 않거나 `null`로 반환한다.
- 실제 파일 데이터는 채팅 메시지 응답 본문(JSON)에 직접 포함하지 않고, 별도 파일 API를 통해 반환한다.
- `user_question`, `suggestion`, `no_result`, `out_of_scope` 메시지는 일반적으로 근거 문서를 포함하지 않으므로 `documents`는 빈 배열(`[]`)이다.
- 별도의 `isAnswered` 필드는 두지 않으며, 응답 유형은 `messageType` 값으로 해석한다.
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

### 6-4. 빠른 질문 목록 조회
현재 로그인한 사용자에게 노출할 빠른 질문 목록을 조회한다.

```http
GET /api/v1/chat/quick-questions
Authorization: Bearer {accessToken}
```

#### Response (200 OK)

```json
{
  "quickQuestions": [
    { "content": "연차는 어떻게 신청하나요?" },
    { "content": "급여일이 언제인가요?" },
    { "content": "건강검진은 어떻게 받나요?" }
  ]
}
```
#### 빈 결과 예시 (200 OK)
```json
{
  "quickQuestions": []
}
```

#### 설명

- 빠른 질문은 사용자가 자주 묻는 질문을 버튼 형태로 제공하기 위한 추천 질문 목록이다.
- 현재 MVP 기준으로 빠른 질문 목록은 공통으로 제공한다.
- 사용자가 빠른 질문을 클릭하면, 해당 `content` 값을 일반 질문과 동일하게 `POST /api/v1/chat/messages`로 전송한다.
- 빠른 질문 클릭 자체가 별도의 답변 생성 API를 호출하지는 않는다.
- 인증 오류와 토큰 만료 처리 방식은 **5-2. 인증 오류 및 토큰 만료 처리**를 따른다.

### 6-5. 채팅 화면 진입 로그 기록
사용자가 채팅 화면에 진입하면 `user_activity_logs`에 `SESSION_START` 이벤트를 기록한다.  
이때 `event_target = CHAT`으로 저장한다.  
단, 동일 사용자가 **30분 이내에 다시 채팅 화면에 진입한 경우** 중복 기록하지 않는다.

```http
POST /api/v1/chat/session-start
Authorization: Bearer {accessToken}
```

#### Response (201 Created)

```json
{
  "logged": true,
  "eventType": "SESSION_START",
  "eventTarget": "CHAT",
  "message": null,
  "createdAt": "2026-04-13T09:00:00Z"
}
```
#### Response (200 OK, 중복 기록 제외)
```json
{
  "logged": false,
  "eventType": "SESSION_START",
  "eventTarget": "CHAT",
  "message": "30분 이내 동일 사용자 채팅 진입 기록이 이미 존재합니다.",
  "createdAt": null
}
```

#### 설명

- 현재 로그인한 사용자 기준으로 동작한다.
- 채팅 화면 진입 시 `user_activity_logs`에 `event_type = SESSION_START`, `event_target = CHAT`으로 기록한다.
- 저장 항목에는 최소한 `user_id`, `event_type`, `event_target`, `created_at`이 포함된다.
- 동일 사용자가 최근 30분 이내에 이미 `event_target = CHAT`인 `SESSION_START` 이벤트를 기록한 경우 새로 저장하지 않는다.
- 프론트엔드는 채팅 화면 최초 진입 시 이 API를 1회 호출한다.
- 인증 오류와 토큰 만료 처리 방식은 **5-2. 인증 오류 및 토큰 만료 처리**를 따른다.
- 채팅 메시지 목록 조회 API(`GET /api/v1/chat/messages`) 호출만으로는 `SESSION_START` 로그를 자동 기록하지 않는다.
- 채팅 화면 진입 로그는 별도 API(`POST /api/v1/chat/session-start`) 호출로 기록한다.

---

## 7. 내부 AI 연동 규격

이 항목은 백엔드 서버와 생성형 AI 서버 간 내부 통신 규격이다.
프론트엔드가 직접 호출하지 않는다.

### 7-1. 연동 흐름

- 사용자가 질문 전송
- 백엔드가 `/api/v1/chat/messages` 요청을 받음
- 백엔드가 사용자 질문 메시지를 저장함
- 백엔드가 생성된 질문 메시지의 `id`를 `questionId`로 사용함
- 백엔드가 로그인한 사용자의 `companyCode`, `questionId`, 질문 `content`를 이용해 `/internal/ai/answer`를 호출
- AI 서버가 공통 문서와 해당 회사 문서를 기반으로 답변을 생성함
- AI 서버가 `questionId`, `document`, `messageType`, `content`를 반환함
- 백엔드가 반환값으로 답변 메시지를 저장하고, 근거 문서 목록은 `chat_message_documents`에 저장한 뒤 최종 응답을 반환함

### 7-2. 답변 생성 요청

```http
POST /internal/ai/answer
Content-Type: application/json
```

#### Request Body
```json
{
  "questionId": 201,
  "companyCode": "WB0001",
  "content": "복지카드는 어떻게 신청하나요?"
}
```

#### Request Field (Top-level)

| 필드            | 타입       | 필수 | 예시값                  | 설명                     | 상세 규칙                                          |
| ------------- | -------- | -- | -------------------- | ---------------------- | ---------------------------------------------- |
| `questionId`  | `Long`   | Y  | `201`                | 백엔드가 저장한 사용자 질문 메시지 ID | 양의 정수                                          |
| `companyCode` | `String` | Y  | `"WB0001"`           | 로그인한 사용자의 회사 코드        | 길이: 1~20자 / 허용 문자: 영문 대소문자 + 숫자 / 특수문자·공백 불가   |
| `content`     | `String` | Y  | `"복지카드는 어떻게 신청하나요?"` | 사용자가 입력한 질문 내용         | 길이: 1~500자 / 공백만 입력 불가 / 일반 문장 입력 가능 / 특수문자 허용 |


#### Response (200 OK)

```json
{
  "questionId": 201,
  "documents": [
    { "documentId": 1 },
    { "documentId": 2 },
    { "documentId": 3 }
  ],
  "messageType": "rag_answer",
  "content": "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다."
}
```

#### Response (200 OK, 문서 없음 예시)

```json
{
  "questionId": 202,
  "document": [],
  "messageType": "no_result",
  "content": "관련 안내 문서를 찾지 못했습니다."
}
```

#### 설명

- 백엔드는 질문 메시지를 먼저 저장한 뒤 내부 AI 서버를 호출한다.
- 내부 AI 요청에는 질문 저장 결과 전체 객체를 전달하지 않고, 답변 생성에 필요한 최소 정보인 `questionId`, `companyCode`, `content`만 전달한다.
- AI 서버는 `companyCode`를 기준으로 해당 회사 문서와 공통 문서만 조회 대상으로 사용한다.
- AI 서버는 질문 내용에 대해 답변 문자열과 답변 유형을 생성하여 반환한다.
- `messageType`은 아래 값 중 하나를 사용한다.
  - `rag_answer`: 문서 기반 답변 생성
  - `no_result`: 질문 범위는 맞지만 문서/정보 부족으로 답변 불가
  - `out_of_scope`: 서비스 범위를 벗어난 질문
- `suggestion`은 온보딩 가이드 기반 메시지 유형이므로 내부 AI 답변 응답값으로 사용하지 않는다.
- `document`는 답변 생성의 근거로 사용된 문서 목록이다.
- `document[].documentId`는 `documents.id`를 의미한다.
- 백엔드는 AI 응답의 `document[].documentId` 목록을 답변 메시지와 연결하여 `chat_message_documents`에 저장한다.
- `rag_answer`인 경우 근거 문서 목록이 포함될 수 있다.
- `no_result`, `out_of_scope`인 경우 `document`는 빈 배열(`[]`)로 반환한다.

---

## 8. Documents

이 항목은 문서 업로드, 조회, 다운로드, 삭제, 백업 재시도 등 스토리지 문서 관리 기능에 대한 API를 설명한다.
이 섹션의 엔드포인트는 현재 Swagger UI 기준으로 확인된 항목을 정리한 것이며, 요청/응답의 상세 스키마는 실제 Swagger UI 정의를 우선 기준으로 한다.

### 8-1. 문서 업로드

```http
POST /api/v1/documents/upload
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

### 설명
- 문서를 업로드한다.
- 업로드된 문서는 `documents`, `document_files`를 기준으로 저장 및 관리한다.
- 파일 저장 후 백업 스토리지 연동 정책에 따라 백업 작업이 수행될 수 있다.

### 8-2. 문서 목록 조회

```http
GET /api/v1/documents
Authorization: Bearer {accessToken}
```
### 설명
- 현재 사용자가 접근 가능한 문서 목록을 조회한다.
- 회사 문서와 공통 문서 기준으로 조회될 수 있다.
- 상세 검색 조건, 정렬, 필터링 여부는 Swagger UI의 실제 파라미터 정의를 따른다.

### 8-3. 문서 상세 조회
```http
GET /api/v1/documents/{documentId}
Authorization: Bearer {accessToken}
```

| 필드           | 타입     | 필수 | 설명        |
| ------------ | ------ | -- | --------- |
| `documentId` | `Long` | Y  | 조회할 문서 ID |

### 설명
- 특정 문서의 상세 정보를 조회한다.
- 문서 기본 정보와 파일 메타데이터를 함께 포함한다.

### 8-4. 문서 파일 직접 다운로드
```http
GET /api/v1/documents/{documentId}/file
Authorization: Bearer {accessToken}
```

| 필드           | 타입     | 필수 | 설명             |
| ------------ | ------ | -- | -------------- |
| `documentId` | `Long` | Y  | 파일 반환 대상 문서 ID |

#### 동작 규칙
- `documents.id = {documentId}`인 문서를 조회한다.
- 해당 문서가 현재 로그인한 사용자의 회사 문서이거나 공통 문서인 경우만 접근 가능하다.
- `document_type = TEMPLATE`인 경우 프론트엔드에 파일 자체를 반환한다.
- 파일 메타데이터 및 실제 저장 위치는 `document_files`를 기준으로 조회한다.
- 파일이 존재하면 `Content-Type`, `Content-Disposition` 헤더와 함께 바이너리 파일을 반환한다.
- `document_type != TEMPLATE`인 경우에는 정책에 따라 `400 Bad Request` 또는 `404 Not Found`를 반환한다.

#### Response (200 OK)
```http
Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Content-Disposition: attachment; filename="welfare-card-application.docx"
```

#### 설명
- Response Body: 파일 바이너리

#### Error Response (404 Not Found)
```json
{
  "timestamp": "2026-04-14T15:30:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "errors": [
    {
      "field": "documentId",
      "message": "해당 문서를 찾을 수 없습니다."
    }
  ],
  "path": "/api/v1/documents/11/file"
}
```

#### Error Response (400 Bad Request)
```json
{
  "timestamp": "2026-04-14T15:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "errors": [
    {
      "field": "documentType",
      "message": "파일 직접 반환은 TEMPLATE 문서에만 허용됩니다."
    }
  ],
  "path": "/api/v1/documents/11/file"
}
```

### 설명
- 로컬 개발 환경에서 문서 파일을 직접 다운로드한다.
- 운영 환경에서는 직접 파일 반환 대신 다운로드 URL 발급 방식을 사용한다.
- `GET /api/v1/documents/{documentId}/file`는 파일 바이너리를 직접 반환한다.

### 8-5. 다운로드 URL 발급
```http
GET /api/v1/documents/{documentId}/download
Authorization: Bearer {accessToken}
```

### 설명
- 문서 파일 다운로드를 위한 URL을 발급한다.
- 실제 파일은 스토리지 경로를 통해 제공될 수 있다.
- `GET /api/v1/documents/{documentId}/download`는 다운로드에 사용할 URL 또는 접근 경로를 반환한다.

### 8-6. 문서 삭제 전 검증
```http
GET /api/v1/documents/{documentId}/delete-check
Authorization: Bearer {accessToken}
```

### 설명
- 특정 문서를 삭제하기 전에 삭제 가능 여부를 검증한다.
- 연관 데이터, 백업 상태, 권한 조건 등을 확인한다.

### 8-7. 문서 삭제
```http
DELETE /api/v1/documents/{documentId}
Authorization: Bearer {accessToken}
```

### 설명
- 특정 문서를 삭제한다.
- 실제 삭제 방식은 물리 삭제 또는 논리 삭제 정책을 따른다.
- 파일 및 백업 데이터 처리 방식은 서버 정책에 따른다.

### 8-8. 문서 전체 삭제 전 검증
```http
GET /api/v1/documents/delete-check
Authorization: Bearer {accessToken}
```

### 설명
- 전체 문서 삭제 전에 삭제 가능 여부를 검증한다.
- 삭제 대상 수, 삭제 불가 사유, 확인 필요 항목 등을 반환한다.

### 8-9. 문서 전체 삭제
```http
DELETE /api/v1/documents
Authorization: Bearer {accessToken}
```

### 설명
- 전체 문서를 삭제한다.
- confirm 필요 정책이 적용된다.
- 실제 요청 규격은 Swagger UI 정의를 따른다.

### 8-10. 문서 선택 삭제 전 검증
```http
POST /api/v1/documents/bulk-delete/delete-check
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### 설명
- 선택한 문서들을 삭제하기 전에 삭제 가능 여부를 검증한다.
- 삭제 대상 목록 기준으로 검증 결과를 반환한다.

### 8-11. 문서 선택 삭제
```http
POST /api/v1/documents/bulk-delete
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### 설명
- 선택한 문서들을 일괄 삭제한다.
- confirm 필요 정책이 적용될 수 있다.
- 실제 요청 바디 구조는 Swagger UI 정의를 따른다.

### 8-12. 백업 재시도
```http
POST /api/v1/documents/{documentId}/backup/retry
Authorization: Bearer {accessToken}
```

### 설명
- 특정 문서의 백업 작업을 재시도한다.
- `document_files`, `document_backup_jobs` 기준으로 백업 상태를 갱신하거나 새 작업을 생성한다.

---

## 9. 변경 이력

- **v1.0.0 (2026-03-10)**:
  - 초기 버전 작성
- **v1.1.0 (2026-03-17)**:
  - 멀티 테넌시 구조 적용, 로그인 API 변경, 회사 식별 정보 응답 구조 추가, 회사 관리 API 추가, 데이터 격리 설명 추가, JWT 페이로드 구조 업데이트, 비밀번호 제거
- **v1.2.0 (2026-03-17)**:
  - MVP 기준 구현 범위만 남기도록 문서 범위 재정리, 미구현·변경 가능성이 큰 API를 별도 관리 대상으로 분리, 커스텀 에러코드·Rate Limiting·페이지네이션 공통 정책을 계획 문서로 이동
- **v1.3.0 (2026-03-24)**:
  - API 명세 단순화, `companyId` 기준으로 응답 예시 수정, `auth/refresh`·`users/me`·체크리스트 API 제거, `conversationId` 제거, `MyBuddy` 기준으로 채팅/온보딩 API 재정리, 내부 AI 연동 규격 최소화
- **v1.4.0 (2026-03-26)**:
  - 로그인 기준과 인증 흐름을 수정된 ERD에 맞게 정리, `companyCode` 반영, 토큰 만료 처리, 프론트 개발 서버 주소 추가, 백엔드 서버와 생성형 AI 서버 간 연동 흐름 추가
- **v1.5.0 (2026-03-26)**:
  - 빠른 질문 목록 조회 API 추가, 문서 양식 및 정합성 정리
- **v1.6.0 (2026-04-02)**:
  - `company_id` 제거, 로그인/내부 AI 연동 관련 요청·응답 예시 및 동작 규칙 수정, Swagger(OpenAPI) 기반 API 문서 확인 경로 추가
- **v1.7.0 (2026-04-03)**:
  - 로그인 API 입력값 검증 적용에 따라 `400 Bad Request` 의미를 구체화, `POST /api/v1/auth/login`의 `200/400/401` 상태 코드 기준 정리, 예외 응답 형식 변경
- **v1.7.1 (2026-04-06)**:
  - `company_code` 예시값 수정
- **v1.7.2 (2026-04-08)**:
  - 내부 AI 요청을 `questionId`, `companyCode`, `content` 기반 구조로 단순화, 내부 AI 응답을 `questionId`, `messageType`, `content` 중심으로 수정
- **v1.7.3 (2026-04-13)**:
  - 채팅 메시지 목록 조회 응답 형식 수정
- **v1.7.4 (2026-04-13)**:
  - `user_activity_logs`의 `SESSION_START` 이벤트 기록 규칙 정리, 로그인 성공 시 `event_target = LOGIN` 로그 기록 규칙 추가, `chat_message_documents` 기반 근거 문서 다중 연결 구조 반영, 채팅 메시지 응답 예시에 `documentIds` 추가, 내부 AI 응답의 근거 문서 저장 규칙 보강
  - 스토리지 문서 API 엔드포인트 목록 추가, `document_files` 및 `document_backup_jobs` 기반 문서 관리/백업 기능 설명 보강