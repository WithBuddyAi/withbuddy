# WithBuddy API 명세서

> WithBuddy REST API 전체 엔드포인트 문서

## 📋 목차
- [API 개요](#api-개요)
- [인증 (Authentication)](#인증-authentication)
- [사용자 (User)](#사용자-user)
- [My Buddy](#my-buddy)
  - [대화 (Conversation)](#대화-conversation)
  - [FAQ](#faq)
  - [체크리스트 (Checklist)](#체크리스트-checklist)
- [기록 (Record)](#기록-record)
- [진행률 (Progress)](#진행률-progress)
- [리포트 (Report)](#리포트-report)
- [문서함 (Document)](#문서함-document)

---

## API 개요

### Base URL
```
로컬: http://localhost:8080
개발: http://<BACKEND_SERVER_IP>:8080
프로덕션: https://api.withbuddy.com
```

### 공통 헤더
```http
Content-Type: application/json
Authorization: Bearer {access_token}
```

### 응답 형식
```json
{
  "success": true,
  "data": { ... },
  "message": "Success"
}
```

### 에러 응답 형식
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Error message"
  }
}
```

---

## 인증 (Authentication)

### 회원가입
```http
POST /api/auth/signup
```

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123!",
  "name": "홍길동",
  "department": "개발팀",
  "position": "신입사원",
  "joinDate": "2026-03-17"
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

**Error Response (409 Conflict)**
```json
{
  "success": false,
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "이미 가입된 이메일입니다"
  }
}
```

---

### 로그인
```http
POST /api/auth/login
```

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123!"
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

**Error Response (401 Unauthorized)**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "이메일 또는 비밀번호가 올바르지 않습니다"
  }
}
```

---

### 토큰 재발급
```http
POST /api/auth/refresh
```

**Request Body**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

**Error Response (401 Unauthorized)**
```json
{
  "success": false,
  "error": {
    "code": "TOKEN_EXPIRED",
    "message": "토큰이 만료되었습니다"
  }
}
```

---

### 로그아웃
```http
POST /api/auth/logout
```

**Headers**
```
Authorization: Bearer {access_token}
```

**Response (200 OK)**
```json
{
  "success": true,
  "message": "로그아웃 성공"
}
```

---

## 사용자 (User)

### 내 정보 조회
```http
GET /api/users/me
```

**Headers**
```
Authorization: Bearer {access_token}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "department": "개발팀",
    "position": "신입사원",
    "joinDate": "2026-03-17",
    "profileImage": "https://example.com/profile.jpg",
    "mentorName": "김멘토",
    "mentorEmail": "mentor@example.com"
  }
}
```

---

### 내 정보 수정
```http
PUT /api/users/me
```

**Request Body**
```json
{
  "name": "홍길동",
  "department": "개발팀",
  "position": "주니어 개발자",
  "profileImage": "https://example.com/new-profile.jpg"
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "name": "홍길동",
    "department": "개발팀",
    "position": "주니어 개발자"
  }
}
```

---

### 비밀번호 변경
```http
PUT /api/users/me/password
```

**Request Body**
```json
{
  "currentPassword": "oldPassword123!",
  "newPassword": "newPassword456!"
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "message": "비밀번호가 변경되었습니다"
}
```

**Error Response (400 Bad Request)**
```json
{
  "success": false,
  "error": {
    "code": "CURRENT_PASSWORD_MISMATCH",
    "message": "현재 비밀번호가 일치하지 않습니다"
  }
}
```

---

## My Buddy

### 대화 (Conversation)

#### 질문하기 (AI 답변)
```http
POST /api/conversations
```

**Request Body**
```json
{
  "question": "연차는 어떻게 신청하나요?",
  "conversationId": null
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "conversationId": "conv-123",
    "question": "연차는 어떻게 신청하나요?",
    "answer": "연차 신청은 인사 포털에서 가능합니다. 1. 인사 포털 로그인 2. 연차 신청 메뉴 클릭...",
    "sources": [
      {
        "documentId": "doc-1",
        "documentTitle": "인사 규정",
        "relevance": 0.95
      }
    ],
    "createdAt": "2026-03-17T10:30:00"
  }
}
```

**Error Response (503 Service Unavailable)**
```json
{
  "success": false,
  "error": {
    "code": "AI_SERVICE_UNAVAILABLE",
    "message": "AI 서비스에 연결할 수 없습니다"
  }
}
```

---

#### 대화 히스토리 조회
```http
GET /api/conversations?page=0&size=20
```

**Query Parameters**
- `page`: 페이지 번호 (default: 0)
- `size`: 페이지 크기 (default: 20)

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "conversations": [
      {
        "conversationId": "conv-123",
        "question": "연차는 어떻게 신청하나요?",
        "answer": "연차 신청은 인사 포털에서...",
        "createdAt": "2026-03-17T10:30:00"
      }
    ],
    "totalElements": 50,
    "totalPages": 3,
    "currentPage": 0
  }
}
```

---

#### 특정 대화 조회
```http
GET /api/conversations/{conversationId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "conversationId": "conv-123",
    "messages": [
      {
        "role": "user",
        "content": "연차는 어떻게 신청하나요?",
        "timestamp": "2026-03-17T10:30:00"
      },
      {
        "role": "assistant",
        "content": "연차 신청은 인사 포털에서...",
        "timestamp": "2026-03-17T10:30:05"
      }
    ]
  }
}
```

**Error Response (404 Not Found)**
```json
{
  "success": false,
  "error": {
    "code": "CONVERSATION_NOT_FOUND",
    "message": "대화를 찾을 수 없습니다"
  }
}
```

---

#### 대화 삭제
```http
DELETE /api/conversations/{conversationId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "message": "대화가 삭제되었습니다"
}
```

---

### FAQ

#### FAQ 목록 조회
```http
GET /api/faqs?category=인사&page=0&size=20
```

**Query Parameters**
- `category`: 카테고리 (선택)
- `page`: 페이지 번호 (default: 0)
- `size`: 페이지 크기 (default: 20)

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "faqs": [
      {
        "faqId": 1,
        "category": "인사",
        "question": "연차는 언제부터 사용할 수 있나요?",
        "answer": "입사 후 1개월이 지나면 연차를 사용할 수 있습니다.",
        "viewCount": 150,
        "createdAt": "2026-01-01T00:00:00"
      }
    ],
    "totalElements": 30,
    "totalPages": 2
  }
}
```

---

#### FAQ 상세 조회
```http
GET /api/faqs/{faqId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "faqId": 1,
    "category": "인사",
    "question": "연차는 언제부터 사용할 수 있나요?",
    "answer": "입사 후 1개월이 지나면 연차를 사용할 수 있습니다. 연차 일수는...",
    "viewCount": 151,
    "relatedFaqs": [
      {
        "faqId": 2,
        "question": "반차 사용은 어떻게 하나요?"
      }
    ]
  }
}
```

---

#### FAQ 검색
```http
GET /api/faqs/search?q=연차&page=0&size=20
```

**Query Parameters**
- `q`: 검색어 (필수)
- `page`: 페이지 번호
- `size`: 페이지 크기

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "results": [
      {
        "faqId": 1,
        "question": "연차는 언제부터 사용할 수 있나요?",
        "answer": "입사 후 1개월이 지나면...",
        "category": "인사",
        "relevance": 0.95
      }
    ],
    "totalElements": 5
  }
}
```

---

### 체크리스트 (Checklist)

#### 전체 체크리스트 조회
```http
GET /api/checklists
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "checklists": [
      {
        "checklistId": 1,
        "week": 1,
        "title": "1주차 온보딩",
        "items": [
          {
            "itemId": 1,
            "content": "사내 시스템 계정 발급",
            "completed": true,
            "completedAt": "2026-03-17T14:00:00"
          },
          {
            "itemId": 2,
            "content": "팀원 소개 받기",
            "completed": false,
            "completedAt": null
          }
        ],
        "completionRate": 50
      }
    ],
    "overallCompletionRate": 35
  }
}
```

---

#### 주차별 체크리스트 조회
```http
GET /api/checklists?week=1
```

**Query Parameters**
- `week`: 주차 (1-12)

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "week": 1,
    "title": "1주차 온보딩",
    "description": "첫 주에는 회사와 팀에 적응하는 시간입니다",
    "items": [
      {
        "itemId": 1,
        "content": "사내 시스템 계정 발급",
        "description": "IT팀에 요청하여 메일, 메신저 등 계정을 받으세요",
        "completed": true,
        "completedAt": "2026-03-17T14:00:00"
      }
    ],
    "completionRate": 50
  }
}
```

---

#### 체크리스트 항목 완료/미완료 토글
```http
PATCH /api/checklists/{itemId}
```

**Request Body**
```json
{
  "completed": true
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "itemId": 1,
    "completed": true,
    "completedAt": "2026-03-17T14:00:00"
  }
}
```

---

## 기록 (Record)

### 데일리 기록 작성
```http
POST /api/records
```

**Request Body**
```json
{
  "date": "2026-03-17",
  "content": "오늘은 Spring Boot 프로젝트 초기 설정을 했습니다. JPA 설정과 Entity 작성을 완료했고...",
  "mood": "GOOD",
  "tags": ["개발", "Spring Boot", "JPA"]
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "recordId": 1,
    "date": "2026-03-17",
    "content": "오늘은 Spring Boot 프로젝트...",
    "mood": "GOOD",
    "tags": ["개발", "Spring Boot", "JPA"],
    "summary": null,
    "createdAt": "2026-03-17T18:00:00"
  }
}
```

**Error Response (409 Conflict)**
```json
{
  "success": false,
  "error": {
    "code": "RECORD_ALREADY_EXISTS",
    "message": "해당 날짜에 이미 기록이 작성되어 있습니다"
  }
}
```

---

### 기록 목록 조회
```http
GET /api/records?startDate=2026-03-01&endDate=2026-03-31&page=0&size=20
```

**Query Parameters**
- `startDate`: 시작일 (선택)
- `endDate`: 종료일 (선택)
- `page`: 페이지 번호
- `size`: 페이지 크기

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "records": [
      {
        "recordId": 1,
        "date": "2026-03-17",
        "content": "오늘은 Spring Boot...",
        "summary": "Spring Boot 초기 설정 완료",
        "mood": "GOOD",
        "tags": ["개발", "Spring Boot"],
        "createdAt": "2026-03-17T18:00:00"
      }
    ],
    "totalElements": 15,
    "totalPages": 1
  }
}
```

---

### 기록 상세 조회
```http
GET /api/records/{recordId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "recordId": 1,
    "date": "2026-03-17",
    "content": "오늘은 Spring Boot 프로젝트 초기 설정을 했습니다...",
    "summary": "Spring Boot 초기 설정 및 JPA Entity 작성 완료",
    "mood": "GOOD",
    "tags": ["개발", "Spring Boot", "JPA"],
    "createdAt": "2026-03-17T18:00:00",
    "updatedAt": "2026-03-17T18:05:00"
  }
}
```

**Error Response (404 Not Found)**
```json
{
  "success": false,
  "error": {
    "code": "RECORD_NOT_FOUND",
    "message": "기록을 찾을 수 없습니다"
  }
}
```

---

### 기록 수정
```http
PUT /api/records/{recordId}
```

**Request Body**
```json
{
  "content": "오늘은 Spring Boot 프로젝트 초기 설정을 했습니다. 수정된 내용...",
  "mood": "GREAT",
  "tags": ["개발", "Spring Boot", "JPA", "MySQL"]
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "recordId": 1,
    "date": "2026-03-17",
    "content": "오늘은 Spring Boot...",
    "mood": "GREAT",
    "tags": ["개발", "Spring Boot", "JPA", "MySQL"],
    "updatedAt": "2026-03-17T19:00:00"
  }
}
```

---

### 기록 삭제
```http
DELETE /api/records/{recordId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "message": "기록이 삭제되었습니다"
}
```

---

### AI 요약 요청
```http
POST /api/records/{recordId}/summary
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "recordId": 1,
    "summary": "Spring Boot 프로젝트 초기 설정 및 JPA Entity 작성 완료. MySQL 연동 성공.",
    "generatedAt": "2026-03-17T18:10:00"
  }
}
```

**Error Response (500 Internal Server Error)**
```json
{
  "success": false,
  "error": {
    "code": "SUMMARY_GENERATION_FAILED",
    "message": "요약 생성에 실패했습니다"
  }
}
```

---

### 주간 기록 조회
```http
GET /api/records/weekly?week=2026-W11
```

**Query Parameters**
- `week`: ISO 주차 형식 (YYYY-Www)

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "week": "2026-W11",
    "startDate": "2026-03-16",
    "endDate": "2026-03-22",
    "records": [
      {
        "recordId": 1,
        "date": "2026-03-17",
        "summary": "Spring Boot 초기 설정 완료",
        "mood": "GOOD"
      }
    ],
    "totalRecords": 5
  }
}
```

