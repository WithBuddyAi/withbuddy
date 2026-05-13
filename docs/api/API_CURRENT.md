# WithBuddy API 명세서

> WithBuddy MVP 기준 REST API 문서
>
**버전**: 1.9.4
**최종 업데이트**: 2026-05-12

---

## 1. 문서 범위

이 문서는 **MVP 개발 범위에서 현재 구현했거나 즉시 구현 대상으로 확정된 API만** 다룬다.

### 포함 범위
- 로그인
- 로그아웃
- 관리자 계정 페이지 API
- 관리자 계정 페이지 내 신입 계정 생성
- 채팅 메시지 목록 조회
- 질문 전송 및 AI 답변 스트리밍
- 온보딩 제안 조회
- 빠른 질문 목록 조회
- 내부 AI 스트리밍 연동 규격
- 세션 만료/무효화 처리 규격
- 스토리지 문서 API
- 관리자 지표 집계 API

### 제외 범위
- 내 정보 조회
- 관리자 화면 UI
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
Production:  https://api-wb.itsdev.kr
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

백엔드 공개 API는 `/api/v1` prefix를 사용한다.
AI 서버 연동은 별도 AI 서버 base URL을 사용하며, 현재 스트리밍 endpoint는 `/chat/stream`이다.

### 공개 API 경로 그룹

| 경로 | 용도 | 호출 권한 |
|---|---|---|
| `/api/v1/auth/*` | 로그인, 로그아웃 및 인증 | 로그인: 비로그인 / 로그아웃: 인증 사용자 |
| `/api/v1/chat/*` | MyBuddy 채팅 및 빠른 질문 | `USER`, `SERVICE_ADMIN` |
| `/api/v1/onboarding-suggestions/*` | 온보딩 제안 노출 처리 | `USER`, `SERVICE_ADMIN` |
| `/api/v1/documents/upload` | 문서 업로드 | `ADMIN` |
| `/api/v1/documents` | 문서 목록 조회·전체 삭제 등 문서 관리 | `ADMIN` |
| `/api/v1/documents/{documentId}` | 문서 상세 조회·삭제 등 문서 관리 | `ADMIN` |
| `/api/v1/chat/documents/{documentId}/download` | 채팅 답변으로 수신한 TEMPLATE 문서만 다운로드 URL 발급 | `USER`, `SERVICE_ADMIN`, `ADMIN` |
| `/api/v1/documents/{documentId}/download` | 회사/공통 TEMPLATE 문서 ID 기준 다운로드 URL 발급 | `USER`, `SERVICE_ADMIN`, `ADMIN` |
| `/api/v1/documents/{documentId}/file` | TEMPLATE 문서 파일 직접 다운로드 | `USER`, `SERVICE_ADMIN`, `ADMIN` |
| `/api/v1/admin/users` | 고객사 관리자 계정 페이지의 신입 계정 생성 | `ADMIN` |
| `/api/v1/admin/metrics/*` | 제품 내부 관리자용 지표 조회 | `SERVICE_ADMIN` |

`/api/v1/admin` 하위 경로는 관리자 성격의 API를 모아둔다.

- `ADMIN`은 고객사 관리자 계정으로, 관리자 계정 페이지에서 신입 계정 생성 및 문서 관리 API를 호출할 수 있다.
- `SERVICE_ADMIN`은 제품 내부 지표 조회용 계정이다. MVP 기준 일반 `USER`와 동일하게 MyBuddy 기능을 사용할 수 있으며, 추가로 Swagger, Postman, 내부 운영 도구 등을 통해 관리자 지표 API(`/api/v1/admin/metrics/*`)를 호출할 수 있다.
- `SERVICE_ADMIN`은 고객사 관리자 계정 페이지 API(`/api/v1/admin/users`)와 문서 관리 API(`/api/v1/documents/*`)를 호출하지 않는다.

### Frontend 권장 라우트

프론트엔드 화면 라우트는 API 경로와 별도로 관리한다. MVP 기준 권장 라우트는 아래와 같다.

| 화면 | Frontend route | Backend API |
|---|---|---|
| MyBuddy 채팅 화면 | `/mybuddy` | `/api/v1/chat/*` |
| 관리자 계정 페이지 | `/admin` | `/api/v1/admin/users` |
| 신입 계정 생성 화면 | `/admin/users/new` | `POST /api/v1/admin/users` |

제품 내부 관리자(`SERVICE_ADMIN`) 계정의 진입 화면은 일반 사용자와 동일하게 MyBuddy 채팅 화면(`/mybuddy`)으로 분기한다.
`SERVICE_ADMIN`은 MVP 기준 별도 관리자 지표 화면을 사용하지 않으며, Swagger, Postman, 내부 운영 도구 등을 통해 관리자 지표 API를 직접 호출한다.
프론트엔드는 로그인 성공 응답의 `user.role` 값을 기준으로 최초 진입 화면을 분기할 수 있다.

### 데이터 범위

모든 데이터 조회 및 저장은 로그인한 사용자의 회사 기준으로 처리한다.  
문서 기반 Q&A는 로그인한 사용자의 회사 문서와 공통 문서(`company_code = null`)를 함께 대상으로 처리한다.
단, 제품 내부 관리자(`SERVICE_ADMIN`) 전용 관리자 지표 API는 `companyCode` 파라미터 기준으로 특정 회사를 조회하거나, 파라미터 생략 시 회사별 전체 집계 결과를 반환할 수 있다.

### 공통 헤더

```http
Content-Type: application/json
Authorization: Bearer {accessToken}
```

로그인 API는 `Authorization` 헤더가 필요하지 않다.
로그아웃 API는 현재 세션을 종료해야 하므로 `Authorization: Bearer {accessToken}` 헤더가 필요하다.

### Swagger (OpenAPI)

본 프로젝트의 REST API는 Swagger(OpenAPI) 기반으로 확인할 수 있다.  
실행 중인 백엔드 서버에서 Swagger UI를 통해 요청/응답 스키마와 엔드포인트를 확인한다.

```text
Local Swagger UI: http://localhost:8080/swagger-ui/index.html
OpenAPI Docs:     http://localhost:8080/v3/api-docs
```

AI Server Swagger UI는 아래 경로를 기준으로 확인한다.

```text
AI Swagger UI: https://ai.itsdev.kr/docs
```

- Swagger UI는 현재 구현된 API 기준으로 동작한다.
- 본 문서는 MVP 범위, 정책, 동작 규칙, 내부 연동 기준을 함께 설명하기 위한 문서다.
- 상세 요청/응답 스키마 및 테스트는 Swagger UI를 우선 확인한다.

---

## 4. 표준 응답 형식

### 성공 응답
성공 응답은 각 API 목적에 맞는 JSON 데이터를 반환한다.

단, `POST /api/v1/chat/messages/stream`은 SSE 스트리밍 API이므로 `text/event-stream` 형식으로 이벤트를 순차 반환한다.

### 에러 응답

```json
{
  "timestamp": "2026-03-25T10:30:00",
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
- `401 Unauthorized`: 인증 실패, 세션 만료 또는 세션 무효화
- `403 Forbidden`: 요청 권한 없음
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 중복 리소스 또는 제약조건 충돌
- `500 Internal Server Error`: 서버 오류
- `503 Service Unavailable`: 세션 저장소 등 외부 의존성 일시 장애
- `504 Gateway Timeout` : AI 서버 응답 시간 초과

#### 로그인 API (`POST /api/v1/auth/login`) 상태 코드

- `200 OK`: 로그인 성공
- `400 Bad Request`: 요청값 검증 실패
- `401 Unauthorized`: 로그인 실패(회사코드/사번/이름 불일치)

#### 로그아웃 API (`POST /api/v1/auth/logout`) 상태 코드

- `204 No Content`: 로그아웃 성공
- `401 Unauthorized`: 인증 실패, 세션 만료 또는 세션 무효화
- `503 Service Unavailable`: 세션 저장소 연결 실패 또는 조회 지연

#### 신입 계정 생성 API (`POST /api/v1/admin/users`) 상태 코드

- `201 Created`: 계정 생성 성공
- `400 Bad Request`: 요청값 검증 실패
- `401 Unauthorized`: 인증 실패, 세션 만료 또는 세션 무효화
- `403 Forbidden`: 관리자 권한 없음
- `409 Conflict`: 동일 회사 내 중복 사원번호

### 공통 에러 코드
- `BAD_REQUEST`: 잘못된 요청
- `UNAUTHORIZED`: 인증 실패
- `SESSION_EXPIRED`: 로그인 세션 또는 액세스 토큰 만료
- `SESSION_REVOKED`: 중복 로그인
- `TOKEN_MISSING`: 인증 토큰 누락
- `INVALID_TOKEN`: 유효하지 않은 인증 정보
- `SESSION_STORE_UNAVAILABLE`: 세션 저장소 연결 실패 또는 조회 지연
- `USER_NOT_FOUND`: 사용자 정보를 찾을 수 없음
- `ACCESS_DENIED`: 요청 권한 없음
- `NOT_FOUND`: 리소스 없음
- `DUPLICATE_EMPLOYEE_NUMBER`: 동일 회사 내 중복 사원번호
- `INTERNAL_SERVER_ERROR`: 서버 내부 오류
- `AI_TIMEOUT`: AI 답변 생성 시간 초과
- `AI_STREAM_FAILED`: SSE 스트리밍 중 AI 답변 생성 실패
- `FORBIDDEN`: 채팅에서 수신하지 않은 문서 다운로드 요청
- `FILE_001`: 업로드 파일이 비어 있거나 파일 크기 제한을 초과함
- `FILE_002`: 지원하지 않는 파일 형식
- `FILE_003`: 파일 읽기 또는 스토리지 처리 실패
- `RESOURCE_004`: 다른 회사 문서 접근

---

## 5. 인증 (Authentication)

로그인 성공 시 `accessToken`을 발급한다.  
인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 사용한다.

### 세션 정책

| 항목 | 값 | 비고 |
|---|---|---|
| JWT 유효기간 | 7일 (`604800000ms`) | `jwt.access-expiration` 기본값 |
| Redis `SESSION_TOKEN` TTL | 9시간 | 마지막 인증 성공 시 `session:user:{userId}` TTL을 다시 9시간으로 갱신 |
| 단일 기기 세션 | 지원 | 동일 사용자가 다시 로그인하면 기존 활성 세션을 새 토큰으로 대체 |
| Redis 세션 키 | `session:user:{userId}`, `session:token:{token}` | 로그인 시 둘 다 저장, 로그아웃/재로그인 시 정리 |
| 사용자 프로필 캐시 | `user:profile:{userId}` | 로그인 성공 시 30분 TTL로 저장, 로그아웃 시 삭제 |

#### JWT 및 Redis 세션 동작 규칙

- `accessToken`은 JWT이며 payload에는 `sub`(userId), `employeeNumber`, `name`, `companyCode`, `companyName`, `hireDate`, `iat`, `exp`가 포함된다.
- 인증 성공 조건은 JWT 서명과 만료 시간이 유효하고, Redis의 `session:user:{userId}` 값이 현재 요청 토큰과 일치하는 것이다.
- JWT 자체가 7일 동안 유효하더라도 Redis 세션 TTL 9시간이 만료되면 인증에 실패한다. 이 경우 다시 로그인해야 한다.
- 동일 사용자가 새로 로그인하면 기존 `session:token:{oldToken}`을 삭제하고 `session:user:{userId}`를 새 토큰으로 갱신한다.
- 기존 토큰으로 인증 API를 호출하면 Redis 활성 세션 기준과 일치하지 않으므로 `SESSION_REVOKED`를 반환한다.
- Redis 세션 저장소에 연결할 수 없으면 인증 처리 중 `503 Service Unavailable`, `SESSION_STORE_UNAVAILABLE`을 반환할 수 있다.

### 5-1. 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json
```

#### Request Body

```json
{
  "companyCode": "WB0001",
  "employeeNumber": "20260001",
  "name": "김지원"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 예시값          | 설명 | 상세 규칙                                           |
|------|------|------|--------------|------|-------------------------------------------------|
| `companyCode` | `String` | Y | `"WB0001"`   | 회사 식별 코드 | 길이: 4~20자 / 허용 문자: 영문 대소문자 + 숫자 / 특수문자·공백 불가    |
| `employeeNumber` | `String` | Y | `"20260001"` | 사용자 사번 | 길이: 4~20자 / 허용 문자: 영문 대소문자 + 숫자 / 특수문자·공백 불가    |
| `name` | `String` | Y | `"김지원"`      | 사용자 이름 | 길이: 1~20자 / 허용 문자: 한글 + 영문 대소문자 / 특수문자·공백·숫자 불가 |

#### 동작 규칙
- 사용자는 로그인 시 회사코드, 사번, 이름을 입력한다.
- 서버는 입력된 `companyCode`로 `companies`를 조회한다.
- 서버는 조회된 `company_code`와 사용자 이름, 사번을 기준으로 `users`에서 사용자를 확인한다.
- 일치하는 사용자가 존재하면 로그인에 성공하고 `accessToken`을 발급한다.
- 로그인 성공 응답의 `user.role`에는 로그인한 사용자의 역할을 반환한다.
- 프론트엔드는 `user.role` 값을 기준으로 `USER`와 `SERVICE_ADMIN`은 MyBuddy 화면으로, `ADMIN`은 관리자 계정 페이지로 분기할 수 있다.
- 권한 검증의 최종 기준은 프론트엔드 판단이 아니라 백엔드의 인증 토큰 검증 및 `users.role` 확인 결과다.
- 동일 사용자가 다시 로그인하면 서버는 기존 활성 세션을 무효화하고 새 `accessToken`을 기준으로 세션을 갱신할 수 있다.
- 이때 기존 `accessToken`으로 인증이 필요한 API를 호출하면 `401 Unauthorized`와 `SESSION_REVOKED` 코드를 반환한다.
- 로그인에 성공하면 `user_activity_logs`에 `event_type = SESSION_START`, `event_target = LOGIN` 로그를 기록한다.
- 로그인 성공 시의 `SESSION_START` 로그는 재로그인 시점 추적 용도로 사용한다.

#### Response (200 OK)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "companyCode": "WB0001",
    "companyName": "테크 주식회사",
    "employeeNumber": "20260001",
    "name": "김지원",
    "role": "USER",
    "hireDate": "2026-03-01"
  }
}
```

#### Response Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `accessToken` | String | Y | 인증이 필요한 API 호출 시 사용할 액세스 토큰 |
| `user` | Object | Y | 로그인한 사용자 정보 |
| `user.id` | Number | Y | 사용자 ID |
| `user.companyCode` | String | Y | 사용자 소속 회사 코드 |
| `user.companyName` | String | Y | 사용자 소속 회사명 |
| `user.employeeNumber` | String | Y | 사용자 사번 |
| `user.name` | String | Y | 사용자 이름 |
| `user.role` | String | Y | 사용자 역할. `USER`, `ADMIN`, `SERVICE_ADMIN` 중 하나 |
| `user.hireDate` | String | Y | 사용자 입사일 |

