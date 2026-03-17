# WithBuddy API 명세서

> WithBuddy MVP 기준 REST API 문서

**버전**: 2.1.0  
**최종 업데이트**: 2026-03-17

---

## 📋 목차

- [문서 범위](#문서-범위)
- [API 개요](#api-개요)
- [멀티 테넌시](#멀티-테넌시)
- [인증 (Authentication)](#인증-authentication)
- [사용자 (User)](#사용자-user)
- [AI 도우미 (AI Assistant)](#ai-도우미-ai-assistant)
- [체크리스트 (Checklist)](#체크리스트-checklist)
- [참고 자료](#참고-자료)

---

## 문서 범위

이 문서는 **MVP 개발 범위에서 현재 구현했거나 즉시 구현 대상으로 확정된 API만** 다룹니다.

- 추후 변경 가능성이 큰 기능
- 아직 개발 범위에 포함되지 않은 기능
- 응답 스키마에 아직 반영되지 않은 커스텀 에러코드

위 항목은 [`docs/api/PLANNED_API.md`](PLANNED_API.md)에서 별도로 관리합니다.

---

## API 개요

### Base URL

```
Development: http://localhost:8080
Production:  https://api.withbuddy.com
```

### API 버전 관리

모든 API는 버전 관리를 위해 `/api/v1/` prefix를 사용합니다.

```
/api/v1/auth/login
/api/v1/auth/refresh
/api/v1/users/me
/api/v1/ai/chat
/api/v1/checklists?week=1
```

### 공통 헤더

```http
Content-Type: application/json
Authorization: Bearer {access_token}
```

### 표준 응답 형식

**성공 응답**:
```json
{
  "id": "uuid-string",
  "companyCode": 1001,
  "companyName": "테크 주식회사",
  "name": "김지원",
  "employeeNumber": "20260001"
}
```

**에러 응답**:
```json
{
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "사원번호는 8자리 숫자여야 합니다",
  "path": "/api/v1/auth/login"
}
```

### HTTP 상태 코드

- `200 OK`: 성공
- `201 Created`: 리소스 생성 성공
- `204 No Content`: 성공 (응답 본문 없음)
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 리소스 충돌
- `500 Internal Server Error`: 서버 오류

> 커스텀 에러코드는 실제 응답에 포함되기 전까지 [`docs/api/PLANNED_API.md`](PLANNED_API.md)에서 관리합니다.

---

## 멀티 테넌시

### 개요

WithBuddy는 **여러 회사가 동시에 사용하는 SaaS 서비스**입니다.

#### 핵심 개념
- 각 회사는 고유한 `companyCode`로 식별
- 회사별 데이터는 분리 관리
- 같은 사원번호를 다른 회사에서 사용할 수 있음
- 모든 API 요청은 로그인한 회사 기준으로 데이터가 조회/저장됨

### JWT 토큰 구조

```json
{
  "sub": "user-uuid-123",
  "companyCode": 1001,
  "companyId": 1,
  "employeeNumber": "20260001",
  "name": "김지원",
  "role": "EMPLOYEE",
  "iat": 1234567890,
  "exp": 1234574890
}
```

**필드 설명**:
- `sub`: 사용자 고유 ID (UUID)
- `companyCode`: 회사 코드
- `companyId`: 회사 내부 ID (데이터베이스 FK)
- `employeeNumber`: 사원번호
- `name`: 사용자 이름
- `role`: 사용자 역할 식별값
- `iat`: 발급 시간 (Unix timestamp)
- `exp`: 만료 시간 (Unix timestamp)

> 세부 권한 분할 정책은 [`docs/api/PLANNED_API.md`](PLANNED_API.md)에서 관리합니다.

### 데이터 격리

모든 API는 JWT 토큰에서 `companyId`를 추출하여 자동으로 필터링합니다.

**예시 1: 주차별 체크리스트 조회**
```http
GET /api/v1/checklists?week=1
Authorization: Bearer {token}
```

**예시 2: AI 채팅**
```http
POST /api/v1/ai/chat
Authorization: Bearer {token}
Content-Type: application/json
```

---

## 인증 (Authentication)

### 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json
```

**Request Body**
```json
{
  "companyCode": 1001,
  "employeeNumber": "20260001",
  "name": "김지원"
}
```

**필드 설명**:
- `companyCode`: 회사 고유 코드 (4자리 숫자)
- `employeeNumber`: 사원번호 (8자리 숫자)
- `name`: 사용자 이름 (추가 검증용)

**Response (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "uuid",
    "companyCode": 1001,
    "companyName": "테크 주식회사",
    "employeeNumber": "20260001",
    "name": "김지원",
    "department": "개발팀",
    "position": "사원",
    "role": "EMPLOYEE"
  }
}
```

**Error Response (401 Unauthorized)**
```json
{
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "회사코드, 사원번호 또는 이름이 올바르지 않습니다",
  "path": "/api/v1/auth/login"
}
```

**Error Response (404 Not Found) - 잘못된 회사코드**
```json
{
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "존재하지 않는 회사입니다",
  "path": "/api/v1/auth/login"
}
```

---

### 토큰 재발급

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=eyJhbGciOiJIUzI1NiIs...
```

**Response (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Error Response (401 Unauthorized)**
```json
{
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "토큰이 만료되었습니다",
  "path": "/api/v1/auth/refresh"
}
```

- **[토큰 발급](./AUTH_GUIDE.md)** - API 인증 및 토큰 가이드

> 회원가입, 로그아웃은 [`docs/api/PLANNED_API.md`](PLANNED_API.md)에서 관리합니다.

---

## 사용자 (User)

### 내 정보 조회

```http
GET /api/v1/users/me
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "uuid",
  "companyCode": 1001,
  "companyName": "테크 주식회사",
  "employeeNumber": "20260001",
  "name": "김지원",
  "department": "개발팀",
  "position": "사원",
  "role": "EMPLOYEE",
  "joinDate": "2026-03-01",
  "profileImage": "https://storage.../profile.jpg",
  "onboardingProgress": 45.5,
  "mentor": {
    "id": "mentor-uuid",
    "name": "이멘토",
    "department": "개발팀",
    "position": "시니어"
  }
}
```

> 내 정보 수정, 회사 내 사용자 목록 조회는 [`docs/api/PLANNED_API.md`](PLANNED_API.md)에서 관리합니다.

---

## AI 도우미 (AI Assistant)

### 질문하기 (AI 채팅)

```http
POST /api/v1/ai/chat
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**
```json
{
  "message": "복지카드는 어떻게 신청하나요?",
  "conversationId": "uuid"
}
```

- `conversationId`: 선택 사항. 기존 대화를 이어가려면 전달, 새 대화는 null

**Response (200 OK)**
```json
{
  "conversationId": "uuid",
  "answer": "복지카드는 인사팀 포털에서 신청할 수 있습니다. 1. 인사 포털 로그인 2. 복지카드 메뉴 클릭...",
  "relatedDocuments": [
    {
      "id": "doc-001",
      "title": "복지카드 신청 가이드",
      "url": "/api/v1/documents/doc-001"
    }
  ],
  "timestamp": "2026-03-17T10:30:00Z"
}
```

**중요**: AI는 자동으로 **로그인한 사용자의 회사 문서**만 검색합니다.

> 대화 히스토리 조회, 특정 대화 조회, 대화 삭제는 [`docs/api/PLANNED_API.md`](PLANNED_API.md)에서 관리합니다.

---

## 체크리스트 (Checklist)

### 주차별 체크리스트 조회

```http
GET /api/v1/checklists?week=1
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "week": 1,
  "title": "1주차 온보딩",
  "description": "첫 주에는 회사와 팀에 적응하는 시간입니다",
  "items": [
    {
      "id": "check-001",
      "title": "복지카드 신청",
      "description": "인사팀 포털에서 복지카드를 신청하세요",
      "category": "행정",
      "completed": true,
      "completedAt": "2026-03-02T14:00:00Z"
    }
  ],
  "progress": 50.0
}
```

---

### 체크리스트 항목 완료

```http
POST /api/v1/checklists/{checklistId}/complete
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "check-001",
  "completed": true,
  "completedAt": "2026-03-17T10:30:00Z"
}
```

---

### 체크리스트 항목 미완료

```http
POST /api/v1/checklists/{checklistId}/incomplete
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "check-001",
  "completed": false,
  "completedAt": null
}
```

> 전체 체크리스트 조회는 [`docs/api/PLANNED_API.md`](PLANNED_API.md)에서 관리합니다.

---

## 참고 자료

- [계획 API 문서](PLANNED_API.md)
- [아키텍처 문서](./architecture/ARCHITECTURE.md)
- [멀티 테넌시 문서](./MULTI_TENANCY.md)
- [환경변수 가이드](./ENV.md)
- [Swagger UI](http://localhost:8080/swagger-ui.html)

---

**문서 버전**: 2.1.0  
**작성일**: 2026-03-17  
**다음 리뷰 예정**: 2026-04-17

## 변경 이력

### v2.1.0 (2026-03-17)
- MVP 기준 구현 범위만 남기도록 문서 범위 재정리
- 미구현/변경 가능성이 큰 API를 `docs/api/PLANNED_API.md`로 분리
- 커스텀 에러코드, Rate Limiting, 페이지네이션 공통 정책을 계획 문서로 이동

### v2.0.0 (2026-03-17)
- 멀티 테넌시 구조 적용
- 로그인 API 변경 (companyCode + employeeNumber + name)
- 모든 응답에 companyCode, companyName 추가
- 회사 관리 API 추가
- 데이터 격리 설명 추가
- JWT 페이로드 구조 업데이트
- 비밀번호 제거 (사내 전용)

### v1.0.0 (2026-03-10)
- 초기 버전