---

## 진행률 (Progress)

### 온보딩 진행률 조회
```http
GET /api/progress
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "userName": "홍길동",
    "joinDate": "2026-03-17",
    "currentWeek": 1,
    "totalWeeks": 12,
    "overallProgress": 8,
    "weeklyProgress": [
      {
        "week": 1,
        "completionRate": 50,
        "checklistCompleted": 5,
        "checklistTotal": 10
      }
    ],
    "milestones": [
      {
        "week": 1,
        "title": "오리엔테이션",
        "completed": false
      }
    ]
  }
}
```

---

### 주차별 진행률 조회
```http
GET /api/progress/weekly?week=1
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "week": 1,
    "title": "1주차 온보딩",
    "completionRate": 50,
    "checklist": {
      "completed": 5,
      "total": 10
    },
    "records": {
      "written": 3,
      "expected": 5
    }
  }
}
```

---

### 진행 현황 대시보드
```http
GET /api/progress/overview
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "overallProgress": 8,
    "currentWeek": 1,
    "checklistProgress": 15,
    "recordsThisWeek": 3,
    "recentAchievements": [
      {
        "title": "첫 기록 작성",
        "achievedAt": "2026-03-17T18:00:00"
      }
    ],
    "upcomingTasks": [
      {
        "title": "팀원 소개 받기",
        "dueDate": "2026-03-20"
      }
    ]
  }
}
```

