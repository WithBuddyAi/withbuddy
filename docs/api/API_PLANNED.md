# WithBuddy Planned API

> MVP 이후 검토하거나 추후 구현 예정인 API/정책 초안 문서

**버전**: 0.43.2
**최종 업데이트**: 2026-04-30

---

## 목차

- [문서 목적](#문서-목적)
- [범위 밖 기능 목록](#범위-밖-기능-목록)
- [인증 (Authentication)](#인증-authentication)
- [권한 분할](#권한-분할)
- [회사 관리 (Company)](#회사-관리-company)
- [사용자 관리 (User)](#사용자-관리-user)
- [AI 에이전트 (AI Agent)](#ai-에이전트-ai-agent)
- [체크리스트 (Checklist)](#체크리스트-checklist)
- [기록 (Records)](#기록-records)
- [리포트 (Reports)](#리포트-reports)
- [문서 관리 고도화 (Documents)](#문서-관리-고도화-documents)
- [진행률 (Progress)](#진행률-progress)
- [Rate Limiting](#rate-limiting)
- [페이지네이션](#페이지네이션)
- [변경 이력](#변경-이력)

---

## 문서 목적

이 문서는 다음 항목을 관리합니다.

- 현재 MVP 구현 범위에서 제외된 API
- MVP 이후 검토하거나 추후 구현 예정인 기능 초안
- 실제 응답 스펙에는 아직 포함되지 않은 공통 정책

`docs/api/API_CURRENT.md`에는 현재 구현 범위와 확정된 API만 유지하고, 이 문서는 참고용 초안으로 관리합니다.

---

## 범위 밖 기능 목록

현재 `docs/api/API_CURRENT.md`에서 제외하고 이 문서에서 관리하는 항목은 다음과 같습니다.

- 회원가입
- 권한 분할
- 로그아웃
- 회사 관리
- 사용자 관리 중 `내 정보 수정`
- 사용자 관리 중 `회사 내 사용자 목록 조회`
- AI 대화 히스토리 조회
- AI 특정 대화 조회
- AI 대화 삭제
- 체크리스트 전체 조회
- 기록 전체
- 리포트 전체
- 문서 검색 및 문서 관리 고도화 기능
- 진행률 전체
- Rate Limiting
- 페이지네이션

> 공통 에러 응답과 현재 구현된 API 규격은 `docs/API.md`를 기준으로 한다.

---

## 인증 (Authentication)

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

**필드 설명**

- `companyCode`: 회사 고유 코드. 필수
- `employeeNumber`: 사원번호. 필수, 회사 내에서 고유
- `name`: 이름. 필수
- `department`: 부서. 선택
- `position`: 직급. 선택
- `joinDate`: 입사일. 선택

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

> 현재 MVP에서는 별도 회원가입 대신 `docs/API.md`의 신입 계정 생성 API를 기준으로 한다.

---

### 로그아웃

```http
POST /api/v1/auth/logout
Authorization: Bearer {token}
```

**Response (204 No Content)**

> 로그아웃 및 토큰 무효화 정책은 MVP 이후 인증 고도화 단계에서 확정한다.

---

## 권한 분할

세부 역할 기반 권한 모델은 MVP 이후에 확정합니다.

### 후보 역할

- `EMPLOYEE`
- `MENTOR`
- `MANAGER`
- `HR`
- `ADMIN`

### 적용 후보 정책

- 회사 설정 수정: `HR`, `ADMIN`
- 회사 내 사용자 목록 조회: `MENTOR`, `MANAGER`, `HR`, `ADMIN`
- 문서 업로드/삭제: `HR`, `ADMIN`

> 실제 권한 체크가 구현되고 응답/오류 정책이 확정되면 `docs/API.md`에 반영한다.

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

- `department`: 부서 필터. 선택
- `role`: 역할 필터. 선택
- `page`: 페이지 번호. 기본값 `0`
- `size`: 페이지 크기. 기본값 `20`

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

**중요**: 자동으로 로그인한 사용자의 회사 데이터만 반환한다.

---

## AI 에이전트 (AI Agent)

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

**중요**: 로그인한 사용자의 체크리스트만 반환한다.

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

---

### 기록 목록 조회

```http
GET /api/v1/records
Authorization: Bearer {token}
Query: type=WEEKLY_REVIEW&page=0&size=10
```

**Query Parameters**

- `type`: 기록 유형. 선택. `DAILY`, `WEEKLY_REVIEW`, `MEMO`
- `startDate`: 시작일. 선택. ISO 8601 형식
- `endDate`: 종료일. 선택
- `page`: 페이지 번호. 기본값 `0`
- `size`: 페이지 크기. 기본값 `10`

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

## 문서 관리 고도화 (Documents)

현재 기본 문서 업로드, 목록 조회, 상세 조회, 다운로드 URL 발급, 파일 직접 다운로드, 삭제, 백업 재시도 API는 `docs/API.md`에서 관리한다.

이 문서에서는 MVP 이후 검토할 문서 관리 확장 기능만 관리한다.

### 문서 검색

```http
GET /api/v1/documents/search
Authorization: Bearer {token}
Query: q=연차&documentType=GUIDE&page=0&size=20
```

**Query Parameters**

- `q`: 검색어. 필수
- `documentType`: 문서 유형 필터. 선택
- `department`: 부서 또는 업무 영역 필터. 선택
- `page`: 페이지 번호. 선택
- `size`: 페이지 크기. 선택

**Response (200 OK)**

```json
{
  "content": [
    {
      "id": "doc-003",
      "title": "연차 사용 가이드",
      "documentType": "GUIDE",
      "department": "HR",
      "description": "연차 신청 및 사용 방법",
      "relevance": 0.95
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

> 문서 검색 API는 MVP 이후 고도화 기능으로 검토한다.  
> 검색 결과의 권한 범위는 현재 로그인한 사용자의 회사 문서와 공통 문서로 제한한다.

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

다음 정책은 참고용 초안이다. 실제 제한이 서버에 적용되기 전까지 `docs/API.md`에는 반영하지 않는다.

| 엔드포인트 | 제한 |
|-----------|------|
| `/api/v1/auth/login` | 5회/5분 |
| `/api/v1/ai/**` | 30회/분 |
| `/api/v1/documents/upload` | 10회/시간 |
| 기타 모든 API | 100회/분 |

Rate Limit 초과 시 응답 예시:

```json
{
  "timestamp": "2026-03-17T11:00:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "요청 횟수 제한을 초과했습니다. 1분 후 다시 시도해주세요",
  "path": "/api/v1/ai/chat"
}
```

> 실제 적용 시에는 `docs/API.md`의 표준 에러 응답 형식에 맞춰 응답 구조를 확정한다.

---

## 페이지네이션

다음 형식은 참고용 공통 포맷 초안이다. 실제 적용 시점에 `docs/API.md`와 개별 엔드포인트 명세에 반영한다.

**Request**

```http
GET /api/v1/records?page=0&size=10&sort=createdAt,desc
```

**Response**

```json
{
  "content": [],
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

**문서 버전**: 2.1.1  
**작성일**: 2026-03-17  
**다음 리뷰 예정**: 2026-05-17

## 변경 이력

### v2.1.1 (2026-04-28)

- 현재 `docs/API.md`에서 관리 중인 기본 문서 API 항목을 제거하고, 문서 검색 등 고도화 항목만 남기도록 정리
- `커스텀 에러 코드` 섹션 제거
- 범위 밖 기능 목록과 목차를 현재 문서 구성에 맞게 수정
- 공통 에러 응답은 `docs/API.md` 기준을 따른다는 문구 추가

### v2.1.0 (2026-03-17)

- MVP 범위 밖 API만 별도 계획 문서로 재구성
- `docs/API.md`에서 이동한 항목을 주제별로 정리
- 커스텀 에러코드, Rate Limiting, 페이지네이션을 참고용 정책으로 명시

### v2.0.0 (2026-03-17)

- 초기 초안 작성