#### 프론트엔드 role 분기 기준

| `user.role` | 설명 | 권장 진입 화면 또는 처리 |
|---|---|---|
| `USER` | 일반 신입 사용자 | MyBuddy 채팅 화면(`/mybuddy`) |
| `ADMIN` | 고객사 관리자. 신입 계정 생성 등 고객사 관리자 페이지 접근 가능 | 관리자 계정 페이지(`/admin`) |
| `SERVICE_ADMIN` | 제품 내부 지표 조회용 계정. 일반 사용자와 동일하게 MyBuddy 기능 사용 가능 | MyBuddy 채팅 화면(`/mybuddy`). 지표 조회는 별도 프론트 화면 없이 Swagger, Postman, 내부 운영 도구 등을 통해 API 직접 호출 |

프론트엔드는 로그인 성공 직후 `user.role`을 저장해 화면 라우팅에 사용할 수 있다.  
다만 API 권한 검증은 항상 백엔드에서 수행한다.

#### Error Response (400 Bad Request)

```json
{
  "timestamp": "2026-04-03T10:30:00",
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
  "timestamp": "2026-03-25T10:30:00",
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

### 5-2. 로그아웃

현재 로그인 세션을 종료하고 Redis에 저장된 활성 세션 및 사용자 프로필 캐시를 삭제한다.

```http
POST /api/v1/auth/logout
Host: api-wb.itsdev.kr
Authorization: Bearer {accessToken}
```

#### Request Header

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` | Y | `Bearer {accessToken}` |

#### Request Body

없음

#### Response (204 No Content)

```http
HTTP/1.1 204 No Content
```

#### 동작 규칙

- 서버는 `Authorization` 헤더에서 Bearer 토큰을 추출한다.
- `JwtAuthenticationFilter`가 JWT 서명, JWT 만료 시간, Redis 활성 세션을 검증한다.
- 로그아웃 요청도 현재 구현 기준으로 Redis 활성 세션 검증을 통과해야 한다.
- 인증에 성공하면 `AuthService.logout(userId)`를 실행한다.
- 로그아웃은 아래 Redis 키를 삭제한다.

| Redis 키 | 설명 | 처리 |
|---|---|---|
| `session:user:{userId}` | userId → 현재 활성 accessToken 매핑 | 즉시 삭제 |
| `session:token:{token}` | accessToken → userId 매핑 | 현재 활성 토큰이 있으면 즉시 삭제 |
| `user:profile:{userId}` | 로그인 사용자 프로필 캐시 | 즉시 삭제 |
| `sse:session:{userId}` | SSE 연결 세션 | 로그아웃에서 직접 삭제하지 않음. TTL 기준 자연 만료 |
| `conversation:{sessionId}` | 대화 히스토리 캐시 | 로그아웃에서 직접 삭제하지 않음. TTL 기준 자연 만료 |

- 로그아웃 성공 후 같은 `accessToken`으로 인증 API를 호출하면 Redis 활성 세션이 없으므로 `401 Unauthorized`, `SESSION_EXPIRED`를 반환한다.
- 동일 사용자가 다른 기기 또는 브라우저에서 다시 로그인해 기존 토큰이 대체된 경우, 기존 토큰으로 로그아웃을 호출하면 `SESSION_REVOKED`를 반환한다.
- 이미 로그아웃되어 Redis 활성 세션이 없는 토큰으로 다시 로그아웃을 호출하면 현재 구현 기준 `SESSION_EXPIRED`를 반환한다.
- 프론트엔드는 로그아웃 API 응답이 `204` 또는 `401`인 경우 모두 로컬 `accessToken`을 삭제하고 로그인 화면으로 이동한다.

#### Error Response (401 Unauthorized - Token Missing)

```json
{
  "timestamp": "2026-05-12T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "TOKEN_MISSING",
  "errors": [
    {
      "field": "auth",
      "message": "인증 토큰이 없습니다."
    }
  ],
  "path": "/api/v1/auth/logout"
}
```

#### Error Response (401 Unauthorized - Invalid Token)

```json
{
  "timestamp": "2026-05-12T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "INVALID_TOKEN",
  "errors": [
    {
      "field": "token",
      "message": "Authorization 헤더 형식이 올바르지 않습니다."
    }
  ],
  "path": "/api/v1/auth/logout"
}
```

#### Error Response (401 Unauthorized - Session Expired)

```json
{
  "timestamp": "2026-05-12T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "SESSION_EXPIRED",
  "errors": [
    {
      "field": "session",
      "message": "로그인 세션 또는 액세스 토큰이 만료되었습니다. 다시 로그인해 주세요."
    }
  ],
  "path": "/api/v1/auth/logout"
}
```

#### Error Response (401 Unauthorized - Session Revoked)

```json
{
  "timestamp": "2026-05-12T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "SESSION_REVOKED",
  "errors": [
    {
      "field": "session",
      "message": "다른 기기 또는 브라우저에서 다시 로그인되어 현재 세션이 종료되었습니다. 다시 로그인해 주세요."
    }
  ],
  "path": "/api/v1/auth/logout"
}
```

#### 클라이언트 처리 가이드

```javascript
async function logout() {
  try {
    await fetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`
      }
    });
  } finally {
    localStorage.removeItem('accessToken');
    window.location.href = '/login';
  }
}
```

### 5-3. 관리자 계정 페이지 - 신입 계정 생성

관리자 계정 페이지에서 신입 사원의 계정을 직접 생성한다.  
계정 생성 후 사용자는 등록된 회사코드, 사원번호, 이름으로 로그인할 수 있다.

```http
POST /api/v1/admin/users
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### Request Body

```json
{
  "name": "김지원",
  "employeeNumber": "20260001",
  "hireDate": "2026-03-01"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 예시값 | 설명 | 상세 규칙 |
|------|------|------|--------|------|-----------|
| `name` | `String` | Y | `"김지원"` | 신입 사원 이름 | 길이: 1~20자 / 허용 문자: 한글 + 영문 대소문자 / 특수문자·공백·숫자 불가 |
| `employeeNumber` | `String` | Y | `"20260001"` | 신입 사원 사번 | 길이: 4~20자 / 허용 문자: 영문 대소문자 + 숫자 / 특수문자·공백 불가 |
| `hireDate` | `LocalDate` | Y | `"2026-03-01"` | 입사일 | `yyyy-MM-dd` 형식 |

#### 동작 규칙

- 이 API는 고객사 관리자(`users.role = ADMIN`)만 호출할 수 있다.
- 일반 사용자(`USER`)와 제품 내부 관리자(`SERVICE_ADMIN`)가 호출하면 `403 Forbidden`과 `ACCESS_DENIED` 에러 코드를 반환한다.
- `ADMIN`은 자신의 회사 기준으로 신입 계정을 생성한다.
- 서버는 인증 토큰에서 현재 관리자의 `companyCode`를 확인한다.
- 서버는 현재 로그인한 관리자 사용자의 `companyCode`를 기준으로 신입 계정의 `users.company_code`를 저장한다.
- 입력받은 `name`, `employeeNumber`, `hireDate`를 `users` 테이블에 저장한다.
- 생성되는 신입 계정의 `role`은 항상 `USER`로 저장한다.
- 동일 회사 내에서 같은 사원번호로 계정을 중복 생성할 수 없다.
- DB에는 `company_code + employee_number` 복합 UNIQUE 제약조건을 둔다.
- 동일 `company_code + employee_number` 조합이 이미 존재하는 경우 `409 Conflict`와 `DUPLICATE_EMPLOYEE_NUMBER` 에러 코드를 반환한다.
- 계정 생성이 완료되면 해당 사용자는 `POST /api/v1/auth/login`에서 회사코드, 사원번호, 이름을 입력해 로그인할 수 있다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

#### DB 제약조건

```sql
ALTER TABLE users
  ADD CONSTRAINT uq_users_company_employee_number
    UNIQUE (company_code, employee_number);