---

## 리포트 (Report)

### 주간 리포트 생성 (AI)
```http
POST /api/reports
```

**Request Body**
```json
{
  "week": "2026-W11",
  "includeRecords": true,
  "includeChecklist": true
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "reportId": 1,
    "week": "2026-W11",
    "startDate": "2026-03-16",
    "endDate": "2026-03-22",
    "summary": "이번 주는 Spring Boot 프로젝트 초기 설정을 완료했으며...",
    "achievements": [
      "Spring Boot 프로젝트 초기 설정",
      "JPA Entity 작성",
      "MySQL 연동"
    ],
    "challenges": [
      "JPA 설정 중 어려움 겪음"
    ],
    "nextWeekGoals": [
      "REST API 개발 시작",
      "인증 구현"
    ],
    "checklistProgress": 50,
    "recordCount": 5,
    "generatedAt": "2026-03-23T10:00:00"
  }
}
```

---

### 리포트 목록 조회
```http
GET /api/reports?page=0&size=10
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "reports": [
      {
        "reportId": 1,
        "week": "2026-W11",
        "summary": "이번 주는 Spring Boot...",
        "generatedAt": "2026-03-23T10:00:00",
        "sharedWithMentor": true
      }
    ],
    "totalElements": 3,
    "totalPages": 1
  }
}
```

