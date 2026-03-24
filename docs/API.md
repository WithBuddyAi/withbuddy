# WithBuddy API 명세서

> WithBuddy MVP 기준 REST API 문서

**버전**: 1.3.0  
**최종 업데이트**: 2026-03-24

---

## 1. 문서 범위

이 문서는 **MVP 개발 범위에서 현재 구현했거나 즉시 구현 대상으로 확정된 API만** 다룹니다.

### 포함 범위:
- 로그인
- 채팅 메시지 목록 조회
- 질문 전송 및 답변 생성
- 온보딩 제안 조회
- 내부 AI 답변 생성 요청 규격

### 제외 범위:
- 토큰 재발급
- 로그아웃
- 내 정보 조회
- 관리자 기능
- 문서 등록/수정/삭제
- 체크리스트
- 커스텀 에러코드
- 기타 미구현 기능

---

## 2. ERD 기준 정리

현재 API 설계는 아래 ERD를 기준으로 한다.

- companies
- users
- documents
- onboarding_suggestions
- chat_messages

---

## 3. API 개요

### Base URL

```text
Development: http://localhost:8080
Production: TBD
```

### Prefix

모든 API는 아래 prefix를 사용합니다.

```text
/api/v1
```

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
성공 응답은 API 목적에 맞는 JSON 데이터를 반환한다.

```json
{
  "id": 1,
  "companyId": 1,
  "companyName": "테크 주식회사",
  "employeeNumber": "20260001",
  "name": "김지원",
  "hireDate": "2026-03-01"
}
```

### 에러 응답
에러 발생 시 아래 형식으로 반환한다.

```json
{
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "잘못된 요청입니다",
  "path": "/api/v1/auth/login"
}
```

### HTTP 상태 코드

- `200 OK`: 성공
- `201 Created`: 리소스 생성 성공
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `404 Not Found`: 리소스 없음
- `500 Internal Server Error`: 서버 오류

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
  "companyName": "테크 주식회사",
  "employeeNumber": "20260001",
  "name": "김지원"
}
```

#### 필드 설명

- `companyName`: 회사명
- `employeeNumber`: 사번
- `name`: 사용자 이름

#### Response (200 OK)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "companyId": 1,
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
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "회사명, 사번 또는 이름이 올바르지 않습니다",
  "path": "/api/v1/auth/login"
}
```

---

## 6. MyBuddy

### 6-1. 채팅 메시지 목록 조회
현재 로그인한 사용자의 채팅 메시지를 시간순으로 조회한다.

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
      "messageType": "QUESTION",
      "content": "복지카드는 어떻게 신청하나요?",
      "documentId": null,
      "suggestionId": null,
      "createdAt": "2026-03-24T10:00:00Z"
    },
    {
      "id": 102,
      "senderType": "BOT",
      "messageType": "ANSWER",
      "content": "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.",
      "documentId": 10,
      "suggestionId": null,
      "createdAt": "2026-03-24T10:00:02Z"
    },
    {
      "id": 103,
      "senderType": "BOT",
      "messageType": "SUGGESTION",
      "content": "입사 1일차 안내를 먼저 확인해보세요.",
      "documentId": null,
      "suggestionId": 3,
      "createdAt": "2026-03-24T10:10:00Z"
    }
  ]
}
```

### 6-2. 질문 전송
- 사용자가 질문을 보내면 사용자 질문 메시지를 저장하고, AI 답변 메시지를 생성하여 저장한 뒤, 두 메시지를 함께 응답으로 반환한다.
- 답변 생성 시 검색 대상은 로그인한 사용자의 회사 문서와 공통 문서(`company_id = null`)이다.

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

#### Response (201 Created)
```json
{
  "question": {
    "id": 201,
    "senderType": "USER",
    "messageType": "QUESTION",
    "content": "복지카드는 어떻게 신청하나요?",
    "documentId": null,
    "suggestionId": null,
    "createdAt": "2026-03-24T10:00:00Z"
  },
  "answer": {
    "id": 202,
    "senderType": "BOT",
    "messageType": "ANSWER",
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
  "timestamp": "2026-03-24T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "질문 내용은 비어 있을 수 없습니다",
  "path": "/api/v1/chat/messages"
}
```

#### 값 설명

- `senderType`
    - `USER` : 사용자
    - `BOT` : 챗봇

- `messageType`
    - `QUESTION` : 사용자 질문
    - `ANSWER` : 챗봇 답변
    - `SUGGESTION` : 온보딩 제안 메시지

#### 동작 규칙
- 질문 메시지는 `chat_messages`에 `sender_type = USER`, `message_type = QUESTION`으로 저장한다.
- 답변 메시지는 `chat_messages`에 `sender_type = BOT`, `message_type = ANSWER`로 저장한다.
- 답변이 특정 문서를 근거로 생성되었다면 `document_id`를 저장한다.
- 문서 근거가 없으면 `document_id`는 `null`이다.


### 6-3. 온보딩 제안 조회
현재 로그인한 사용자의 `hireDate`를 기준으로 노출 대상 온보딩 제안을 조회한다.

```http
GET /api/v1/onboarding-suggestions/me
Authorization: Bearer {accessToken}
```
#### 동작 기준
- `dayOffset = 오늘 날짜 - hireDate`
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

---

## 7. 내부 AI 연동 규격

이 항목은 백엔드 서버와 생성형 AI 서버 간 내부 통신 규격이다.
프론트엔드가 직접 호출하지 않는다.

### 7-1. 답변 생성 요청

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
    "name": "김지원",
    "hireDate": "2026-03-01"
  },
  "question": "복지카드는 어떻게 신청하나요?",
  "messageHistory": [
    {
      "senderType": "USER",
      "messageType": "QUESTION",
      "content": "출근 기록은 어디서 확인하나요?"
    },
    {
      "senderType": "BOT",
      "messageType": "ANSWER",
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

#### Response (200 OK)
```json
{
  "answer": "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.",
  "sourceDocumentId": 10
}
```

#### 설명

- 백엔드는 현재 로그인한 사용자 기준으로 회사 문서와 공통 문서만 필터링한 뒤 AI 서버에 전달한다.
- AI 서버는 답변 문자열과 대표 근거 문서 ID 1개를 반환한다.
- 반환된 `sourceDocumentId`는 저장되는 답변 메시지의 `document_id`에 기록한다.
- 특정 근거 문서가 없으면 `sourceDocumentId`는 `null`이다.

---

## 8. 변경 이력

- **v1.0.0 (2026-03-10)**: 초기 버전 작성
- **v1.1.0 (2026-03-17)**:
  - 멀티 테넌시 구조 적용, 로그인 API 변경, 회사 식별 정보 응답 구조 추가, 회사 관리 API 추가, 데이터 격리 설명 추가, JWT 페이로드 구조 업데이트, 비밀번호 제거
- **v1.2.0 (2026-03-17)**:
  - MVP 기준 구현 범위만 남기도록 문서 범위 재정리, 미구현·변경 가능성이 큰 API를 별도 관리 대상으로 분리, 커스텀 에러코드·Rate Limiting·페이지네이션 공통 정책을 계획 문서로 이동
- **v1.3.0 (2026-03-24)**:
  - API 명세 단순화, `companyId` 기준으로 응답 예시 수정, `auth/refresh`·`users/me`·체크리스트 API 제거, `conversationId` 제거, `MyBuddy` 기준으로 채팅/온보딩 API 재정리, 내부 AI 연동 규격 최소화