# WithBuddy API 명세서

> WithBuddy REST API 전체 엔드포인트 문서

**버전**: 2.0.0  
**최종 업데이트**: 2026-03-17

---

## 📋 목차

- [API 개요](#api-개요)
- [멀티 테넌시](#멀티-테넌시)
- [인증 (Authentication)](#인증-authentication)
- [회사 관리 (Company)](#회사-관리-company)
- [사용자 관리 (User)](#사용자-관리-user)
- [AI 도우미 (AI Assistant)](#ai-도우미-ai-assistant)
- [체크리스트 (Checklist)](#체크리스트-checklist)
- [기록 (Records)](#기록-records)
- [리포트 (Reports)](#리포트-reports)
- [문서 관리 (Documents)](#문서-관리-documents)
- [진행률 (Progress)](#진행률-progress)

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
/api/v1/users/me
/api/v1/ai/chat
/api/v1/companies/me
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
- `429 Too Many Requests`: Rate Limit 초과
- `500 Internal Server Error`: 서버 오류

---

## 멀티 테넌시

### 개요

WithBuddy는 **여러 회사가 동시에 사용하는 SaaS 서비스**입니다.

#### 핵심 개념
- 🏢 각 회사는 고유한 `companyCode`로 식별
- 🔒 회사별 데이터 완전 격리
- 👥 같은 사원번호를 다른 회사에서 사용 가능
- 📊 모든 API 요청은 자동으로 로그인한 회사의 데이터만 조회/수정

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
- `role`: 역할 (EMPLOYEE, MENTOR, MANAGER, HR, ADMIN)
- `iat`: 발급 시간 (Unix timestamp)
- `exp`: 만료 시간 (Unix timestamp)

### 데이터 격리

모든 API는 JWT 토큰에서 `companyId`를 추출하여 자동으로 필터링합니다.

**예시 1: 체크리스트 조회**
```http
GET /api/v1/checklists
Authorization: Bearer {token}  # companyId=1 포함

→ companyId=1인 체크리스트만 반환
```

**예시 2: 기록 작성**
```http
POST /api/v1/records
Authorization: Bearer {token}  # companyId=1 포함
Content-Type: application/json

{
  "title": "첫 주 회고",
  "content": "..."
}

→ 자동으로 companyId=1로 설정되어 저장
```

### 회사 구분 예시

| 회사 A (companyCode: 1001) | 회사 B (companyCode: 1002) |
|---------------------------|---------------------------|
| 사원번호: 20260001 (김지원) | 사원번호: 20260001 (박민수) |
| 체크리스트: 10개 | 체크리스트: 8개 |
| 기록: 25개 | 기록: 30개 |

→ 같은 사원번호지만 **완전히 별도의 사용자**

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

### 회원가입

```http
POST /api/v1/auth/signup
Content-Type: application/json
```

**Request Body**
```json
{
  "companyCode": 1001,
  "employeeNumber": "20260001",
  "name": "김지원",
  "department": "개발팀",
  "position": "사원",
  "joinDate": "2026-03-01"
}
```

**필드 설명**:
- `companyCode`: 회사 고유 코드 (필수)
- `employeeNumber`: 사원번호 (필수, 회사 내에서 고유)
- `name`: 이름 (필수)
- `department`: 부서 (선택)
- `position`: 직급 (선택)
- `joinDate`: 입사일 (선택)

**Response (201 Created)**
```json
{
  "id": "uuid",
  "companyCode": 1001,
  "companyName": "테크 주식회사",
  "employeeNumber": "20260001",
  "name": "김지원",
  "department": "개발팀",
  "position": "사원",
  "joinDate": "2026-03-01"
}
```

**Error Response (404 Not Found) - 잘못된 회사코드**
```json
{
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "존재하지 않는 회사입니다",
  "path": "/api/v1/auth/signup"
}
```

**Error Response (409 Conflict) - 사원번호 중복**
```json
{
  "timestamp": "2026-03-17T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "해당 회사에 이미 등록된 사원번호입니다",
  "path": "/api/v1/auth/signup"
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
---

### 로그아웃

```http
POST /api/v1/auth/logout
Authorization: Bearer {token}
```

**Response (204 No Content)**

---

## 회사 관리 (Company)

### 내 회사 정보 조회

```http
GET /api/v1/companies/me
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": 1,
  "companyCode": 1001,
  "companyName": "테크 주식회사",
  "industry": "IT",
  "active": true,
  "settings": {
    "onboardingWeeks": 12,
    "aiEnabled": true,
    "logoUrl": "https://storage.../logo.png",
    "primaryColor": "#3B82F6",
    "reportGenerationInterval": 1
  },
  "createdAt": "2024-01-01T00:00:00Z"
}
```

---

### 회사 설정 수정

```http
PATCH /api/v1/companies/me/settings
Authorization: Bearer {token}
Content-Type: application/json
```

**권한**: HR, ADMIN만 가능

**Request Body**
```json
{
  "onboardingWeeks": 12,
  "aiEnabled": true,
  "logoUrl": "https://storage.../new-logo.png",
  "primaryColor": "#10B981"
}
```

**Response (200 OK)**
```json
{
  "onboardingWeeks": 12,
  "aiEnabled": true,
  "logoUrl": "https://storage.../new-logo.png",
  "primaryColor": "#10B981",
  "updatedAt": "2026-03-17T10:30:00Z"
}
```

---

## 사용자 관리 (User)

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

---

### 내 정보 수정

```http
PATCH /api/v1/users/me
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**
```json
{
  "name": "김지원",
  "profileImage": "https://storage.../new-profile.jpg"
}
```

**Response (200 OK)**
```json
{
  "id": "uuid",
  "name": "김지원",
  "profileImage": "https://storage.../new-profile.jpg",
  "updatedAt": "2026-03-17T10:30:00Z"
}
```

---

### 회사 내 사용자 목록 조회

```http
GET /api/v1/users
Authorization: Bearer {token}
Query: department=개발팀&page=0&size=20
```

**권한**: MENTOR, MANAGER, HR, ADMIN

**Query Parameters**
- `department`: 부서 필터 (선택)
- `role`: 역할 필터 (선택)
- `page`: 페이지 번호 (default: 0)
- `size`: 페이지 크기 (default: 20)

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "uuid",
      "employeeNumber": "20260001",
      "name": "김지원",
      "department": "개발팀",
      "position": "사원",
      "role": "EMPLOYEE",
      "joinDate": "2026-03-01"
    }
  ],
  "totalElements": 45,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

**중요**: 자동으로 **로그인한 사용자의 회사(companyId)** 데이터만 반환됩니다.

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

---

### 대화 히스토리 조회

```http
GET /api/v1/ai/conversations
Authorization: Bearer {token}
Query: page=0&size=20
```

**Response (200 OK)**
```json
{
  "content": [
    {
      "conversationId": "uuid",
      "firstMessage": "복지카드는 어떻게 신청하나요?",
      "messageCount": 3,
      "createdAt": "2026-03-17T10:30:00Z",
      "updatedAt": "2026-03-17T10:35:00Z"
    }
  ],
  "totalElements": 25,
  "totalPages": 2,
  "size": 20,
  "number": 0
}
```

---

### 특정 대화 조회

```http
GET /api/v1/ai/conversations/{conversationId}
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "uuid",
  "messages": [
    {
      "role": "user",
      "content": "복지카드는 어떻게 신청하나요?",
      "timestamp": "2026-03-17T10:30:00Z"
    },
    {
      "role": "assistant",
      "content": "복지카드는 인사팀 포털에서...",
      "timestamp": "2026-03-17T10:30:05Z"
    }
  ],
  "createdAt": "2026-03-17T10:30:00Z"
}
```

---

### 대화 삭제

```http
DELETE /api/v1/ai/conversations/{conversationId}
Authorization: Bearer {token}
```

**Response (204 No Content)**

---

## 체크리스트 (Checklist)

### 전체 체크리스트 조회

```http
GET /api/v1/checklists
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "weeks": [
    {
      "week": 1,
      "title": "1주차 온보딩",
      "items": [
        {
          "id": "check-001",
          "title": "복지카드 신청",
          "description": "인사팀 포털에서 복지카드를 신청하세요",
          "category": "행정",
          "completed": true,
          "completedAt": "2026-03-02T14:00:00Z"
        },
        {
          "id": "check-002",
          "title": "개발환경 세팅",
          "description": "개발 환경을 구축하세요",
          "category": "기술",
          "completed": false,
          "completedAt": null
        }
      ],
      "progress": 50.0
    }
  ],
  "overallProgress": 15.5
}
```

**중요**: 로그인한 사용자의 체크리스트만 반환됩니다.

---

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

---

## 기록 (Records)

### 기록 작성

```http
POST /api/v1/records
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**
```json
{
  "title": "첫 주 회고",
  "content": "이번 주에는 개발환경을 세팅하고 팀원들과 첫 미팅을 했습니다...",
  "type": "WEEKLY_REVIEW",
  "tags": ["회고", "1주차"]
}
```

**Response (201 Created)**
```json
{
  "id": "record-001",
  "title": "첫 주 회고",
  "content": "이번 주에는 개발환경을 세팅하고...",
  "aiSummary": "이번 주 주요 활동: 개발환경 세팅 완료, 팀원들과 첫 미팅...",
  "type": "WEEKLY_REVIEW",
  "tags": ["회고", "1주차"],
  "createdAt": "2026-03-17T10:30:00Z"
}
```

**중요**: 자동으로 **로그인한 사용자의 companyId**로 저장됩니다.

---

### 기록 목록 조회

```http
GET /api/v1/records
Authorization: Bearer {token}
Query: type=WEEKLY_REVIEW&page=0&size=10
```

**Query Parameters**
- `type`: 기록 유형 (선택) - `DAILY`, `WEEKLY_REVIEW`, `MEMO`
- `startDate`: 시작일 (선택) - ISO 8601 형식
- `endDate`: 종료일 (선택)
- `page`: 페이지 번호 (default: 0)
- `size`: 페이지 크기 (default: 10)

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "record-001",
      "title": "첫 주 회고",
      "content": "이번 주에는...",
      "aiSummary": "개발환경 세팅 완료...",
      "type": "WEEKLY_REVIEW",
      "tags": ["회고", "1주차"],
      "createdAt": "2026-03-17T10:30:00Z"
    }
  ],
  "totalElements": 25,
  "totalPages": 3,
  "size": 10,
  "number": 0
}
```

**중요**: 로그인한 사용자의 기록만 반환됩니다.

---

### 기록 상세 조회

```http
GET /api/v1/records/{recordId}
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "record-001",
  "title": "첫 주 회고",
  "content": "이번 주에는 개발환경을 세팅하고 팀원들과 첫 미팅을 했습니다...",
  "aiSummary": "이번 주 주요 활동: 개발환경 세팅 완료, 팀원들과 첫 미팅...",
  "type": "WEEKLY_REVIEW",
  "tags": ["회고", "1주차"],
  "createdAt": "2026-03-17T10:30:00Z",
  "updatedAt": "2026-03-17T10:35:00Z"
}
```

---

### 기록 수정

```http
PUT /api/v1/records/{recordId}
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**
```json
{
  "title": "첫 주 회고 (수정)",
  "content": "이번 주에는 개발환경을 세팅하고... (수정된 내용)",
  "tags": ["회고", "1주차", "개발환경"]
}
```

**Response (200 OK)**
```json
{
  "id": "record-001",
  "title": "첫 주 회고 (수정)",
  "content": "이번 주에는...",
  "tags": ["회고", "1주차", "개발환경"],
  "updatedAt": "2026-03-17T11:00:00Z"
}
```

---

### 기록 삭제

```http
DELETE /api/v1/records/{recordId}
Authorization: Bearer {token}
```

**Response (204 No Content)**

---

### AI 요약 재생성

```http
POST /api/v1/records/{recordId}/summary
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "record-001",
  "aiSummary": "새로운 AI 요약 내용...",
  "generatedAt": "2026-03-17T11:00:00Z"
}
```

---

## 리포트 (Reports)

### 리포트 목록 조회

```http
GET /api/v1/reports
Authorization: Bearer {token}
Query: page=0&size=10
```

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "report-001",
      "week": 1,
      "title": "1주차 온보딩 리포트",
      "summary": "이번 주 주요 성과...",
      "fileUrl": "https://storage.../report-001.pdf",
      "createdAt": "2026-03-08T18:00:00Z"
    }
  ],
  "totalElements": 4,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### 리포트 상세 조회

```http
GET /api/v1/reports/{reportId}
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "report-001",
  "week": 1,
  "title": "1주차 온보딩 리포트",
  "summary": "이번 주는 회사와 팀에 적응하는 시간이었습니다...",
  "achievements": [
    "사내 시스템 계정 발급 완료",
    "개발환경 세팅 완료",
    "팀원들과 첫 미팅"
  ],
  "challenges": [
    "JPA 설정 중 어려움"
  ],
  "nextWeekGoals": [
    "REST API 개발 시작",
    "인증 구현"
  ],
  "checklistProgress": 50.0,
  "recordCount": 5,
  "fileUrl": "https://storage.../report-001.pdf",
  "createdAt": "2026-03-08T18:00:00Z"
}
```

---

### 리포트 생성 (AI)

```http
POST /api/v1/reports/generate
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**
```json
{
  "week": 1
}
```

**Response (202 Accepted)**
```json
{
  "message": "리포트 생성이 시작되었습니다",
  "estimatedTime": "약 1분 소요"
}
```

---

### 리포트 PDF 다운로드

```http
GET /api/v1/reports/{reportId}/download
Authorization: Bearer {token}
```

**Response (200 OK)**
- Content-Type: application/pdf
- Content-Disposition: attachment; filename="report-week-1.pdf"
- Binary PDF data

---

### 리포트 삭제

```http
DELETE /api/v1/reports/{reportId}
Authorization: Bearer {token}
```

**Response (204 No Content)**

---

## 문서 관리 (Documents)

### 문서 목록 조회

```http
GET /api/v1/documents
Authorization: Bearer {token}
Query: category=HR&page=0&size=20
```

**Query Parameters**
- `category`: 카테고리 필터 (선택) - `HR`, `IT`, `ADMIN`, `BENEFITS`
- `search`: 검색어 (선택)
- `page`: 페이지 번호 (default: 0)
- `size`: 페이지 크기 (default: 20)

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "doc-001",
      "title": "복지카드 신청 가이드",
      "category": "HR",
      "description": "복지카드 신청 방법 및 사용 안내",
      "fileUrl": "https://storage.../company_1001/doc-001.pdf",
      "fileType": "PDF",
      "fileSize": 1024000,
      "uploadedAt": "2024-01-15T09:00:00Z"
    }
  ],
  "totalElements": 45,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

**중요**: 자동으로 **로그인한 사용자의 회사 문서**만 반환됩니다.

---

### 문서 상세 조회

```http
GET /api/v1/documents/{documentId}
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "id": "doc-001",
  "title": "복지카드 신청 가이드",
  "category": "HR",
  "description": "복지카드 신청 방법 및 사용 안내",
  "content": "1. 복지카드란? ...",
  "fileUrl": "https://storage.../company_1001/doc-001.pdf",
  "fileType": "PDF",
  "fileSize": 1024000,
  "relatedDocuments": [
    {
      "id": "doc-002",
      "title": "복리후생 제도 안내"
    }
  ],
  "uploadedAt": "2024-01-15T09:00:00Z"
}
```

---

### 문서 검색

```http
GET /api/v1/documents/search
Authorization: Bearer {token}
Query: q=연차&category=HR
```

**Query Parameters**
- `q`: 검색어 (필수)
- `category`: 카테고리 필터 (선택)
- `page`: 페이지 번호
- `size`: 페이지 크기

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "doc-003",
      "title": "연차 사용 가이드",
      "category": "HR",
      "description": "연차 신청 및 사용 방법",
      "relevance": 0.95
    }
  ],
  "totalElements": 3,
  "totalPages": 1
}
```

**중요**: 자동으로 **로그인한 사용자의 회사 문서**만 검색합니다.

---

### 문서 업로드

```http
POST /api/v1/documents/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**권한**: HR, ADMIN만 가능

**Request (multipart/form-data)**
- `file`: (binary) - 업로드할 파일
- `title`: (string) - 문서 제목
- `category`: (string) - 카테고리
- `description`: (string) - 설명 (선택)

**Response (201 Created)**
```json
{
  "id": "doc-new",
  "title": "신규 문서",
  "category": "HR",
  "fileUrl": "https://storage.../company_1001/doc-new.pdf",
  "fileSize": 2048000,
  "uploadedAt": "2026-03-17T11:00:00Z"
}
```

**중요**: 자동으로 **로그인한 사용자의 companyId**로 저장됩니다.

---

### 문서 다운로드 URL 생성

```http
GET /api/v1/documents/{documentId}/download
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "downloadUrl": "https://storage.../presigned-url?expires=...",
  "expiresIn": 600
}
```

---

### 문서 삭제

```http
DELETE /api/v1/documents/{documentId}
Authorization: Bearer {token}
```

**권한**: HR, ADMIN만 가능

**Response (204 No Content)**

---

## 진행률 (Progress)

### 온보딩 진행률 대시보드

```http
GET /api/v1/progress
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "userId": "uuid",
  "userName": "김지원",
  "companyName": "테크 주식회사",
  "joinDate": "2026-03-01",
  "currentWeek": 1,
  "totalWeeks": 12,
  "overallProgress": 8.5,
  "checklistProgress": 15.0,
  "recordsThisWeek": 3,
  "recentAchievements": [
    {
      "title": "첫 기록 작성 완료",
      "achievedAt": "2026-03-17T10:00:00Z"
    }
  ],
  "upcomingTasks": [
    {
      "title": "팀원 소개 받기",
      "dueDate": "2026-03-20",
      "category": "체크리스트"
    }
  ]
}
```

---

### 주차별 진행률 조회

```http
GET /api/v1/progress/weekly?week=1
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "week": 1,
  "title": "1주차 온보딩",
  "completionRate": 50.0,
  "checklist": {
    "completed": 5,
    "total": 10
  },
  "records": {
    "written": 3,
    "expected": 5
  },
  "milestones": [
    {
      "title": "오리엔테이션 참석",
      "completed": true,
      "completedAt": "2026-03-17T09:00:00Z"
    }
  ]
}
```

---

## Rate Limiting

API 호출 제한:

| 엔드포인트 | 제한 |
|-----------|------|
| `/api/v1/auth/login` | 5회/5분 |
| `/api/v1/ai/**` | 30회/분 |
| `/api/v1/documents/upload` | 10회/시간 |
| 기타 모든 API | 100회/분 |

Rate Limit 초과 시:

```json
{
  "timestamp": "2026-03-17T11:00:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "요청 횟수 제한을 초과했습니다. 1분 후 다시 시도해주세요",
  "path": "/api/v1/ai/chat"
}
```

---

## 페이지네이션

페이지네이션을 사용하는 모든 API는 다음 형식을 따릅니다:

**Request**
```
GET /api/v1/records?page=0&size=10&sort=createdAt,desc
```

**Response**
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 10,
  "size": 10,
  "number": 0,
  "numberOfElements": 10,
  "first": true,
  "last": false
}
```

---

## 에러 코드 상세

### 인증 관련
- `AUTH_001`: 토큰이 없습니다
- `AUTH_002`: 토큰이 만료되었습니다
- `AUTH_003`: 유효하지 않은 토큰입니다
- `AUTH_004`: 회사코드, 사원번호 또는 이름이 올바르지 않습니다
- `AUTH_005`: 존재하지 않는 회사입니다

### 리소스 관련
- `RESOURCE_001`: 리소스를 찾을 수 없습니다
- `RESOURCE_002`: 이미 존재하는 리소스입니다
- `RESOURCE_003`: 리소스 접근 권한이 없습니다
- `RESOURCE_004`: 다른 회사의 리소스에 접근할 수 없습니다

### AI 서비스 관련
- `AI_001`: AI 서비스에 연결할 수 없습니다
- `AI_002`: AI 응답 생성에 실패했습니다
- `AI_003`: AI 서비스 요청 제한 초과

### 파일 관련
- `FILE_001`: 파일 크기가 너무 큽니다 (최대 10MB)
- `FILE_002`: 지원하지 않는 파일 형식입니다
- `FILE_003`: 파일 업로드에 실패했습니다

---

## 참고 자료

- [아키텍처 문서](./ARCHITECTURE.md)
- [멀티 테넌시 문서](./MULTI_TENANCY.md)
- [보안 설계 문서](./SECURITY.md)
- [환경변수 가이드](./ENV.md)
- [Swagger UI](http://localhost:8080/swagger-ui.html)

---

**문서 버전**: 2.0.0  
**작성일**: 2026-03-17  
**다음 리뷰 예정**: 2026-04-17

## 변경 이력

### v2.0.0 (2026-03-17)
- ✅ 멀티 테넌시 구조 적용
- ✅ 로그인 API 변경 (companyCode + employeeNumber + name)
- ✅ 모든 응답에 companyCode, companyName 추가
- ✅ 회사 관리 API 추가
- ✅ 데이터 격리 설명 추가
- ✅ JWT 페이로드 구조 업데이트
- ✅ 비밀번호 제거 (사내 전용)

### v1.0.0 (2026-03-10)
- 초기 버전