---

### 리포트 상세 조회
```http
GET /api/reports/{reportId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "reportId": 1,
    "week": "2026-W11",
    "startDate": "2026-03-16",
    "endDate": "2026-03-22",
    "summary": "이번 주는 Spring Boot 프로젝트 초기 설정을 완료했으며...",
    "achievements": ["..."],
    "challenges": ["..."],
    "nextWeekGoals": ["..."],
    "checklistProgress": 50,
    "recordCount": 5,
    "mentorFeedback": null,
    "generatedAt": "2026-03-23T10:00:00"
  }
}
```

---

### 리포트 PDF 다운로드
```http
GET /api/reports/{reportId}/pdf
```

**Response (200 OK)**
- Content-Type: application/pdf
- 파일 다운로드

---

### 멘토에게 리포트 공유
```http
POST /api/reports/{reportId}/share
```

**Request Body**
```json
{
  "message": "이번 주 활동 내용 공유드립니다."
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "reportId": 1,
    "sharedAt": "2026-03-23T11:00:00",
    "mentorEmail": "mentor@example.com"
  }
}
```

---

## 문서함 (Document)

### 문서 목록 조회
```http
GET /api/documents?category=인사&page=0&size=20
```

**Query Parameters**
- `category`: 카테고리 (인사, 행정, IT, 복리후생 등)
- `page`: 페이지 번호
- `size`: 페이지 크기

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "documents": [
      {
        "documentId": 1,
        "category": "인사",
        "title": "인사 규정",
        "description": "회사 인사 관련 규정 및 절차",
        "fileUrl": "https://example.com/documents/hr-policy.pdf",
        "fileType": "PDF",
        "fileSize": 1024000,
        "uploadedAt": "2026-01-01T00:00:00"
      }
    ],
    "totalElements": 25,
    "totalPages": 2
  }
}
```

---

### 문서 상세 조회
```http
GET /api/documents/{documentId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "documentId": 1,
    "category": "인사",
    "title": "인사 규정",
    "description": "회사 인사 관련 규정 및 절차",
    "content": "1. 채용 ... 2. 인사평가 ...",
    "fileUrl": "https://example.com/documents/hr-policy.pdf",
    "fileType": "PDF",
    "fileSize": 1024000,
    "relatedDocuments": [
      {
        "documentId": 2,
        "title": "연차 사용 가이드"
      }
    ],
    "uploadedAt": "2026-01-01T00:00:00"
  }
}
```

---

### 문서 검색
```http
GET /api/documents/search?q=연차&page=0&size=20
```

**Query Parameters**
- `q`: 검색어 (필수)
- `category`: 카테고리 필터 (선택)
- `page`: 페이지 번호
- `size`: 페이지 크기

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "results": [
      {
        "documentId": 2,
        "title": "연차 사용 가이드",
        "description": "연차 신청 및 사용 방법",
        "category": "인사",
        "relevance": 0.95
      }
    ],
    "totalElements": 3
  }
}
```