```

> 실제 마이그레이션 파일에서는 기존 제약조건명 및 컬럼명과 충돌하지 않도록 현재 스키마를 확인한 뒤 적용한다.

#### Response (201 Created)

```json
{
  "id": 10,
  "companyCode": "WB0001",
  "companyName": "테크 주식회사",
  "employeeNumber": "20260001",
  "name": "김지원",
  "role": "USER",
  "hireDate": "2026-03-01",
  "createdAt": "2026-04-28T09:30:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | 생성된 신입 사용자 ID |
| `companyCode` | String | 신입 사용자가 소속된 회사 코드 |
| `companyName` | String | 신입 사용자가 소속된 회사명 |
| `employeeNumber` | String | 신입 사용자 사번 |
| `name` | String | 신입 사용자 이름 |
| `role` | String | 생성된 사용자 역할. 신입 계정 생성 API에서는 항상 `USER` |
| `hireDate` | String | 신입 사용자 입사일 |
| `createdAt` | String | 계정 생성 시각 |

#### Error Response (400 Bad Request)

```json
{
  "timestamp": "2026-04-28T09:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "errors": [
    {
      "field": "name",
      "message": "이름은 필수입니다."
    },
    {
      "field": "employeeNumber",
      "message": "사번은 필수입니다."
    },
    {
      "field": "hireDate",
      "message": "입사일은 필수입니다."
    }
  ],
  "path": "/api/v1/admin/users"
}
```

#### Error Response (403 Forbidden)

```json
{
  "timestamp": "2026-04-28T09:30:00",
  "status": 403,
  "error": "Forbidden",
  "code": "ACCESS_DENIED",
  "errors": [
    {
      "field": "role",
      "message": "관리자 권한이 필요한 API입니다."
    }
  ],
  "path": "/api/v1/admin/users"
}
```

#### Error Response (409 Conflict)

```json
{
  "timestamp": "2026-04-28T09:30:00",
  "status": 409,
  "error": "Conflict",
  "code": "DUPLICATE_EMPLOYEE_NUMBER",
  "errors": [
    {
      "field": "employeeNumber",
      "message": "이미 등록된 사번입니다."
    }
  ],
  "path": "/api/v1/admin/users"
}
```

#### 검증 규칙

- `name`, `employeeNumber`, `hireDate`는 필수값이다.
- `name`, `employeeNumber`는 공백만 입력할 수 없다.
- `hireDate`는 `yyyy-MM-dd` 형식이어야 한다.
- 길이 제한 또는 형식 검증에 실패하면 `400 Bad Request`를 반환한다.
- 중복 사번 검증은 현재 로그인한 관리자 사용자의 회사 범위 안에서 수행한다.
- 이 API의 권한 검증은 `users.role = ADMIN` 기준으로 수행한다.
- 응답 형식은 **4. 표준 응답 형식 > 에러 응답**을 따른다.

### 5-4. 인증 오류 및 세션 만료/무효화 처리

인증이 필요한 API에서 인증 정보가 없거나 올바르지 않거나, 세션이 만료 또는 무효화된 경우 `401 Unauthorized`를 반환한다.  
프론트엔드는 HTTP 상태 코드뿐 아니라 `code` 값을 기준으로 사용자 안내 문구를 구분한다.

#### Error Response (401 Unauthorized - Token Missing)

`Authorization` 헤더가 없거나 Bearer 토큰이 전달되지 않은 경우 아래와 같이 응답한다.

```json
{
  "timestamp": "2026-05-07T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "TOKEN_MISSING",
  "errors": [
    {
      "field": "auth",
      "message": "인증 토큰이 필요합니다."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### Error Response (401 Unauthorized - Invalid Token)

토큰 형식이 올바르지 않거나, 서명 검증에 실패했거나, 서버가 해석할 수 없는 인증 토큰인 경우 아래와 같이 응답한다.

```json
{
  "timestamp": "2026-05-07T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "INVALID_TOKEN",
  "errors": [
    {
      "field": "token",
      "message": "인증 정보가 유효하지 않습니다."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### Error Response (401 Unauthorized - Session Expired)

로그인 세션 또는 액세스 토큰이 만료된 경우 아래와 같이 응답한다.

```json
{
  "timestamp": "2026-05-07T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "SESSION_EXPIRED",
  "errors": [
    {
      "field": "session",
      "message": "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### Error Response (401 Unauthorized - Session Revoked)

동일 사용자의 중복 로그인 등으로 기존 세션이 무효화된 경우 아래와 같이 응답한다.

```json
{
  "timestamp": "2026-05-07T10:35:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "SESSION_REVOKED",
  "errors": [
    {
      "field": "session",
      "message": "다른 기기 또는 브라우저에서 로그인되어 현재 세션이 종료되었습니다. 다시 로그인해 주세요."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### Error Response (503 Service Unavailable - Session Store Unavailable)

Redis 세션 저장소 연결 실패 또는 조회 지연으로 인증 상태를 확인할 수 없는 경우 아래와 같이 응답한다.

```json
{
  "timestamp": "2026-05-12T10:35:00",
  "status": 503,
  "error": "Service Unavailable",
  "code": "SESSION_STORE_UNAVAILABLE",
  "errors": [
    {
      "field": "server",
      "message": "세션 저장소 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### 처리 규칙

- `INVALID_TOKEN`은 토큰 형식 오류, 서명 검증 실패 등 인증 토큰 자체가 유효하지 않은 경우에만 사용한다.
- 세션 또는 액세스 토큰의 유효 시간이 지난 경우 `SESSION_EXPIRED`를 반환한다.
- 중복 로그인 등으로 서버의 활성 세션 기준에서 기존 토큰이 더 이상 유효하지 않은 경우 `SESSION_REVOKED`를 반환한다.
- Redis 세션 저장소 장애로 인증 상태를 확인할 수 없는 경우 `SESSION_STORE_UNAVAILABLE`을 반환한다.
- 사용자를 찾을 수 없는 경우 `USER_NOT_FOUND`를 반환할 수 있다.
- 프론트엔드는 401 응답과 함께 아래 코드 중 하나를 받으면 로그인 페이지로 리다이렉트 처리한다.
  - `TOKEN_MISSING`
  - `INVALID_TOKEN`
  - `SESSION_EXPIRED`
  - `SESSION_REVOKED`
  - `USER_NOT_FOUND`
- 프론트엔드는 `SESSION_EXPIRED`와 `SESSION_REVOKED`를 구분해 사용자 안내 문구를 다르게 표시할 수 있다.

## 6. MyBuddy

MyBuddy는 신입 사용자가 회사 생활과 온보딩 과정에서 궁금한 내용을 질문하고, AI 기반 답변과 온보딩 제안 메시지를 채팅 형태로 확인하는 기능이다.

MyBuddy 화면에서 사용하는 주요 기능은 다음과 같다.

| 구분 | API | 설명 |
|---|---|---|
| 채팅 화면 진입 로그 기록 | `POST /api/v1/chat/session-start` | 사용자가 MyBuddy 화면에 진입했음을 기록한다. |
| 온보딩 제안 노출 처리 | `POST /api/v1/onboarding-suggestions/me/exposure` | 오늘 노출 대상 온보딩 제안이 있으면 `chat_messages`에 suggestion 메시지로 생성한다. |
| 채팅 메시지 목록 조회 | `GET /api/v1/chat/messages` | 현재 로그인한 사용자의 채팅 메시지 목록을 조회한다. |
| 질문 전송 | `POST /api/v1/chat/messages/stream` | 사용자 질문을 저장하고 AI 답변을 생성한다. |
| 빠른 질문 목록 조회 | `GET /api/v1/chat/quick-questions` | 일반 빠른 질문 후보 중 랜덤으로 5개를 조회한다. |
| 빠른 질문 클릭 로그 기록 | `POST /api/v1/chat/quick-questions/click` | 사용자가 빠른 질문 버튼을 클릭한 이력을 기록한다. |

---

### 6-1. MyBuddy 화면 진입 시 권장 호출 흐름

프론트엔드는 MyBuddy 화면 진입 시 다음 순서로 API를 호출한다.

```text
1. POST /api/v1/chat/session-start
   - 채팅 화면 진입 로그를 기록한다.
   - 동일 사용자가 30분 이내 재진입한 경우 중복 기록하지 않는다.

2. POST /api/v1/onboarding-suggestions/me/exposure
   - 오늘 노출 대상 온보딩 제안이 있으면 chat_messages에 suggestion 메시지로 생성한다.
   - 이미 동일한 온보딩 제안 메시지가 있으면 중복 생성하지 않는다.

3. GET /api/v1/chat/messages
   - 실제 화면 렌더링은 이 API 응답만 사용한다.
   - user_question, rag_answer, no_result, out_of_scope, suggestion 메시지를 모두 포함할 수 있다.
```

#### 메시지 렌더링 기준

- 프론트엔드는 별도의 온보딩 제안 배열을 화면에 합치지 않는다.
- 화면에 표시할 채팅 데이터는 `GET /api/v1/chat/messages` 응답의 `messages` 배열만 사용한다.
- 온보딩 제안 메시지도 최종적으로는 `chat_messages`에 저장된 `messageType = suggestion` 메시지로 렌더링한다.
- `messageType = suggestion`인 경우, 해당 메시지 하단에 `quickTaps` 버튼을 노출할 수 있다.
- 사용자가 `quickTaps` 버튼을 클릭하면 해당 항목의 `content` 값을 질문 내용으로 사용하여 `POST /api/v1/chat/messages/stream`를 호출한다.
- 빠른 질문 클릭 로그가 필요한 경우, 프론트엔드는 `quickTaps[].eventTarget` 값을 사용하여 `POST /api/v1/chat/quick-questions/click`을 별도로 호출한다.

---

### 6-2. 채팅 화면 진입 로그 기록

사용자가 MyBuddy 채팅 화면에 진입하면 `user_activity_logs`에 `SESSION_START` 이벤트를 기록한다.

동일 사용자가 30분 이내에 다시 채팅 화면에 진입한 경우에는 중복 기록하지 않는다.

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
  "createdAt": "2026-04-13T09:00:00"
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

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `logged` | Boolean | 이번 요청에서 로그가 새로 저장되었는지 여부 |
| `eventType` | String | 기록된 이벤트 타입 |
| `eventTarget` | String | 이벤트 대상 |
| `message` | String 또는 null | 처리 결과 메시지 |
| `createdAt` | String 또는 null | 로그 생성 시각 |

#### 동작 규칙

- 현재 로그인한 사용자 기준으로 동작한다.
- 채팅 화면 진입 시 `user_activity_logs`에 `event_type = SESSION_START`, `event_target = CHAT`으로 기록한다.
- 저장 항목에는 최소한 `user_id`, `event_type`, `event_target`, `created_at`이 포함된다.
- 동일 사용자가 최근 30분 이내에 이미 `event_type = SESSION_START`, `event_target = CHAT` 이벤트를 기록한 경우 새로 저장하지 않는다.
- 프론트엔드는 MyBuddy 화면 최초 진입 시 이 API를 1회 호출한다.
- 채팅 메시지 목록 조회 API(`GET /api/v1/chat/messages`) 호출만으로는 `SESSION_START` 로그를 자동 기록하지 않는다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

---

### 6-3. 온보딩 제안 노출 처리

현재 로그인한 사용자의 `hireDate`를 기준으로 오늘 노출 대상 온보딩 제안이 있는지 확인한다.

노출 대상 온보딩 제안이 존재하면 해당 제안을 `chat_messages`에 `message_type = suggestion` 메시지로 생성한다.

이미 동일한 온보딩 제안 메시지가 생성되어 있는 경우에는 중복 생성하지 않는다.

```http
POST /api/v1/onboarding-suggestions/me/exposure
Authorization: Bearer {accessToken}
```

#### Response (200 OK, 새 suggestion 메시지 생성)

```json
{
  "created": true,
  "messageId": 301,
  "suggestionId": 5,
  "message": "온보딩 제안 메시지가 생성되었습니다."
}
```

#### Response (200 OK, 이미 생성된 suggestion 메시지 존재)

```json
{
  "created": false,
  "messageId": 301,
  "suggestionId": 5,
  "message": "이미 생성된 온보딩 제안 메시지가 있습니다."
}
```

#### Response (200 OK, 오늘 노출 대상 없음)

```json
{
  "created": false,
  "messageId": null,
  "suggestionId": null,
  "message": "오늘 노출할 온보딩 제안이 없습니다."
}
```

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `created` | Boolean | 이번 요청에서 새 suggestion 메시지가 생성되었는지 여부 |
| `messageId` | Number 또는 null | 생성되었거나 이미 존재하는 `chat_messages.id` |
| `suggestionId` | Number 또는 null | 생성 대상 또는 기존 메시지에 연결된 `onboarding_suggestions.id` |
| `message` | String | 처리 결과 메시지 |

#### 동작 기준

- 날짜 계산 기준은 **Asia/Seoul(KST)** 로 한다.
- `dayOffset = KST 기준 오늘 날짜 - users.hire_date` 로 계산한다.
- `dayOffset` 값에 따라 노출 대상 온보딩 제안을 결정한다.
- 현재 MVP 기준으로 `onboarding_suggestions`는 회사 구분 없이 공통으로 사용한다.
- 노출 대상 온보딩 제안이 존재하면 백엔드는 해당 제안을 `chat_messages`에 저장한다.
- 저장되는 메시지는 `sender_type = BOT`, `message_type = suggestion`을 사용한다.
- `suggestion_id`에는 저장 대상 온보딩 제안의 ID를 저장한다.
- `content`에는 온보딩 제안 본문을 저장한다.
- `{이름}`, `{회사명}`, `{N}`과 같은 플레이스홀더가 있는 경우, 백엔드는 로그인 사용자 정보를 기준으로 치환하여 저장한다.
- 이미 동일한 사용자의 동일한 온보딩 제안 메시지가 저장되어 있으면 중복 저장하지 않는다.
- 중복 판단 기준은 `user_id`, `suggestion_id`, `message_type` 조합이다.
- DB 차원에서도 `user_id`, `suggestion_id`, `message_type` 조합의 유니크 제약을 두어 중복 저장을 최종 방지하는 것을 권장한다.
- 노출 대상 온보딩 제안이 없으면 `chat_messages`에 아무 메시지도 저장하지 않는다.
- 이 API 응답은 화면 렌더링에 직접 사용하지 않는다.
- 실제 화면 렌더링은 이후 호출하는 `GET /api/v1/chat/messages` 응답만 사용한다.
- `messageType = suggestion` 메시지의 `quickTaps`는 `GET /api/v1/chat/messages` 응답에서 제공한다.
- `quickTaps` 목록은 suggestion 메시지 자체에 저장하지 않고, `suggestionId`를 기준으로 응답 DTO 조립 시 포함한다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

---

### 6-4. 채팅 메시지 목록 조회

현재 로그인한 사용자의 채팅 메시지를 `createdAt` 오름차순으로 조회한다.

`date`가 없으면 전체 메시지를 조회하고, `date`가 있으면 해당 날짜의 메시지만 조회한다.

```http
GET /api/v1/chat/messages?date=2026-03-24
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `date` | String (`yyyy-MM-dd`) | N | 조회할 날짜. 지정하지 않으면 전체 메시지를 조회한다. |

#### Response (200 OK)

```json
{
  "messages": [
    {
      "id": 101,
      "suggestionId": null,
      "documents": [],
      "senderType": "USER",
      "messageType": "user_question",
      "content": "복지카드 신청 양식은 어디서 받나요?",
      "quickTaps": [],
      "recommendedContacts": [],
      "createdAt": "2026-03-24T10:00:00"
    },
    {
      "id": 102,
      "suggestionId": null,
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
            "downloadUrl": "/api/v1/chat/documents/11/download"
          }
        }
      ],
      "senderType": "BOT",
      "messageType": "rag_answer",
      "content": "복지카드 신청은 안내 문서를 참고하고, 신청서는 바로 내려받아 작성할 수 있습니다.",
      "quickTaps": [],
      "recommendedContacts": [],
      "createdAt": "2026-03-24T10:00:02"
    },
    {
      "id": 301,
      "suggestionId": 5,
      "documents": [],
      "senderType": "BOT",
      "messageType": "suggestion",
      "content": "3일째, 위드버디에 조금 익숙해졌나요? 🌱 이쯤 되면 이런 게 궁금해지더라고요.",
      "quickTaps": [
        {
          "buttonText": "⏰ 출퇴근 시간",
          "content": "출근 시간과 퇴근 시간은 어떻게 되나요?",
          "eventTarget": "QUICK_TAP_WORK_HOUR"
        },
        {
          "buttonText": "📅 연차 사용",
          "content": "연차는 언제부터 사용할 수 있나요?",
          "eventTarget": "QUICK_TAP_LEAVE_START"
        },
        {
          "buttonText": "📦 장비 신청",
          "content": "업무 장비는 어떻게 신청하나요?",
          "eventTarget": "QUICK_TAP_EQUIPMENT"
        }
      ],
      "recommendedContacts": [],
      "createdAt": "2026-04-30T09:00:00"
    }
  ]
}
```

#### Response (200 OK, 빈 결과)

```json
{
  "messages": []
}
```

#### 동작 규칙

- 현재 로그인한 사용자 기준으로 `chat_messages`를 조회한다.
- 모든 메시지는 `createdAt` 오름차순으로 정렬한다.
- `date`가 지정된 경우 해당 날짜의 00:00:00 이상, 다음 날 00:00:00 미만 범위의 메시지만 조회한다.
- 온보딩 제안 메시지인 경우 `suggestionId`가 포함될 수 있다.
- `senderType`은 `USER`, `BOT` 값을 사용한다.
- `messageType`은 `user_question`, `rag_answer`, `no_result`, `out_of_scope`, `suggestion` 값을 사용한다.
- `rag_answer` 메시지인 경우, 근거 문서는 `chat_message_documents`를 기준으로 조회한다.
- `documents[].documentId`는 답변 메시지와 연결된 문서 ID를 의미한다.
- `documents[].title`은 `documents.title` 값을 의미하며, 프론트엔드에서 근거 문서명 표시용으로 사용한다.
- `documents[].documentType = TEMPLATE`인 경우 `documents[].file` 객체를 포함한다.
- 프론트엔드는 `documents[].file.downloadUrl`을 통해 채팅 근거 문서 다운로드 URL 발급 API를 호출할 수 있다.
- `documents[].documentType != TEMPLATE`인 경우 `documents[].file`은 `null`일 수 있다.
- 실제 파일 데이터는 채팅 메시지 응답 JSON에 직접 포함하지 않고, 별도 파일 API를 통해 반환한다.
- `user_question`, `suggestion`, `no_result`, `out_of_scope` 메시지는 일반적으로 근거 문서를 포함하지 않으므로 `documents`는 빈 배열(`[]`)이다.
- `messageType = suggestion`인 경우, 백엔드는 해당 온보딩 제안에 연결된 빠른 질문 목록을 `quickTaps`에 포함하여 반환한다.
- `messageType = user_question`, `rag_answer`, `no_result`, `out_of_scope`인 경우 `quickTaps`는 빈 배열(`[]`)을 반환한다.
- `messageType = no_result`이고 추천 담당자 정보가 존재하는 경우에만 `recommendedContacts`가 채워질 수 있다.
- `messageType = no_result`인 BOT 메시지의 추천 담당자 정보는 `chat_messages.recommended_contacts_json` 컬럼에 JSON 형태로 저장된 값을 기준으로 반환한다.
- `recommended_contacts_json` 값이 `null`이거나 빈 배열인 경우 `recommendedContacts`는 빈 배열(`[]`)로 반환한다.
- `messageType = user_question`, `rag_answer`, `out_of_scope`, `suggestion`인 경우 `recommendedContacts`는 빈 배열(`[]`)을 반환한다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

---

### 6-5. 질문 전송 및 AI 답변 스트리밍

사용자가 질문을 보내면 사용자 질문 메시지를 저장하고, 내부 AI 서버에 답변 생성을 요청한다.
AI 서버가 스트리밍 방식으로 반환하는 답변 조각을 백엔드는 SSE 형식으로 프론트엔드에 전달한다.

프론트엔드는 `answer_delta` 이벤트를 받을 때마다 `content` 값을 기존 답변 뒤에 이어 붙여 화면에 표시한다.  
최종 답변 저장, 근거 문서 연결 저장, 추천 담당자 정보 전달은 `answer_completed` 이벤트를 기준으로 처리한다.

```http
POST /api/v1/chat/messages/stream
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: text/event-stream
```

#### Request Body

```json
{
  "content": "복지카드는 어떻게 신청하나요?"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 | 상세 규칙 |
|---|---|---|---|---|
| `content` | String | Y | 사용자가 입력한 질문 내용 | 길이 1~500자 / 공백만 입력 불가 / 일반 문장 입력 가능 / 특수문자 허용 |

#### Response

스트림 시작 전 인증, 권한, 요청값 검증이 성공하면 아래와 같이 SSE 응답을 시작한다.

```http
200 OK
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache
Connection: keep-alive
```

> SSE 응답은 일반 JSON 응답처럼 하나의 완성된 Body를 반환하지 않는다.  
> 서버는 이벤트를 순차적으로 전송하고, 프론트엔드는 이벤트를 수신하는 즉시 화면 상태를 갱신한다.

#### SSE Event 목록

| 이벤트명 | 발생 시점 | 설명 |
|---|---|---|
| `question_saved` | 사용자 질문 저장 직후 | 사용자 질문 메시지가 DB에 저장되었음을 알린다. |
| `answer_delta` | AI 서버의 토큰 조각 수신 시 | AI 서버에서 받은 답변 조각을 프론트엔드에 전달한다. |
| `answer_completed` | AI 스트림 정상 종료 및 BOT 메시지 저장 후 | 최종 BOT 메시지 저장 결과를 전달한다. |
| `error` | 스트리밍 중 오류 발생 시 | AI 호출 실패, 스트림 중단, 저장 실패 등의 오류를 전달한다. |

---

#### Event: `question_saved`

사용자 질문 메시지가 저장되었음을 알린다.  
프론트엔드는 이 이벤트를 받으면 사용자 질문 말풍선을 확정 상태로 표시할 수 있다.

```sse
event: question_saved
data: {"question":{"id":201,"suggestionId":null,"documents":[],"senderType":"USER","messageType":"user_question","content":"복지카드는 어떻게 신청하나요?","quickTaps":[],"recommendedContacts":[],"createdAt":"2026-05-04T09:30:00"}}
```

##### Data Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `question` | Object | Y | 저장된 사용자 질문 메시지 |
| `question.id` | Number | Y | `chat_messages.id` |
| `question.senderType` | String | Y | `USER` |
| `question.messageType` | String | Y | `user_question` |
| `question.content` | String | Y | 사용자가 입력한 질문 내용 |
| `question.createdAt` | String | Y | 질문 메시지 저장 시각 |

---

#### Event: `answer_delta`

AI 서버에서 받은 답변 조각을 전달한다.  
프론트엔드는 `answer_delta.content`를 현재 출력 중인 임시 BOT 답변 문자열 뒤에 append한다.

```sse
event: answer_delta
data: {"questionId":201,"content":"복지카드는 "}

```

```sse
event: answer_delta
data: {"questionId":201,"content":"관련 안내 문서를 기준으로 "}

```

```sse
event: answer_delta
data: {"questionId":201,"content":"신청할 수 있습니다."}

```

##### Data Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `questionId` | Number | Y | 사용자 질문 메시지 ID |
| `content` | String | Y | AI 서버에서 수신한 답변 조각 |

##### 처리 규칙

- `answer_delta.content`는 누적된 전체 답변이 아니라 새로 수신한 조각이다.
- `answer_delta`는 한 요청에서 여러 번 발생할 수 있다.
- 백엔드는 AI 서버에서 받은 토큰 문자열을 문장 단위로 재가공하지 않는다.
- 백엔드는 프론트엔드에 전달하는 동시에 전체 답변 문자열을 누적한다.
- 프론트엔드는 `answer_delta`를 받을 때마다 현재 출력 중인 임시 BOT 메시지에 `content`를 이어 붙인다.
- `answer_delta`는 DB 저장 완료를 의미하지 않는다.

#### Event: `answer_completed`

AI 서버 스트림이 정상 종료되고, 백엔드가 최종 BOT 답변 메시지를 저장한 뒤 전달한다.  
프론트엔드는 이 이벤트를 받으면 임시 BOT 답변을 저장 완료된 BOT 메시지로 교체하거나 확정한다.

```sse
event: answer_completed
data: {"questionId":201,"answer":{"id":202,"suggestionId":null,"documents":[{"documentId":10,"title":"복지카드 신청 안내","documentType":"GUIDE","file":null},{"documentId":11,"title":"복지카드 신청서","documentType":"TEMPLATE","file":{"fileName":"welfare-card-application.docx","contentType":"application/vnd.openxmlformats-officedocument.wordprocessingml.document","downloadUrl":"/api/v1/chat/documents/11/download"}}],"senderType":"BOT","messageType":"rag_answer","content":"복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.","quickTaps":[],"recommendedContacts":[],"createdAt":"2026-05-04T09:30:03"}}
```

##### Data Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `questionId` | Number | Y | 사용자 질문 메시지 ID |
| `answer` | Object | Y | 저장 완료된 BOT 답변 메시지 |
| `answer.id` | Number | Y | 저장된 BOT 답변 메시지 ID |
| `answer.senderType` | String | Y | `BOT` |
| `answer.messageType` | String | Y | AI 답변 유형 |
| `answer.content` | String | Y | 최종 AI 답변 전문 |
| `answer.documents` | Array | Y | 답변 메시지에 연결된 근거 문서 목록 |
| `answer.quickTaps` | Array | Y | 일반 AI 답변에서는 빈 배열 `[]` |
| `answer.recommendedContacts` | Array | Y | 담당자 추천 목록. 추천 대상이 없으면 빈 배열 `[]` |
| `answer.createdAt` | String | Y | BOT 답변 메시지 저장 시각 |

##### 처리 규칙

- `answer_completed.answer.content`는 최종 답변 전문이다.
- 프론트엔드는 이미 `answer_delta`로 출력한 문자열에 `answer_completed.answer.content`를 추가로 append하지 않는다.
- 프론트엔드는 누락 방지를 위해 임시 출력 문자열을 `answer_completed.answer.content`로 덮어쓸 수 있다.
- `answer_completed`는 한 요청당 한 번만 전송한다.
- `answer_completed` 이후에는 추가 `answer_delta`를 전송하지 않는다.
- `answer_completed` 수신 시 프론트엔드는 로딩 상태를 종료하고 전송 버튼을 다시 활성화한다.
- `documents`와 `recommendedContacts`는 `GET /api/v1/chat/messages`의 `ChatMessageResponse`와 동일한 구조를 사용한다.

##### AI 서버 `/chat/stream` 기준 메타데이터 처리

AI 서버는 답변 조각을 `answer_delta` 이벤트로 여러 번 전송하고, 답변 생성이 완료되면 `answer_completed` 이벤트를 반드시 한 번 전송한다.

`answer_completed`에는 백엔드가 BOT 메시지 저장 및 후속 데이터 저장에 필요한 아래 값을 모두 포함해야 한다.

- 최종 답변 유형 `messageType`
- 최종 답변 전문 `content`
- 근거 문서 목록 `documents`
- 추천 담당자 목록 `recommendedContacts`

`messageType = no_result`이고 `recommendedContacts`가 존재하는 경우, 백엔드는 해당 값을 `chat_messages.recommended_contacts_json` 컬럼에 JSON 형태로 저장한다. 이후 `GET /api/v1/chat/messages` 조회 시 이 값을 역직렬화하여 `recommendedContacts` 응답 필드로 반환한다.
AI 서버가 `answer_completed`를 전송하지 못하고 연결이 종료되면 백엔드는 해당 AI 답변 생성을 실패로 처리하고 BOT 메시지를 저장하지 않는다.

---

#### Event: `error`

SSE 연결이 시작된 뒤 오류가 발생한 경우 전달한다.

```sse
event: error
data: {"code":"AI_STREAM_FAILED","message":"AI 답변 생성 중 오류가 발생했습니다."}
```

##### Data Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `code` | String | Y | 오류 코드 |
| `message` | String | Y | 사용자에게 표시 가능한 오류 메시지 |
| `questionId` | Number | N | 사용자 질문 메시지 ID. 식별 가능한 경우 포함 |

##### 오류 처리 규칙

- 인증 실패, 세션 만료/무효화, 권한 없음, 요청값 검증 실패처럼 스트림 시작 전에 판별 가능한 오류는 기존 JSON 에러 응답 형식을 사용한다.
- SSE 연결이 시작된 뒤 발생한 오류는 `error` 이벤트로 전달한다.
- `error` 이벤트 전달 후 백엔드는 SSE 연결을 종료한다.
- AI 서버 연결이 중간에 끊기고 최종 답변을 확정할 수 없는 경우 백엔드는 BOT 답변 메시지를 저장하지 않는다.
- 이미 사용자 질문 메시지가 저장된 상태에서 AI 답변 생성에 실패할 수 있다. 이 경우 프론트엔드는 오류 안내를 표시하고 사용자가 같은 질문을 재시도할 수 있게 한다.

#### 공개 API 동작 규칙

- 현재 로그인한 사용자 기준으로 동작한다.
- 백엔드는 JWT를 검증한 뒤 사용자 ID와 회사 코드를 확인한다.
- 질문 메시지는 `chat_messages`에 `sender_type = USER`, `message_type = user_question`으로 먼저 저장한다.
- 백엔드는 질문 저장 후 생성된 질문 메시지의 `id`를 `questionId`로 사용한다.
- 백엔드는 AI 서버의 `POST /chat/stream`을 호출한다.
- 백엔드는 AI 서버에서 받은 토큰 조각을 프론트엔드에 `answer_delta` 이벤트로 전달한다.
- 백엔드는 전달한 토큰 조각을 동시에 누적한다.
- AI 서버가 `answer_completed` 이벤트를 전송하면 백엔드는 해당 이벤트의 `content`, `messageType`, `documents`, `recommendedContacts`를 기준으로 최종 답변을 확정한다.
- 백엔드는 `answer_completed.content`를 `chat_messages`에 `sender_type = BOT`으로 저장한다.
- 백엔드는 `answer_completed.documents[].documentId` 목록을 기준으로 `chat_message_documents`에 답변 메시지와 문서 ID를 연결 저장한다.
- `messageType = no_result`이고 `answer_completed.recommendedContacts`가 존재하는 경우, 백엔드는 해당 값을 `chat_messages.recommended_contacts_json` 컬럼에 JSON 형태로 저장한다.
- 이후 `GET /api/v1/chat/messages` 조회 시 백엔드는 `chat_messages.recommended_contacts_json` 값을 역직렬화하여 `recommendedContacts` 응답 필드로 반환한다.
- `messageType = rag_answer`, `out_of_scope`인 경우 `recommended_contacts_json`은 `null` 또는 빈 배열(`[]`)로 저장할 수 있으며, 응답에서는 `recommendedContacts: []`를 반환한다.
- 백엔드는 BOT 답변 메시지 저장 및 문서 연결 저장이 완료된 뒤 프론트엔드에 `answer_completed` 이벤트를 전송한다.
- 온보딩 제안 메시지는 이 API에서 생성하지 않는다.
- 온보딩 제안 메시지는 `POST /api/v1/onboarding-suggestions/me/exposure` 흐름에서 생성되며, `message_type = suggestion`을 사용한다.
- 사용자가 재시도를 선택한 경우, 프론트엔드는 동일한 질문 내용을 다시 `POST /api/v1/chat/messages/stream`으로 전송한다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

#### Error Response (400 Bad Request)

스트림 시작 전 요청값 검증에 실패한 경우 반환한다.

```json
{
  "timestamp": "2026-05-04T09:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "errors": [
    {
      "field": "content",
      "message": "질문 내용은 비어 있을 수 없습니다."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### Error Response (504 Gateway Timeout)

스트림 시작 전 AI 서버 연결에 실패하거나, AI 서버가 제한 시간 내 스트림을 시작하지 못한 경우 반환한다.

```json
{
  "timestamp": "2026-05-04T09:30:00",
  "status": 504,
  "error": "Gateway Timeout",
  "code": "AI_TIMEOUT",
  "errors": [
    {
      "field": "ai",
      "message": "AI 답변 생성 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

---

### 6-6. 빠른 질문 목록 조회

현재 로그인한 사용자에게 노출할 빠른 질문 목록을 조회한다.

전체 quick tap 후보 중 랜덤으로 5개를 반환한다.

```http
GET /api/v1/chat/quick-questions
Authorization: Bearer {accessToken}
```

#### Response (200 OK)

```json
{
  "quickQuestions": [
    {
      "buttonText": "🏢 출근 장소·입장 방법",
      "content": "첫 출근 장소와 입장 방법이 어떻게 되나요?",
      "eventTarget": "QUICK_TAP_LOCATION"
    },
    {
      "buttonText": "🕘 출근 시간·근무 형태",
      "content": "출근 시간과 근무 형태가 어떻게 되나요?",
      "eventTarget": "QUICK_TAP_WORK_HOUR"
    },
    {
      "buttonText": "👔 복장 규정",
      "content": "회사 복장 규정이 있나요?",
      "eventTarget": "QUICK_TAP_DRESSCODE"
    },
    {
      "buttonText": "📋 제출 서류",
      "content": "입사 첫날 제출해야 하는 서류는 무엇인가요?",
      "eventTarget": "QUICK_TAP_DOCS"
    },
    {
      "buttonText": "💻 이메일·계정 세팅",
      "content": "회사 이메일 계정은 어떻게 세팅하나요?",
      "eventTarget": "QUICK_TAP_IT_SETUP"
    }
  ]
}
```

#### Response (200 OK, 빈 결과)

```json
{
  "quickQuestions": []
}
```

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `quickQuestions` | Array | 현재 사용자에게 노출할 빠른 질문 목록 |
| `quickQuestions[].buttonText` | String | 화면에 표시할 버튼 텍스트 |
| `quickQuestions[].content` | String | 사용자가 버튼을 클릭했을 때 실제 질문으로 전송할 문장 |
| `quickQuestions[].eventTarget` | String | 빠른 질문 클릭 로그 저장 시 사용할 eventTarget 값 |

#### 동작 규칙

- 빠른 질문은 사용자가 자주 묻는 질문을 버튼 형태로 제공하기 위한 추천 질문 목록이다.
- 현재 MVP 기준으로 빠른 질문 목록은 회사 구분 없이 공통으로 제공한다.
- 전체 quick tap 후보 중 랜덤으로 5개를 반환한다.
- 이 API는 빠른 질문 버튼 목록만 조회한다.
- 이 API는 클릭 로그 저장이나 채팅 메시지 생성을 수행하지 않는다.
- 사용자가 빠른 질문을 클릭하면, 프론트엔드는 해당 항목의 `content` 값을 일반 질문과 동일하게 `POST /api/v1/chat/messages/stream`로 전송한다.
- 빠른 질문 클릭 로그를 남기려면 프론트엔드가 별도로 `POST /api/v1/chat/quick-questions/click`을 호출한다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

---

### 6-7. 빠른 질문 클릭 로그 기록

사용자가 빠른 질문 버튼을 클릭하면 `user_activity_logs`에 `BUTTON_CLICK` 이벤트를 기록한다.

이때 `eventTarget`은 사용자가 클릭한 빠른 질문 항목의 `eventTarget` 값을 저장한다.

```http
POST /api/v1/chat/quick-questions/click
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### Request Body

```json
{
  "eventTarget": "QUICK_TAP_LOCATION"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `eventTarget` | String | Y | 사용자가 클릭한 빠른 질문 버튼의 eventTarget 값 |

#### Response (201 Created)

```json
{
  "logged": true,
  "eventType": "BUTTON_CLICK",
  "eventTarget": "QUICK_TAP_LOCATION",
  "message": null,
  "createdAt": "2026-04-24T09:30:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `logged` | Boolean | 이번 요청에서 로그가 저장되었는지 여부 |
| `eventType` | String | 기록된 이벤트 타입 |
| `eventTarget` | String | 클릭한 빠른 질문 버튼의 eventTarget |
| `message` | String 또는 null | 처리 결과 메시지 |
| `createdAt` | String | 로그 생성 시각 |

#### 동작 규칙

- 현재 로그인한 사용자 기준으로 동작한다.
- 빠른 질문 버튼 클릭 시 `user_activity_logs`에 `event_type = BUTTON_CLICK`으로 기록한다.
- `event_target`에는 사용자가 클릭한 버튼의 실제 `eventTarget` 값을 저장한다.
- 저장 항목에는 `user_id`, `event_type`, `event_target`, `created_at`이 포함된다.
- 이 API는 클릭 로그만 저장한다.
- 이 API는 채팅 메시지 생성이나 AI 답변 생성을 수행하지 않는다.
- 빠른 질문을 실제 질문으로 전송하려면 프론트엔드가 별도로 `POST /api/v1/chat/messages/stream`를 호출한다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

---

### 6-8. 공통 응답 객체

#### ChatMessageResponse

`GET /api/v1/chat/messages`의 `messages[]`, `POST /api/v1/chat/messages/stream`의 `question_saved.question`, `answer_completed.answer`는 모두 아래 구조를 기준으로 한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | `chat_messages.id` |
| `suggestionId` | Number 또는 null | 온보딩 제안 메시지와 연결된 `onboarding_suggestions.id` |
| `documents` | Array | 답변 메시지와 연결된 근거 문서 목록 |
| `senderType` | String | 메시지 발신자 타입 |
| `messageType` | String | 메시지 유형 |
| `content` | String | 메시지 본문 |
| `quickTaps` | Array | suggestion 메시지 하단에 노출할 빠른 질문 버튼 목록 |
| `recommendedContacts` | Array | no_result 메시지에서 노출할 담당자 추천 목록 |
| `createdAt` | String | 메시지 생성 시각 |

#### DocumentResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `documentId` | Number | 문서 ID |
| `title` | String | 문서 제목 |
| `documentType` | String | 문서 유형 |
| `file` | Object 또는 null | 다운로드 가능한 파일 정보 |

#### FileResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `fileName` | String | 원본 파일명 |
| `contentType` | String | 파일 MIME 타입 |
| `downloadUrl` | String | 파일 다운로드 API URL |

#### QuickTapResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `buttonText` | String | 화면에 표시할 버튼 텍스트 |
| `content` | String | 사용자가 버튼을 클릭했을 때 실제 질문으로 전송할 문장 |
| `eventTarget` | String | 빠른 질문 클릭 로그 저장 시 사용할 eventTarget 값 |

#### RecommendedContactResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `department` | String | 담당 부서명 |
| `name` | String | 담당자 이름 |
| `position` | String | 담당자 직급 |
| `connects` | Array | 연락 수단 목록 |

#### ContactMethodResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | String | 연락 수단 유형 |
| `value` | String | 실제 연락 값 |


#### ChatStreamQuestionSavedEvent

`POST /api/v1/chat/messages/stream`의 `question_saved` 이벤트 data 구조다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `question` | ChatMessageResponse | 저장된 사용자 질문 메시지 |

#### ChatStreamAnswerDeltaEvent

`POST /api/v1/chat/messages/stream`의 `answer_delta` 이벤트 data 구조다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `questionId` | Number | 사용자 질문 메시지 ID |
| `content` | String | AI 서버에서 수신한 답변 조각 |

#### ChatStreamAnswerCompletedEvent

`POST /api/v1/chat/messages/stream`의 `answer_completed` 이벤트 data 구조다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `questionId` | Number | 사용자 질문 메시지 ID |
| `answer` | ChatMessageResponse | 저장 완료된 BOT 답변 메시지 |

#### ChatStreamErrorEvent

`POST /api/v1/chat/messages/stream`의 `error` 이벤트 data 구조다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | String | 오류 코드 |
| `message` | String | 사용자에게 표시 가능한 오류 메시지 |
| `questionId` | Number 또는 null | 사용자 질문 메시지 ID. 식별 가능한 경우 포함 |

---

### 6-9. 공통 값 설명

#### senderType

| 값 | 설명 |
|---|---|
| `USER` | 사용자 |
| `BOT` | 챗봇 |

#### messageType

| 값 | 설명 |
|---|---|
| `user_question` | 사용자가 입력한 질문 메시지 |
| `rag_answer` | 문서 기반으로 답변이 생성된 메시지 |
| `no_result` | 질문 범위는 맞지만 근거 문서나 정보가 부족해 답변하지 못한 메시지 |
| `out_of_scope` | 서비스 범위를 벗어난 질문에 대한 안내 메시지 |
| `suggestion` | 온보딩 제안 메시지 |

#### UserRole

| 값 | 설명 |
|---|---|
| `USER` | 일반 신입 사용자. MyBuddy 채팅 기능을 사용한다. |
| `ADMIN` | 고객사 관리자. 관리자 계정 페이지에서 신입 계정 생성 및 문서 관리 API를 호출할 수 있다. |
| `SERVICE_ADMIN` | 제품 내부 지표 조회용 계정. MVP 기준 일반 `USER`와 동일하게 MyBuddy 기능을 사용할 수 있으며, 추가로 관리자 지표 API(`/api/v1/admin/metrics/*`)를 호출할 수 있다. 고객사 관리자 계정 페이지 API와 문서 관리 API는 호출할 수 없다. |

#### ContactType

| 값 | 설명 |
|---|---|
| `slack` | Slack 사용자 또는 채널 연결 정보 |
| `email` | 이메일 주소 |
| `phone` | 일반 전화번호 또는 휴대전화번호 |
| `extension` | 사내 내선 번호 |

#### eventTarget 허용값

빠른 질문 클릭 로그 기록 시 `eventTarget`에는 사용자가 클릭한 빠른 질문 버튼의 `eventTarget` 값을 전달한다.

| 버튼 텍스트 | 전송 질문 (`content`) | eventTarget |
|---|---|---|
| 🏢 출근 장소·입장 방법 | 첫 출근 장소와 입장 방법이 어떻게 되나요? | `QUICK_TAP_LOCATION` |
| 🕘 출근 시간 | 출근 시간이 어떻게 되나요? | `QUICK_TAP_WORK_HOUR` |
| 👔 복장 규정 | 회사 복장 규정이 있나요? | `QUICK_TAP_DRESSCODE` |
| 📍 첫날 누구를 찾아요? | 첫 출근 시 어디로 가야 하고 누구를 찾으면 되나요? | `QUICK_TAP_FIRST_DAY` |
| 🔑 출입카드 받는 법 | 출입카드는 어떻게 받나요? | `QUICK_TAP_ACCESS` |
| 📋 제출 서류 | 입사 첫날 제출해야 하는 서류는 무엇인가요? | `QUICK_TAP_DOCS` |
| 💻 이메일·계정 세팅 | 회사 이메일 계정은 어떻게 세팅하나요? | `QUICK_TAP_IT_SETUP` |
| 📦 비품 신청하기 | 업무에 필요한 비품은 어떻게 신청하나요? | `QUICK_TAP_EQUIPMENT` |
| 📅 연차 언제부터? | 연차는 입사 후 언제부터 쓸 수 있나요? | `QUICK_TAP_LEAVE_START` |
| 🖨️ 프린터·사무기기 사용법 | 프린터나 사무기기는 어떻게 사용하나요? | `QUICK_TAP_PRINTER` |
| 🗓️ 회의실 예약 방법 | 회의실은 어떻게 예약하나요? | `QUICK_TAP_MEETING_ROOM` |
| 🍱 점심 식대 지원 | 점심 식대 지원은 어떻게 되나요? | `QUICK_TAP_MEAL` |
| 💳 복지 혜택 적용 시점 | 복지카드·자기계발비는 입사 후 언제부터 사용할 수 있나요? | `QUICK_TAP_WELFARE` |
| 🧾 Slack 채널 어떻게 써요? | 사내 Slack 채널 종류와 각 채널 용도가 어떻게 되나요? | `QUICK_TAP_SLACK_GUIDE` |
| 🔐 보안·파일 저장 규칙 | 회사 보안 규정이나 업무 파일 저장 규칙이 어떻게 되나요? | `QUICK_TAP_SECURITY` |
| 🕘 지각·조퇴 처리 방법 | 지각이나 조퇴가 생기면 어떻게 처리하나요? | `QUICK_TAP_LATE` |
| 📅 반차 사용 방법 | 반차는 어떻게 신청하나요? | `QUICK_TAP_HALF_DAY` |
| 💊 병가 규정 | 몸이 아플 때 병가는 며칠까지 쓸 수 있나요? | `QUICK_TAP_SICK` |
| 📝 결재는 어떻게 해요? | 업무 결재나 승인은 어디서, 어떻게 하나요? | `QUICK_TAP_APPROVAL` |
| 🖥️ 업무 시스템 권한 신청 | 추가로 필요한 업무 시스템 권한은 어떻게 신청하나요? | `QUICK_TAP_SYSTEM_AUTH` |
| 🏠 재택근무 신청 | 재택근무는 어떻게 신청하나요? | `QUICK_TAP_REMOTE` |
| 💳 외근 보고 방법 | 외근이 생기면 어떻게 보고하나요? | `QUICK_TAP_WORKING_OUTSIDE` |
| 🧾 법인카드 사용 후 처리 | 법인카드 사용 후 어떻게 처리하나요? | `QUICK_TAP_CORP_CARD` |
| 💬 버디 제도란? | 버디 제도가 어떻게 운영되나요? | `QUICK_TAP_BUDDY` |
| 💳 복지 혜택 신청하기 | 복지 혜택은 어떻게 신청하나요? | `QUICK_TAP_WELFARE_APPLY` |
| 💰 급여명세서 확인 | 급여명세서는 어디서 확인하나요? | `QUICK_TAP_SALARY` |
| 📊 수습 평가 기준 | 수습 기간 평가는 어떤 기준으로 이루어지나요? | `QUICK_TAP_PROBATION` |
| 🏖️ 연차 신청 방법 | 연차 신청은 어떻게 하나요? | `QUICK_TAP_LEAVE_REQ` |
| 🏥 건강검진 언제부터? | 건강검진 지원은 언제부터 받을 수 있나요? | `QUICK_TAP_HEALTH` |
| 📚 교육·자기계발 지원 | 업무 관련 강의나 책 구입 비용을 지원받을 수 있나요? | `QUICK_TAP_EDUCATION` |
| 📋 정규직 전환 절차 | 수습 기간 종료 후 정규직 전환 절차가 어떻게 되나요? | `QUICK_TAP_CONVERT` |
| 📊 급여 공제 항목이 뭔가요? | 급여에서 공제되는 항목이 어떻게 되나요? | `QUICK_TAP_SALARY_DEDUCTION` |
| 🎯 전환 후 평가 방식 | 정규직 전환 후 목표나 평가 방식은 어떻게 되나요? | `QUICK_TAP_KPI` |

---

### 6-10. 공통 에러 처리

인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

#### Error Response (400 Bad Request)

요청 값이 유효하지 않은 경우 반환한다.

```json
{
  "timestamp": "2026-03-25T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "errors": [
    {
      "field": "content",
      "message": "질문 내용은 비어 있을 수 없습니다."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### Error Response (401 Unauthorized)

인증 헤더가 없거나, 토큰이 유효하지 않거나, 세션이 만료 또는 무효화된 경우 반환한다.
세부 에러 코드는 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

```json
{
  "timestamp": "2026-03-25T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "INVALID_TOKEN",
  "errors": [
    {
      "field": "token",
      "message": "인증 정보가 유효하지 않습니다."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

#### Error Response (504 Gateway Timeout)

AI 답변 생성 시간이 초과된 경우 반환한다.

```json
{
  "timestamp": "2026-04-16T11:30:00",
  "status": 504,
  "error": "Gateway Timeout",
  "code": "AI_TIMEOUT",
  "errors": [
    {
      "field": "ai",
      "message": "AI 답변 생성 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."
    }
  ],
  "path": "/api/v1/chat/messages/stream"
}
```

---

## 7. 내부 AI 연동 규격

이 항목은 백엔드 서버와 생성형 AI 서버 간 내부 통신 규격이다.
프론트엔드가 직접 호출하지 않는다.

현재 AI 서버 Swagger 기준 스트리밍 endpoint는 `POST /chat/stream`이다.  
해당 API는 오케스트레이터 기반 멀티에이전트 질의응답을 수행하고, SSE 스트리밍 방식으로 토큰 단위 응답을 전달한다.

### 7-1. 연동 흐름

```text
프론트엔드
  → POST /api/v1/chat/messages/stream
백엔드
  → USER 질문 메시지 저장
백엔드
  → POST {AI_SERVER_BASE_URL}/chat/stream 호출
AI 서버
  → answer_delta 이벤트로 답변 조각 반환
AI 서버
  → answer_completed 이벤트로 최종 답변·문서·담당자 메타데이터 반환
백엔드
  → answer_delta 이벤트로 프론트엔드에 중계
백엔드
  → AI answer_completed 기준으로 BOT 메시지와 문서 연결 정보 저장
백엔드
  → answer_completed 이벤트 전송 후 SSE 연결 종료
```

#### 상세 흐름

- 사용자가 질문을 전송한다.
- 백엔드가 `/api/v1/chat/messages/stream` 요청을 받는다.
- 백엔드는 JWT를 검증한 뒤 현재 사용자 ID와 회사 코드를 확인하고, 사용자 정보에서 이름과 입사일(`hireDate`)을 조회한다.
- 백엔드가 사용자 질문 메시지를 `chat_messages`에 저장한다.
- 백엔드가 생성된 질문 메시지의 `id`를 `questionId`로 사용한다.
- 백엔드가 AI 서버의 `/chat/stream`을 호출한다.
- AI 서버가 `content`, `user.companyCode`, `user.hireDate`를 기준으로 회사 문서 범위와 입사일 기반 온보딩 맥락을 반영해 답변을 생성한다.
- AI 서버가 답변 조각을 `answer_delta` 이벤트로 스트리밍 반환한다.
- 백엔드는 AI 서버에서 받은 답변 조각을 프론트엔드에 `answer_delta` 이벤트로 전달한다.
- AI 서버는 답변 생성이 완료되면 `answer_completed` 이벤트를 반드시 한 번 전송한다.
- AI 서버의 `answer_completed`에는 `questionId`, `messageType`, `content`, `documents`, `recommendedContacts`가 포함되어야 한다.
- 백엔드는 AI 서버의 `answer_completed`를 기준으로 BOT 메시지와 문서 연결 정보를 저장한다.
- 백엔드는 저장 완료된 BOT 메시지를 공개 SSE의 `answer_completed` 이벤트로 프론트엔드에 전달한다.

---

### 7-2. AI 답변 생성 스트리밍 요청

```http
POST {AI_SERVER_BASE_URL}/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

#### AI Server Base URL

```text
Development/External Swagger: https://ai.itsdev.kr
```

> 실제 백엔드 설정에서는 환경변수 또는 설정 파일의 AI 서버 base URL을 사용한다.

#### Request Body

AI 서버 Swagger의 `/chat/stream` 요청 예시 기준으로 작성한다.

```json
{
  "questionId": 201,
  "user": {
    "userId": 1,
    "name": "김지원",
    "companyCode": "WB0001",
    "hireDate": "2026-05-01"
  },
  "content": "연차 신청 방법이 뭐야?"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 예시값 | 설명 | 상세 규칙 |
|---|---|---|---|---|---|
| `questionId` | Long | Y | `201` | 백엔드가 저장한 사용자 질문 메시지 ID | 양의 정수 |
| `user` | Object | Y | `{ "userId": 1, "name": "김지원", "companyCode": "WB0001", "hireDate": "2026-05-01" }` | 답변 생성에 사용할 사용자 정보 | 사용자 식별, 개인화, 회사 문서 범위 판별, 입사일 기준 온보딩 맥락 판단에 사용 |
| `content` | String | Y | `"연차 신청 방법이 뭐야?"` | 사용자가 입력한 질문 내용 | 길이 1~500자 / 공백만 입력 불가 |

#### Request Field (`user`)

| 필드 | 타입 | 필수 | 예시값 | 설명 | 상세 규칙 |
|---|---|---|---|---|---|
| `user.userId` | Long | Y | `1` | 로그인한 사용자 ID | 양의 정수 |
| `user.name` | String | Y | `"김지원"` | 로그인한 사용자 이름 | 길이 1~100자 |
| `user.companyCode` | String | Y | `"WB0001"` | 로그인한 사용자의 회사 코드 | 길이 1~20자 / 허용 문자: 영문 대소문자 + 숫자 / 특수문자·공백 불가 |
| `user.hireDate` | String | Y | `"2026-05-01"` | 로그인한 사용자의 입사일 | ISO-8601 날짜 형식(`yyyy-MM-dd`) / 답변 개인화 및 입사일 기준 온보딩 맥락 판단에 사용 |

#### 기존 내부 AI 요청과의 차이

기존 명세의 내부 AI 요청은 `POST /internal/ai/answer`에 아래 값을 전달하는 구조였다.

- `questionId`
- `user`
- `content`
- `conversationHistory`
- 선택적으로 `companyName`

SSE 적용 후 AI 서버 Swagger의 `/chat/stream` 기준 요청에는 `conversationHistory`와 `companyName`이 정의되어 있지 않다.  
따라서 본 명세에서는 AI 서버로 전달하는 필드를 아래 세 가지로 제한한다.

- `questionId`
- `user.userId`, `user.name`, `user.companyCode`, `user.hireDate`
- `content`

향후 AI 서버가 `conversationHistory` 또는 `companyName`을 스트림 요청 스키마에 추가하면, 백엔드 내부 요청 DTO를 확장한다.

---

### 7-3. AI 답변 생성 스트리밍 응답

AI 서버는 답변 생성 중에는 `answer_delta` 이벤트로 답변 조각을 전송하고, 답변 생성이 완료되면 `answer_completed` 이벤트를 반드시 한 번 전송한다.

```http
200 OK
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache
```

AI Swagger의 200 응답 스키마는 문자열(`string`)로 표시될 수 있으나, 백엔드 저장을 위해 실제 스트림 응답은 아래 이벤트 규격을 따른다.

#### Event: `answer_delta`

AI가 생성한 답변 조각을 전달한다.

```sse
event: answer_delta
data: {"questionId":201,"content":"복지카드는 "}

```

```sse
event: answer_delta
data: {"questionId":201,"content":"관련 안내 문서를 기준으로 "}

```

```sse
event: answer_delta
data: {"questionId":201,"content":"신청할 수 있습니다."}

```

##### Data Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `questionId` | Long | Y | 사용자 질문 메시지 ID |
| `content` | String | Y | AI가 생성한 답변 조각 |

##### 처리 규칙

- `answer_delta.content`는 누적된 전체 답변이 아니라 새로 생성된 조각이다.
- `answer_delta`는 한 요청에서 여러 번 발생할 수 있다.
- 빈 문자열 또는 공백만 포함된 `answer_delta`는 전송하지 않는다.
- `answer_delta`는 최종 저장 가능 상태를 의미하지 않는다.

#### Event: `answer_completed`

AI 답변 생성이 완료되었음을 알리고, 백엔드 저장에 필요한 최종 결과와 메타데이터를 전달한다.

##### 문서 기반 답변 예시

```sse
event: answer_completed
data: {"questionId":201,"messageType":"rag_answer","content":"복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.","documents":[{"documentId":10},{"documentId":11}],"recommendedContacts":[]}
```

##### 답변 불가 및 담당자 추천 예시

```sse
event: answer_completed
data: {"questionId":201,"messageType":"no_result","content":"관련 문서를 찾지 못했습니다. 담당자에게 문의해 주세요.","documents":[],"recommendedContacts":[{"department":"경영지원팀","name":"김지수","position":"매니저","connects":[{"type":"email","value":"jisoo.kim@withbuddy.ai"},{"type":"extension","value":"635"}]}]}
```

##### Data Field

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `questionId` | Long | Y | 사용자 질문 메시지 ID |
| `messageType` | String | Y | AI 답변 유형. `rag_answer`, `no_result`, `out_of_scope` 중 하나 |
| `content` | String | Y | AI가 생성한 최종 답변 전문 |
| `documents` | Array | Y | 답변 생성의 근거로 사용된 문서 목록. 없으면 빈 배열 `[]` |
| `documents[].documentId` | Long | Y | 근거 문서 ID |
| `recommendedContacts` | Array | Y | 추천 담당자 목록. 추천 대상이 없으면 빈 배열 `[]` |

##### Data Field (`recommendedContacts[]`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `recommendedContacts[].department` | String | Y | 담당 부서명 |
| `recommendedContacts[].name` | String | Y | 담당자 이름 |
| `recommendedContacts[].position` | String | Y | 담당자 직급 |
| `recommendedContacts[].connects` | Array | Y | 연락 수단 목록 |
| `recommendedContacts[].connects[].type` | String | Y | 연락 수단 유형. `slack`, `email`, `phone`, `extension` 중 하나 |
| `recommendedContacts[].connects[].value` | String | Y | 실제 연락 값 |

##### 처리 규칙

- AI 서버는 `answer_completed`를 한 요청당 반드시 한 번만 전송한다.
- `answer_completed` 이후에는 추가 `answer_delta`를 전송하지 않는다.
- `answer_completed.content`는 이전에 전송한 `answer_delta.content`를 모두 이어 붙인 최종 답변과 의미상 동일해야 한다.
- 백엔드는 최종 BOT 메시지를 저장할 때 `answer_completed.content`를 우선 사용한다.
- 백엔드는 `answer_completed.messageType`을 BOT 메시지의 `message_type`으로 저장한다.
- 백엔드는 `answer_completed.documents[].documentId`를 기준으로 문서 정보를 조회하고, `chat_message_documents`에 답변 메시지와 문서 ID를 연결 저장한다.
- `messageType = rag_answer`인 경우 `documents`는 근거 문서 목록을 포함할 수 있다.
- `messageType = no_result` 또는 `out_of_scope`인 경우 `documents`는 빈 배열 `[]`을 반환한다.
- `messageType = no_result`인 경우 추천 가능한 담당자가 있으면 `recommendedContacts`를 함께 반환한다.
- 추천 담당자가 없는 경우 `recommendedContacts`는 빈 배열 `[]`을 반환한다.
- AI 서버가 `answer_completed`를 전송하지 못하고 연결이 종료되면 백엔드는 해당 요청을 실패로 처리하고 BOT 메시지를 저장하지 않는다.

---

### 7-4. AI 스트림 응답과 프론트엔드 SSE 이벤트 매핑

AI 서버의 `/chat/stream` 응답은 백엔드가 프론트엔드 공개 SSE 이벤트로 변환한다.

| AI 서버 응답 | 백엔드 공개 SSE 이벤트 | 설명 |
|---|---|---|
| `answer_delta` | `answer_delta` | AI 답변 조각을 `content`에 담아 프론트엔드로 즉시 전달 |
| `answer_completed` | `answer_completed` | AI 최종 결과를 기준으로 BOT 메시지와 문서 연결 정보를 저장한 뒤 저장 완료 메시지 전달 |
| `error` 또는 연결 실패 | `error` | 프론트엔드에 오류 이벤트 전달 |

#### 변환 예시

AI 서버 응답:

```sse
event: answer_delta
data: {"questionId":201,"content":"복지카드는 "}

```

```sse
event: answer_delta
data: {"questionId":201,"content":"관련 안내 문서를 기준으로 신청할 수 있습니다."}

```

```sse
event: answer_completed
data: {"questionId":201,"messageType":"rag_answer","content":"복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.","documents":[{"documentId":10},{"documentId":11}],"recommendedContacts":[]}
```

프론트엔드로 전달하는 백엔드 SSE 응답:

```sse
event: answer_delta
data: {"questionId":201,"content":"복지카드는 "}

```

```sse
event: answer_delta
data: {"questionId":201,"content":"관련 안내 문서를 기준으로 신청할 수 있습니다."}

```

```sse
event: answer_completed
data: {"questionId":201,"answer":{"id":202,"suggestionId":null,"documents":[{"documentId":10,"title":"복지카드 신청 안내","documentType":"GUIDE","file":null},{"documentId":11,"title":"복지카드 신청서","documentType":"TEMPLATE","file":{"fileName":"welfare-card-application.docx","contentType":"application/vnd.openxmlformats-officedocument.wordprocessingml.document","downloadUrl":"/api/v1/chat/documents/11/download"}}],"senderType":"BOT","messageType":"rag_answer","content":"복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.","quickTaps":[],"recommendedContacts":[],"createdAt":"2026-05-04T09:30:03"}}
```

#### 변환 규칙

- AI 서버의 `answer_delta.content`는 백엔드가 문장 가공 없이 프론트엔드의 `answer_delta.content`로 전달한다.
- AI 서버의 `answer_completed.content`는 백엔드가 BOT 메시지 저장에 사용하는 최종 답변 전문이다.
- AI 서버의 `answer_completed.messageType`은 백엔드가 BOT 메시지의 `message_type`으로 저장한다.
- AI 서버의 `answer_completed.documents[].documentId`는 백엔드가 `chat_message_documents` 저장 및 `DocumentResponse` 조립에 사용한다.
- AI 서버의 `answer_completed.recommendedContacts`는 백엔드가 담당자 추천 응답 구성에 사용한다.
- 백엔드가 프론트엔드에 전달하는 `answer_completed.answer.documents`는 AI 서버가 준 `documentId`만 그대로 전달하지 않고, 백엔드 DB의 문서 정보(`title`, `documentType`, `file`)를 조회해 보강한 값이다.

---

### 7-5. 메타데이터 처리 기준

AI 서버는 `/chat/stream`의 최종 완료 이벤트인 `answer_completed`에 백엔드 저장용 메타데이터를 반드시 포함한다.

| 항목 | 처리 기준 |
|---|---|
| `content` | AI가 생성한 최종 답변 전문. 백엔드는 이 값을 BOT 메시지 본문으로 저장한다. |
| `messageType` | AI 답변 유형. 백엔드는 이 값을 BOT 메시지의 `message_type`으로 저장한다. |
| `documents` | 답변 근거 문서 ID 목록. 백엔드는 이 값을 `chat_message_documents` 저장에 사용한다. |
| `recommendedContacts` | 담당자 추천 목록. 백엔드는 이 값을 담당자 추천 응답 구성 또는 저장에 사용한다. |

#### 필수 반환 규칙

- AI 서버는 `answer_completed.messageType`을 반드시 반환한다.
- AI 서버는 `answer_completed.content`를 반드시 반환한다.
- AI 서버는 `answer_completed.documents`를 반드시 반환한다. 근거 문서가 없으면 빈 배열 `[]`을 반환한다.
- AI 서버는 `answer_completed.recommendedContacts`를 반드시 반환한다. 추천 담당자가 없으면 빈 배열 `[]`을 반환한다.
- 백엔드는 `documents[].documentId`가 현재 사용자 회사 문서 또는 공통 문서 범위에 속하는지 검증한 뒤 저장한다.
- 백엔드는 존재하지 않거나 접근 권한이 없는 `documentId`가 포함된 경우 해당 문서를 제외하거나 내부 오류로 처리할 수 있다.

#### 값 설명

##### `messageType`

| 값 | 설명 |
|---|---|
| `rag_answer` | 문서 기반 답변 생성 |
| `no_result` | 질문 범위는 맞지만 문서 또는 정보 부족으로 답변 불가 |
| `out_of_scope` | 서비스 범위를 벗어난 질문 |

##### `recommendedContacts[].connects[].type`

| 값 | 설명 |
|---|---|
| `slack` | Slack 사용자 또는 채널 연결 정보 |
| `email` | 이메일 주소 |
| `phone` | 일반 전화번호 또는 휴대전화번호 |
| `extension` | 사내 내선 번호 |

#### 저장 규칙

- 백엔드는 `answer_completed.content`를 `chat_messages.content`로 저장한다.
- 백엔드는 `answer_completed.messageType`을 `chat_messages.message_type`으로 저장한다.
- 백엔드는 `answer_completed.documents[].documentId`를 답변 메시지 ID와 함께 `chat_message_documents`에 저장한다.
- `messageType = no_result`이고 `recommendedContacts`가 빈 배열이 아닌 경우, 백엔드는 해당 값을 `chat_messages.recommended_contacts_json` 컬럼에 JSON 형태로 저장한다.
- `messageType = no_result`이지만 `recommendedContacts`가 빈 배열인 경우, `recommended_contacts_json`은 `null` 또는 빈 배열(`[]`)로 저장한다.
- `recommended_contacts_json`은 담당자 추천 카드 재조회용 저장 필드다.
- 이후 `GET /api/v1/chat/messages` 조회 시 백엔드는 `recommended_contacts_json` 값을 `RecommendedContactResponse[]`로 변환하여 `recommendedContacts`에 담아 반환한다.
- `messageType = rag_answer` 또는 `out_of_scope`인 경우 `recommendedContacts`는 빈 배열(`[]`)을 사용하며, `recommended_contacts_json`은 `null` 또는 빈 배열(`[]`)로 저장할 수 있다.

---

### 7-6. 내부 AI 오류 처리

#### AI 서버 Validation Error

AI 서버 Swagger 기준 요청값 검증 실패 시 422 응답이 발생할 수 있다.

```json
{
  "detail": [
    {
      "loc": [
        "body",
        "content"
      ],
      "msg": "string",
      "type": "string",
      "input": "string",
      "ctx": {}
    }
  ]
}
```

#### 백엔드 처리 규칙

- 백엔드가 AI 서버 호출 전에 공개 API 요청값을 먼저 검증한다.
- 공개 API 요청값 검증 실패는 `400 Bad Request`로 처리한다.
- AI 서버가 422를 반환하면 백엔드는 내부 AI 요청 DTO 구성 또는 AI 서버 스키마 불일치로 간주한다.
- 스트림 시작 전 AI 서버 오류가 발생하면 공개 API는 기존 JSON 에러 응답으로 `500 Internal Server Error` 또는 `504 Gateway Timeout`을 반환한다.
- 스트림 시작 후 AI 서버 오류가 발생하면 프론트엔드에 `error` 이벤트를 전달하고 SSE 연결을 종료한다.
- AI 서버 연결 타임아웃은 `AI_TIMEOUT`으로 처리한다.
- AI 서버가 `answer_completed`를 전송하지 못하고 연결이 종료된 경우 백엔드는 BOT 답변 메시지를 저장하지 않는다.
- AI 서버의 `answer_completed`에 `messageType`, `content`, `documents`, `recommendedContacts` 중 필수 값이 누락된 경우 백엔드는 AI 응답 형식 오류로 처리한다.

---

### 7-7. 내부 AI 동작 규칙

- 백엔드는 질문 메시지를 먼저 저장한 뒤 AI 서버를 호출한다.
- AI 서버 호출 endpoint는 `POST /chat/stream`을 사용한다.
- 내부 AI 요청에는 질문 저장 결과 전체 객체를 전달하지 않고, 답변 생성에 필요한 값만 전달한다.
- 내부 AI 요청에 포함되는 값은 `questionId`, `user`, `content`이다.
- `user.userId`는 사용자별 대화 맥락 식별에 사용할 수 있다.
- `user.name`은 개인화된 답변 생성에 사용할 수 있다.
- `user.companyCode`는 회사별 문서 범위 판별에 사용한다.
- `user.hireDate`는 입사일 기준 온보딩 맥락 판단 및 개인화 답변 생성에 사용한다.
- AI 서버는 `user.companyCode`를 기준으로 해당 회사 문서와 공통 문서만 조회 대상으로 사용해야 한다.
- AI 서버는 `user.hireDate`를 기준으로 입사 후 경과일, 온보딩 단계, 신입 사용자 상황을 답변 맥락에 반영할 수 있다.
- AI 서버는 질문 내용에 대해 답변 조각을 `answer_delta` 이벤트로 반환한다.
- 백엔드는 AI 서버의 `answer_delta`를 프론트엔드에 `answer_delta`로 중계한다.
- AI 서버는 답변 생성 완료 시 `answer_completed` 이벤트를 반드시 전송한다.
- AI 서버의 `answer_completed`에는 `questionId`, `messageType`, `content`, `documents`, `recommendedContacts`가 포함되어야 한다.
- 백엔드는 `answer_completed.content`를 기준으로 최종 BOT 답변 메시지를 저장한다.
- 백엔드는 `answer_completed.messageType`을 기준으로 답변 메시지 유형을 저장한다.
- 백엔드는 `answer_completed.documents[].documentId`를 기준으로 `chat_message_documents`에 답변 메시지와 근거 문서를 연결 저장한다.
- 백엔드는 `answer_completed.recommendedContacts`를 담당자 추천 응답 구성 또는 저장에 사용한다.
- AI 서버가 `answer_completed`를 전송하지 못하거나 필수 메타데이터가 누락된 경우 해당 요청은 실패로 처리한다.
- `suggestion`은 온보딩 가이드 기반 메시지 유형이므로 내부 AI 답변 응답값으로 사용하지 않는다.

---

## 8. Documents

이 항목은 문서 업로드, 조회, 다운로드, 삭제, 백업 재시도 등 스토리지 문서 관리 기능에 대한 API를 설명한다.
이 섹션의 엔드포인트는 현재 Swagger UI 기준으로 확인된 항목을 정리한 것이며, 요청/응답의 상세 스키마는 실제 Swagger UI 정의를 우선 기준으로 한다.

문서 관리 API는 고객사 관리자(`ADMIN`)만 호출할 수 있다.
일반 사용자(`USER`)와 제품 내부 관리자(`SERVICE_ADMIN`)는 문서 관리 API를 호출할 수 없다.

#### 문서 API 에러 코드

문서 API는 공통 에러 응답 형식을 사용하며, 실제 backend 구현 기준으로 아래 코드가 추가로 반환될 수 있다.

| 코드 | HTTP 상태 | 발생 조건 |
|---|---:|---|
| `FILE_001` | 400 | 업로드 파일이 비어 있거나 파일 크기 제한을 초과한 경우 |
| `FILE_002` | 400 | 지원하지 않는 파일 확장자인 경우 |
| `FILE_003` | 500 | 파일 읽기, 원본 스토리지 업로드, DB 저장 등 스토리지 처리에 실패한 경우 |
| `RESOURCE_004` | 403 | 다른 회사 문서에 접근하려는 경우 |
| `USER_NOT_FOUND` | 401 | JWT 인증은 통과했지만 사용자 정보를 DB에서 찾을 수 없는 경우 |

### 8-1. 문서 업로드

```http
POST /api/v1/documents/upload
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

#### 설명
- 문서를 업로드한다.
- 업로드된 문서는 `documents`, `document_files`를 기준으로 저장 및 관리한다.
- 파일 저장 후 백업 스토리지 연동 정책에 따라 백업 작업이 수행될 수 있다.

### 8-2. 문서 목록 조회

```http
GET /api/v1/documents
Authorization: Bearer {accessToken}
```
#### 설명
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

#### 설명
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
- Response Body는 파일 바이너리다.
- 이 API는 실제 파일 바이너리를 직접 반환하는 최종 다운로드 엔드포인트다.
- 로컬 개발 환경에서는 프론트엔드가 이 경로를 직접 호출하여 파일을 내려받을 수 있다.
- 운영 환경에서는 일반적으로 프론트엔드가 `GET /api/v1/documents/{documentId}/download`를 먼저 호출하고, 서버가 반환한 다운로드 URL 또는 접근 경로를 사용한다.

#### Error Response (404 Not Found)
```json
{
  "timestamp": "2026-04-14T15:30:00",
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
  "timestamp": "2026-04-14T15:30:00",
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

### 8-5. 다운로드 URL 발급
```http
GET /api/v1/documents/{documentId}/download
Authorization: Bearer {accessToken}
```

채팅 메시지 응답의 `documents[].file.downloadUrl`은 현재 구현 기준 아래 경로를 사용한다.

```http
GET /api/v1/chat/documents/{documentId}/download
Authorization: Bearer {accessToken}
```

| 필드           | 타입     | 필수 | 설명                   |
| ------------ | ------ | -- | -------------------- |
| `documentId` | `Long` | Y  | 다운로드 URL 발급 대상 문서 ID |

#### 동작 규칙

- `documents.id = {documentId}`인 문서를 조회한다.
- 해당 문서가 현재 로그인한 사용자의 회사 문서이거나 공통 문서인 경우만 접근 가능하다.
- `/api/v1/chat/documents/{documentId}/download` 경로는 현재 로그인한 사용자의 채팅 답변에 연결된 문서만 접근 가능하다.
- 채팅 문서 다운로드 경로에서 `chat_message_documents` 연결 이력이 없으면 `403 Forbidden`, `FORBIDDEN`을 반환한다.
- `document_type = TEMPLATE`인 경우에만 다운로드 URL 또는 접근 경로를 반환한다.
- 파일 메타데이터 및 실제 저장 위치는 `document_files`를 기준으로 조회한다.
- 운영 환경에서는 스토리지 기반 다운로드 URL 또는 파일 접근 경로를 반환할 수 있다.
- 로컬 개발 환경에서는 `/api/v1/documents/{documentId}/file` 경로를 다운로드 URL로 반환할 수 있다.
- `document_type != TEMPLATE`인 경우에는 정책에 따라 `400 Bad Request` 또는 `404 Not Found`를 반환한다.

#### Response (200 OK)
```json
{
  "downloadUrl": "/api/v1/documents/11/file"
}
```
#### 설명
- 프론트엔드는 TEMPLATE 문서 다운로드 시 이 API를 우선 호출한다.
- 응답의 `downloadUrl` 값을 사용해 실제 파일 다운로드를 진행한다.
- 채팅 메시지 응답의 `documents[].file.downloadUrl`에는 구현상 채팅 문서 다운로드 경로(`/api/v1/chat/documents/{documentId}/download`)를 제공한다.
- 실제 파일 바이너리 반환은 `downloadUrl`이 가리키는 경로에서 수행된다.

#### Error Response (404 Not Found)
```json
{
  "timestamp": "2026-04-14T15:30:00",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "errors": [
    {
      "field": "documentId",
      "message": "해당 문서를 찾을 수 없습니다."
    }
  ],
  "path": "/api/v1/documents/11/download"
}
```

#### Error Response (400 Bad Request)
```json
{
  "timestamp": "2026-04-14T15:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "errors": [
    {
      "field": "documentType",
      "message": "다운로드 URL 발급은 TEMPLATE 문서에만 허용됩니다."
    }
  ],
  "path": "/api/v1/documents/11/download"
}
```

#### Error Response (403 Forbidden)
```json
{
  "timestamp": "2026-05-13T10:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "code": "FORBIDDEN",
  "errors": [
    {
      "field": "documentId",
      "message": "채팅에서 수신하지 않은 문서입니다."
    }
  ],
  "path": "/api/v1/chat/documents/11/download"
}
```

### 8-6. 문서 삭제 전 검증
```http
GET /api/v1/documents/{documentId}/delete-check
Authorization: Bearer {accessToken}
```

#### 설명
- 특정 문서를 삭제하기 전에 삭제 가능 여부를 검증한다.
- 연관 데이터, 백업 상태, 권한 조건 등을 확인한다.

### 8-7. 문서 삭제
```http
DELETE /api/v1/documents/{documentId}
Authorization: Bearer {accessToken}
```

#### 설명
- 특정 문서를 삭제한다.
- 실제 삭제 방식은 물리 삭제 또는 논리 삭제 정책을 따른다.
- 파일 및 백업 데이터 처리 방식은 서버 정책에 따른다.

### 8-8. 문서 전체 삭제 전 검증
```http
GET /api/v1/documents/delete-check
Authorization: Bearer {accessToken}
```

#### 설명
- 전체 문서 삭제 전에 삭제 가능 여부를 검증한다.
- 삭제 대상 수, 삭제 불가 사유, 확인 필요 항목 등을 반환한다.

### 8-9. 문서 전체 삭제
```http
DELETE /api/v1/documents
Authorization: Bearer {accessToken}
```

#### 설명
- 전체 문서를 삭제한다.
- confirm 필요 정책이 적용된다.
- 실제 요청 규격은 Swagger UI 정의를 따른다.

### 8-10. 문서 선택 삭제 전 검증
```http
POST /api/v1/documents/bulk-delete/delete-check
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 설명
- 선택한 문서들을 삭제하기 전에 삭제 가능 여부를 검증한다.
- 삭제 대상 목록 기준으로 검증 결과를 반환한다.

### 8-11. 문서 선택 삭제
```http
POST /api/v1/documents/bulk-delete
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 설명
- 선택한 문서들을 일괄 삭제한다.
- confirm 필요 정책이 적용될 수 있다.
- 실제 요청 바디 구조는 Swagger UI 정의를 따른다.

### 8-12. 백업 재시도
```http
POST /api/v1/documents/{documentId}/backup/retry
Authorization: Bearer {accessToken}
```

#### 설명
- 특정 문서의 백업 작업을 재시도한다.
- `document_files`, `document_backup_jobs` 기준으로 백업 상태를 갱신하거나 새 작업을 생성한다.

---

## 9. 관리 지표

이 항목은 회사별 핵심 지표 집계 API를 설명한다.  
`users`, `chat_messages`, `user_activity_logs` 데이터를 기준으로 계산하며, 집계 쿼리와 조회 API만 구현한다.

### 9-1. 관리자 지표 개요

| 구분 | 지표                  |
|---|---------------------|
| 북극성 | D+6 RAG 답변 수신 경험률   |
| 보조 1 | D+0 첫 인터랙션 발생률      |
| 보조 2 | D+6 재방문률            |
| 가드레일 | 미답변 비율              |
| 가드레일 | TTA, 최초 로그인 → 첫 RAG |

| API | 설명                               |
|---|----------------------------------|
| `GET /api/v1/admin/metrics/rag-experience-rate` | 회사별 D+6 RAG 답변 수신 경험률            |
| `GET /api/v1/admin/metrics/first-interaction-rate` | 회사별 D+0 첫 인터랙션 발생률               |
| `GET /api/v1/admin/metrics/revisit-rate` | 회사별 D+6 재방문률                     |
| `GET /api/v1/admin/metrics/unanswered-rate` | 회사별 미답변 비율                       |
| `GET /api/v1/admin/metrics/tta` | 회사별 평균 TTA, 최초 로그인 → 첫 RAG 소요 시간 |

#### 공통 인증 및 권한 규칙

- 이 API는 제품 지표 확인을 위해 사용하는 내부 관리자(`SERVICE_ADMIN`) API다.
- 고객사 관리자 계정 페이지 API(`/api/v1/admin/users`)와 권한 목적이 다르다.
- 일반 사용자(`USER`)와 고객사 관리자(`ADMIN`)는 호출할 수 없다.
- 권한이 없는 사용자가 호출하면 `403 Forbidden`을 반환한다.
- 인증 오류와 세션 만료/무효화 처리 방식은 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

#### 공통 Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `companyCode` | String | N | 특정 회사만 조회할 때 사용한다. 생략하면 회사별 전체 집계 결과를 반환한다. |
| `asOfDate` | String (`yyyy-MM-dd`) | N | 집계 기준일. 생략하면 KST 기준 오늘 날짜를 사용한다. |

#### 공통 산정 규칙

- 날짜 계산 기준은 **Asia/Seoul(KST)** 로 한다.
- `D+0`은 `DATEDIFF(이벤트 발생일, users.hire_date) = 0`인 경우를 의미한다.
- `D+6` 지표는 입사일을 포함한 7일 관찰 기간을 기준으로 하며, 세부 포함 범위는 각 지표의 집계 기준을 따른다.
- 비율은 `numerator / denominator * 100`으로 계산한다.
- 소수점은 기본적으로 소수점 첫째 자리까지 반올림한다.
- 분모가 0인 경우 비율은 `0.0`으로 반환한다.
- 회사별 집계 결과는 `companies` 배열로 반환한다.
- 집계 대상 사용자는 기본적으로 `users.role = USER`인 일반 사용자, 즉 신입 사용자로 한정한다.

---

### 9-2. 북극성: D+6 RAG 답변 수신 경험률

입사 후 7일 이내에 `rag_answer` 메시지를 1건 이상 수신한 신입 사용자 비율을 회사별로 조회한다.

```http
GET /api/v1/admin/metrics/rag-experience-rate
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `companyCode` | String | N | 특정 회사만 조회할 때 사용한다. |
| `asOfDate` | String (`yyyy-MM-dd`) | N | 집계 기준일. 생략하면 KST 기준 오늘 날짜를 사용한다. |

#### Response (200 OK)

```json
{
  "metric": "rag_experience_rate",
  "asOfDate": "2026-05-06",
  "companies": [
    {
      "companyCode": "WB0001",
      "companyName": "테크 주식회사",
      "targetUsers": 20,
      "ragReceivedUsers": 15,
      "ragExperienceRate": 75.0
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명                                       |
|---|---|------------------------------------------|
| `metric` | String | 지표 식별자. `rag_experience_rate`            |
| `asOfDate` | String | 집계 기준일                                   |
| `companies` | Array | 회사별 집계 결과                                |
| `companies[].companyCode` | String | 회사 코드                                    |
| `companies[].companyName` | String | 회사명                                      |
| `companies[].targetUsers` | Number | D+6 RAG 경험률 산정 대상 사용자 수                  |
| `companies[].ragReceivedUsers` | Number | 입사 후 7일 이내 `rag_answer`를 1건 이상 수신한 사용자 수 |
| `companies[].ragExperienceRate` | Number | D+6 RAG 답변 수신 경험률, 단위 `%`                |

#### 집계 기준

- `chat_messages.message_type = rag_answer`인 BOT 메시지를 기준으로 계산한다.
- `chat_messages.sender_type = BOT`인 메시지만 AI 답변으로 본다.
- `DATEDIFF(chat_messages.created_at, users.hire_date) BETWEEN 0 AND 6` 조건을 만족하는 `rag_answer`가 1건 이상 있으면 RAG 답변 수신 경험 사용자로 계산한다.
- `asOfDate` 기준으로 D+6 산정 기간이 완료된 사용자만 기본 분모에 포함한다.
- 즉 `DATEDIFF(:asOfDate, users.hire_date) >= 6`인 사용자만 기본 산정 대상이다.
- 회사별로 `companies.company_code` 또는 `users.company_code` 기준으로 그룹화한다.

---

### 9-3. D+0 첫 인터랙션 발생률

입사 당일에 빠른 질문 버튼 클릭 또는 직접 질문을 1회 이상 수행한 신입 사용자 비율을 회사별로 조회한다.

```http
GET /api/v1/admin/metrics/first-interaction-rate
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `companyCode` | String | N | 특정 회사만 조회할 때 사용한다. |
| `asOfDate` | String (`yyyy-MM-dd`) | N | 집계 기준일. 생략하면 KST 기준 오늘 날짜를 사용한다. |

#### Response (200 OK)

```json
{
  "metric": "first_interaction_rate",
  "asOfDate": "2026-05-06",
  "companies": [
    {
      "companyCode": "WB0001",
      "companyName": "테크 주식회사",
      "targetUsers": 20,
      "firstInteractionUsers": 16,
      "firstInteractionRate": 80.0
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `metric` | String | 지표 식별자. `first_interaction_rate` |
| `asOfDate` | String | 집계 기준일 |
| `companies` | Array | 회사별 집계 결과 |
| `companies[].companyCode` | String | 회사 코드 |
| `companies[].companyName` | String | 회사명 |
| `companies[].targetUsers` | Number | 첫 인터랙션 발생률 산정 대상 사용자 수 |
| `companies[].firstInteractionUsers` | Number | D+0에 첫 인터랙션이 발생한 사용자 수 |
| `companies[].firstInteractionRate` | Number | D+0 첫 인터랙션 발생률, 단위 `%` |

#### 집계 기준

- 첫 인터랙션은 아래 둘 중 하나가 D+0에 1회 이상 발생한 경우로 본다.
  - `user_activity_logs.event_type = BUTTON_CLICK`
  - `chat_messages.message_type = user_question`
- `BUTTON_CLICK`은 `user_activity_logs`를 기준으로 확인한다.
- 직접 질문은 `chat_messages.sender_type = USER`, `chat_messages.message_type = user_question`을 기준으로 확인한다.
- `DATEDIFF(이벤트 발생일, users.hire_date) = 0`인 이벤트만 D+0 첫 인터랙션으로 계산한다.
- 동일 사용자가 D+0에 여러 번 클릭하거나 여러 번 질문해도 사용자 수는 1명으로 계산한다.

---

### 9-4. D+6 재방문률

D+0에 MyBuddy 채팅 화면에 진입한 신입 중, D+1부터 D+6 사이에 1회 이상 다시 진입한 사용자 비율을 회사별로 조회한다.

```http
GET /api/v1/admin/metrics/revisit-rate
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `companyCode` | String | N | 특정 회사만 조회할 때 사용한다. |
| `asOfDate` | String (`yyyy-MM-dd`) | N | 집계 기준일. 생략하면 KST 기준 오늘 날짜를 사용한다. |

#### Response (200 OK)

```json
{
  "metric": "revisit_rate",
  "asOfDate": "2026-05-06",
  "companies": [
    {
      "companyCode": "WB0001",
      "companyName": "테크 주식회사",
      "d0Users": 18,
      "revisitUsers": 12,
      "revisitRate": 66.7
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명                            |
|---|---|-------------------------------|
| `metric` | String | 지표 식별자. `revisit_rate`        |
| `asOfDate` | String | 집계 기준일                        |
| `companies` | Array | 회사별 집계 결과                     |
| `companies[].companyCode` | String | 회사 코드                         |
| `companies[].companyName` | String | 회사명                           |
| `companies[].d0Users` | Number | D+0에 MyBuddy 채팅 화면에 진입한 사용자 수 |
| `companies[].revisitUsers` | Number | D+1~D+6 사이에 1회 이상 재진입한 사용자 수  |
| `companies[].revisitRate` | Number | D+6 재방문률, 단위 `%`              |

#### 집계 기준

- 화면 진입은 `user_activity_logs.event_type = SESSION_START`, `event_target = CHAT` 로그를 기준으로 계산한다.
- 분모는 D+0에 MyBuddy 채팅 화면에 진입한 사용자 수다.
- 분자는 분모 사용자 중 D+1~D+6 사이에 `SESSION_START + CHAT` 로그가 1건 이상 있는 사용자 수다.
- `asOfDate` 기준으로 D+6 산정 기간이 완료된 사용자만 분모에 포함한다.
- 즉 `DATEDIFF(:asOfDate, users.hire_date) >= 6`인 사용자만 기본 산정 대상이다.
- D+0에 접속하지 않고 D+1 이후 처음 접속한 사용자는 재방문률 분모에 포함하지 않는다.
- `POST /api/v1/chat/session-start`의 30분 중복 제외 정책 때문에 동일 사용자의 짧은 시간 내 반복 진입은 중복 집계되지 않는다.
- 별도 `session_id` 없이 기존 `user_activity_logs` 데이터만으로 집계한다.

---

### 9-5. 미답변 비율

전체 AI 답변 중 `no_result` 또는 `out_of_scope`로 종료된 답변 비율을 회사별로 조회한다.

```http
GET /api/v1/admin/metrics/unanswered-rate
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `companyCode` | String | N | 특정 회사만 조회할 때 사용한다. |
| `asOfDate` | String (`yyyy-MM-dd`) | N | 집계 기준일. 생략하면 KST 기준 오늘 날짜를 사용한다. |

#### Response (200 OK)

```json
{
  "metric": "unanswered_rate",
  "asOfDate": "2026-05-06",
  "companies": [
    {
      "companyCode": "WB0001",
      "companyName": "테크 주식회사",
      "totalAiAnswers": 120,
      "unansweredAnswers": 18,
      "unansweredRate": 15.0
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `metric` | String | 지표 식별자. `unanswered_rate` |
| `asOfDate` | String | 집계 기준일 |
| `companies` | Array | 회사별 집계 결과 |
| `companies[].companyCode` | String | 회사 코드 |
| `companies[].companyName` | String | 회사명 |
| `companies[].totalAiAnswers` | Number | 전체 AI 답변 수 |
| `companies[].unansweredAnswers` | Number | 미답변 처리된 AI 답변 수 |
| `companies[].unansweredRate` | Number | 미답변 비율, 단위 `%` |

#### 집계 기준

- 전체 AI 답변은 `chat_messages.sender_type = BOT`이고 `message_type IN (rag_answer, no_result, out_of_scope)`인 메시지 수다.
- 미답변은 `message_type IN (no_result, out_of_scope)`인 BOT 메시지 수다.
- `suggestion` 메시지는 온보딩 제안 메시지이므로 전체 AI 답변 수와 미답변 수에서 제외한다.
- `user_question` 메시지는 사용자 입력이므로 전체 AI 답변 수와 미답변 수에서 제외한다.

---

### 9-6. 평균 TTA, 최초 로그인 → 첫 RAG

사용자가 최초 로그인한 시점부터 첫 `rag_answer`를 수신하기까지 걸린 평균 시간을 분 단위로 회사별 조회한다.

```http
GET /api/v1/admin/metrics/tta
Authorization: Bearer {accessToken}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `companyCode` | String | N | 특정 회사만 조회할 때 사용한다. |
| `asOfDate` | String (`yyyy-MM-dd`) | N | 집계 기준일. 생략하면 KST 기준 오늘 날짜를 사용한다. |

#### Response (200 OK)

```json
{
  "metric": "tta",
  "asOfDate": "2026-05-06",
  "unit": "minutes",
  "companies": [
    {
      "companyCode": "WB0001",
      "companyName": "테크 주식회사",
      "loggedInUsers": 20,
      "measuredUsers": 15,
      "averageTtaMinutes": 42.6
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `metric` | String | 지표 식별자. `tta` |
| `asOfDate` | String | 집계 기준일 |
| `unit` | String | 측정 단위. `minutes` |
| `companies` | Array | 회사별 집계 결과 |
| `companies[].companyCode` | String | 회사 코드 |
| `companies[].companyName` | String | 회사명 |
| `companies[].loggedInUsers` | Number | 최초 로그인 기록이 있는 사용자 수 |
| `companies[].measuredUsers` | Number | 최초 로그인과 첫 RAG 답변 수신 기록이 모두 있는 사용자 수 |
| `companies[].averageTtaMinutes` | Number 또는 null | 회사별 평균 TTA. 측정 대상이 없으면 `null` |

#### 집계 기준

- 최초 로그인은 `user_activity_logs.event_type = SESSION_START`, `event_target = LOGIN`의 최소 `created_at`으로 계산한다.
- 첫 RAG 답변 수신은 `chat_messages.sender_type = BOT`, `message_type = rag_answer`의 최소 `created_at`으로 계산한다.
- 사용자별 TTA는 `TIMESTAMPDIFF(MINUTE, 최초 로그인 시각, 첫 RAG 답변 시각)`으로 계산한다.
- 평균 TTA는 최초 로그인과 첫 RAG 답변이 모두 존재하는 사용자만 대상으로 계산한다.
- 첫 RAG 답변이 없는 사용자는 `measuredUsers`와 평균 계산에서 제외한다.
- 첫 RAG 답변 시각이 최초 로그인 시각보다 빠른 비정상 데이터는 평균 계산에서 제외한다.

---

### 9-7. 관리자 지표 API 공통 에러 처리

#### Error Response (401 Unauthorized)

인증 헤더가 없거나, 토큰이 유효하지 않거나, 세션이 만료 또는 무효화된 경우 반환한다.
세부 에러 코드는 **5-4. 인증 오류 및 세션 만료/무효화 처리**를 따른다.

```json
{
  "timestamp": "2026-05-06T09:30:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "INVALID_TOKEN",
  "errors": [
    {
      "field": "token",
      "message": "인증 정보가 유효하지 않습니다."
    }
  ],
  "path": "/api/v1/admin/metrics/rag-experience-rate"
}
```

#### Error Response (403 Forbidden)

`SERVICE_ADMIN` 권한이 없는 사용자가 호출한 경우 반환한다.

```json
{
  "timestamp": "2026-05-06T09:30:00",
  "status": 403,
  "error": "Forbidden",
  "code": "ACCESS_DENIED",
  "errors": [
    {
      "field": "role",
      "message": "관리자 권한이 필요한 API입니다."
    }
  ],
  "path": "/api/v1/admin/metrics/rag-experience-rate"
}
```

#### Error Response (400 Bad Request)

쿼리 파라미터 형식이 올바르지 않은 경우 반환한다.

```json
{
  "timestamp": "2026-05-06T09:30:00",
  "status": 400,
  "error": "Bad Request",
  "code": "BAD_REQUEST",
  "errors": [
    {
      "field": "asOfDate",
      "message": "집계 기준일은 yyyy-MM-dd 형식이어야 합니다."
    }
  ],
  "path": "/api/v1/admin/metrics/rag-experience-rate"
}
```

---

## 10. 변경 이력

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
- **v1.7.5 (2026-04-16)**:
  - `documents` 오타 수정, 내부 AI 응답 시간 초과 처리 규칙 추가, `504 Gateway Timeout`, `AI_TIMEOUT` 에러 코드 추가
- **v1.7.6 (2026-04-20)**:
  - 온보딩 제안 조회 API의 `dayOffset` 기준을 입사일 당일 `D+0` 기준으로 수정하고, 응답 필드 및 노출 규칙을 최신 구현 기준으로 정리
- **v1.7.7 (2026-04-23)**:
  - `no_result` 메시지에 대한 담당자 추천 카드 응답 구조 추가, 채팅 메시지 목록 조회 및 질문 전송 응답 예시에 추천 담당자 정보 반영, 내부 AI 응답 규격에 추천 담당자 정보 반영
- **v1.7.8 (2026-04-27)**:
  - 내부 AI 답변 생성 요청(`/internal/ai/answer`)에 이전 대화 이력 `conversationHistory` 전달 규칙 추가
- **v1.7.9 (2026-04-28)**:
  - 신입 계정 생성 API(`POST /api/v1/users`) 추가, 동일 회사 내 사원번호 중복 방지를 위한 `company_id + employee_number` 복합 UNIQUE 제약조건 및 `409 Conflict`, `DUPLICATE_EMPLOYEE_NUMBER` 에러 응답 규격 추가
- **v1.8.0 (2026-04-29)**:
  - 온보딩 제안 조회 응답에 D-day별 빠른 질문 버튼(`quickTaps`) 추가, 빠른 질문 목록 및 클릭 로그 응답 형식 수정
- **v1.9.0 (2026-05-05)**:
  - 질문 전송 API를 SSE 스트리밍 방식으로 변경하고, 백엔드가 AI 서버 `/chat/stream` 응답을 중계하도록 내부 연동 규격을 수정했다. AI 답변 조각은 `answer_delta`로 실시간 전달하며, 최종 답변 저장 및 문서/담당자 정보는 `answer_completed` 기준으로 처리한다.
  - AI 서버 `/chat/stream` 요청 body의 `user` 객체에 `hireDate`를 추가
- **v1.9.1 (2026-05-06)**:
  - 관리자용 서비스 측정 지표 조회 API 추가
- **v1.9.2 (2026-05-07)**:
  - 인증 에러 코드 규격을 수정
- **v1.9.3 (2026-05-08)**:
  - 관리자 계정 경로 설정 및 로그인 응답에 `role` 추가, 내부 지표 API의 경로·권한 규격을 분리 정리
- **v1.9.4 (2026-05-12)**:
  - 로그아웃 API(`POST /api/v1/auth/logout`) 명세 추가, JWT/Redis 세션 정책과 Redis 삭제 키 목록 정리, 세션 저장소 장애(`SESSION_STORE_UNAVAILABLE`) 응답 규격 추가