---

### 담당자 목록 조회
```http
GET /api/employees?department=인사&page=0&size=20
```

**Query Parameters**
- `department`: 부서 (선택)
- `page`: 페이지 번호
- `size`: 페이지 크기

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "employees": [
      {
        "employeeId": 1,
        "name": "김담당",
        "department": "인사팀",
        "position": "대리",
        "email": "kim@example.com",
        "phone": "010-1234-5678",
        "responsibilities": ["채용", "인사평가"]
      }
    ],
    "totalElements": 50,
    "totalPages": 3
  }
}
```

---

### 담당자 상세 조회
```http
GET /api/employees/{employeeId}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "employeeId": 1,
    "name": "김담당",
    "department": "인사팀",
    "position": "대리",
    "email": "kim@example.com",
    "phone": "010-1234-5678",
    "extension": "1234",
    "location": "본관 3층",
    "responsibilities": ["채용", "인사평가"],
    "availableHours": "09:00-18:00"
  }
}
```

---

## HTTP Status Code 요약

### 성공 응답
- **200 OK**: 요청 성공 (조회, 수정, 삭제)
- **201 Created**: 리소스 생성 성공 (회원가입, 기록 작성, 리포트 생성)

### 클라이언트 에러
- **400 Bad Request**: 잘못된 요청 (유효성 검증 실패, 잘못된 파라미터)
- **401 Unauthorized**: 인증 실패 (토큰 없음, 만료, 잘못된 토큰)
- **403 Forbidden**: 권한 없음 (다른 사용자 리소스 접근)
- **404 Not Found**: 리소스 없음
- **409 Conflict**: 리소스 충돌 (이메일 중복, 기록 중복)

### 서버 에러
- **500 Internal Server Error**: 서버 내부 오류
- **502 Bad Gateway**: 외부 API 오류 (AI 서비스)
- **503 Service Unavailable**: 서비스 일시 중단 (AI 서비스 장애)

---

## 다음 단계

- [에러 코드](./ERROR-CODES.md) - 상세 에러 처리 가이드
- [개발 환경 설정](../guides/SETUP.md) - API 로컬 실행
- [Swagger UI](http://localhost:8080/swagger-ui.html) - 인터랙티브 API 문서
