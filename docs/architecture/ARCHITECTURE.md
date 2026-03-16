# WithBuddy 시스템 아키텍처

> 신입사원 온보딩 AI 통합 비서 서비스

**최종 업데이트**: 2026-03-16  
**버전**: 1.0.0

---

## 📋 목차

- [1. 시스템 아키텍처](#1-시스템-아키텍처)
- [2. 기술 스택](#2-기술-스택)
- [3. 인프라 구조](#3-인프라-구조)
- [4. 데이터 흐름](#4-데이터-흐름)
- [5. 보안 설계](#5-보안-설계)
- [6. API 설계](#6-api-설계)
- [7. 모니터링 & 로깅](#7-모니터링--로깅)
- [8. 배포 전략](#8-배포-전략)

---

## 1. 시스템 아키텍처

### 1.1 전체 구조도

```
┌─────────────────────────────────────────────────────────────┐
│                         사용자 (신입사원)                      │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTPS
                        ↓
┌─────────────────────────────────────────────────────────────┐
│                  Frontend (React + Vite)                     │
│                     Vercel 호스팅                             │
│                  (Cloudflare 도메인 관리)                     │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTPS/CORS
                        │ API 요청
                        ↓
┌─────────────────────────────────────────────────────────────┐
│              Cloud Provider (AWS/GCP/Oracle)                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    VCN (Private Network)              │   │
│  │                                                        │   │
│  │  ┌─────────────┐      ┌─────────────┐      ┌───────┐ │   │
│  │  │   Backend   │◄────►│   AI Server │◄────►│ Redis │ │   │
│  │  │ Spring Boot │      │  FastAPI    │      │ Cache │ │   │
│  │  │   (8080)    │      │   (5000)    │      │       │ │   │
│  │  └──────┬──────┘      └──────┬──────┘      └───────┘ │   │
│  │         │                     │                        │   │
│  │         │                     │                        │   │
│  │         └─────────┬───────────┘                        │   │
│  │                   ↓                                    │   │
│  │         ┌──────────────────┐                          │   │
│  │         │   MySQL 8.0      │                          │   │
│  │         │   Database       │                          │   │
│  │         └──────────────────┘                          │   │
│  │                                                        │   │
│  │         ┌──────────────────┐                          │   │
│  │         │ Object Storage   │◄─── 파일 업로드/다운로드  │   │
│  │         │  (S3/GCS/OCI)    │                          │   │
│  │         └──────────────────┘                          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                        │
                        ↓
                 ┌─────────────┐
                 │  OpenAI API │
                 │  (GPT-4)    │
                 └─────────────┘
```

### 1.2 서버 구성

#### Frontend Server
- **호스팅**: Vercel
- **도메인**: Cloudflare 관리
- **프로토콜**: HTTPS
- **CDN**: Vercel Edge Network

#### Backend Server (VCN 내부)
- **위치**: Cloud Provider Private Network
- **포트**: 8080
- **프로토콜**: HTTP (내부), HTTPS (외부)
- **스케일링**: 수평 확장 가능

#### AI Server (VCN 내부)
- **위치**: Backend와 동일 VCN
- **포트**: 5000
- **프로토콜**: HTTP (내부 전용)
- **통신**: Backend ↔ AI 내부 통신

#### Database Server (VCN 내부)
- **위치**: 별도 서버, VCN 연결
- **포트**: 3306
- **접근**: Private IP만 허용
- **백업**: 자동 백업 스케줄링

#### Cache Server (VCN 내부)
- **서비스**: Redis
- **용도**: 세션, API 응답 캐싱
- **포트**: 6379

---

## 2. 기술 스택

### 2.1 Frontend

```yaml
Framework: React 18
Build Tool: Vite
Language: JavaScript (ES6+)
State Management: Context API
Routing: React Router v6
HTTP Client: Axios
Styling: Tailwind CSS
UI Components: 
  - Headless UI (추천)
  - Radix UI (추천)
Form Handling: React Hook Form (추천)
Validation: Zod (추천)
Date Handling: date-fns / day.js
Charts: Recharts (추천)
Icons: Lucide React / Heroicons
```

### 2.2 Backend

```yaml
Framework: Spring Boot 3.5.11
Language: Java 21
Build Tool: Gradle
Security: Spring Security + JWT
ORM: Spring Data JPA (Hibernate)
Database: MySQL 8.0
API Documentation: SpringDoc OpenAPI (Swagger)
Validation: Spring Validation
Logging: SLF4J + Logback
```

### 2.3 AI Service

```yaml
Framework: FastAPI
Language: Python 3.10+
AI Provider: OpenAI API (GPT-4)
Vector DB: Pinecone / ChromaDB (문서 임베딩)
HTTP Client: httpx
Data Processing: pandas, numpy
Async: asyncio
Cache: Redis
```

### 2.4 Infrastructure

```yaml
Cloud Provider: AWS / GCP / Oracle Cloud (선택 예정)
Network: VCN (Virtual Cloud Network)
Storage: S3 / Google Cloud Storage / OCI Object Storage
Cache: Redis
Domain: Cloudflare
Frontend Hosting: Vercel
SSL/TLS: Let's Encrypt / Cloudflare SSL
```

### 2.5 DevOps & Tools

```yaml
Version Control: Git + GitHub
CI/CD: GitHub Actions
Monitoring: 
  - Application: Spring Boot Actuator
  - Error Tracking: Sentry (추천)
  - Logging: ELK Stack / CloudWatch (추천)
API Testing: Postman / REST Client
Load Testing: JMeter / k6
```

---

## 3. 인프라 구조

### 3.1 네트워크 구성

#### VCN (Virtual Cloud Network) 설계

```
┌─────────────────── VCN (10.0.0.0/16) ───────────────────┐
│                                                           │
│  Public Subnet (10.0.1.0/24)                            │
│  ┌──────────────────────────────────────────┐           │
│  │  - Load Balancer (ALB/NLB)               │           │
│  │  - NAT Gateway                            │           │
│  │  - Bastion Host (관리용, 옵션)           │           │
│  └──────────────────────────────────────────┘           │
│                                                           │
│  Private Subnet - App (10.0.2.0/24)                     │
│  ┌──────────────────────────────────────────┐           │
│  │  - Backend Server (Spring Boot)          │           │
│  │  - AI Server (FastAPI)                   │           │
│  │  - Redis Cache                            │           │
│  └──────────────────────────────────────────┘           │
│                                                           │
│  Private Subnet - DB (10.0.3.0/24)                      │
│  ┌──────────────────────────────────────────┐           │
│  │  - MySQL Database                         │           │
│  └──────────────────────────────────────────┘           │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

#### 보안 그룹 규칙

**Load Balancer Security Group**
```
Inbound:
  - 443 (HTTPS) from 0.0.0.0/0
  - 80 (HTTP) from 0.0.0.0/0 → 443으로 리다이렉트
Outbound:
  - 8080 to Backend Security Group
```

**Backend Security Group**
```
Inbound:
  - 8080 from Load Balancer SG
  - 8080 from Frontend (Vercel IP 대역)
Outbound:
  - 3306 to MySQL SG
  - 5000 to AI Server SG
  - 6379 to Redis SG
  - 443 to 0.0.0.0/0 (OpenAI API)
```

**AI Server Security Group**
```
Inbound:
  - 5000 from Backend SG only
Outbound:
  - 3306 to MySQL SG
  - 6379 to Redis SG
  - 443 to 0.0.0.0/0 (OpenAI API)
```

**MySQL Security Group**
```
Inbound:
  - 3306 from Backend SG
  - 3306 from AI Server SG
Outbound:
  - None (데이터베이스는 Outbound 불필요)
```

**Redis Security Group**
```
Inbound:
  - 6379 from Backend SG
  - 6379 from AI Server SG
Outbound:
  - None
```

### 3.2 스토리지 구조

#### Object Storage (S3/GCS/OCI)

```
withbuddy-storage/
├── documents/              # 인사/행정 문서
│   ├── templates/         # 문서 템플릿
│   ├── user-uploads/      # 사용자 업로드
│   └── generated/         # AI 생성 리포트
├── avatars/               # 프로필 이미지
├── embeddings/            # 문서 임베딩 벡터
└── backups/               # 백업 파일
    ├── db/
    └── logs/
```

**접근 권한**:
- Public Read: 없음 (모든 파일 Private)
- Backend: 읽기/쓰기 권한
- Presigned URL을 통한 임시 다운로드 링크 제공

---

## 4. 데이터 흐름

### 4.1 일반 API 요청 흐름

```
[사용자] 
   ↓ (1) 로그인 요청
[Frontend - Vercel]
   ↓ (2) POST /api/auth/login
[Load Balancer]
   ↓ (3) HTTPS → HTTP 변환
[Backend - Spring Boot]
   ↓ (4) 사용자 조회
[MySQL Database]
   ↑ (5) 사용자 정보 반환
[Backend]
   ↓ (6) JWT 생성
[Frontend]
   ↓ (7) JWT 저장 (localStorage)
[사용자]
```

### 4.2 AI 기능 요청 흐름 (Q&A)

```
[사용자] "복지카드 신청 방법은?"
   ↓ (1) 질문 입력
[Frontend]
   ↓ (2) POST /api/ai/chat
   ↓     Headers: Authorization: Bearer {JWT}
[Backend]
   ↓ (3) JWT 검증
   ↓ (4) POST /chat (Internal)
[AI Server - FastAPI]
   ↓ (5) 벡터 DB에서 관련 문서 검색
   ↓ (6) OpenAI API 호출 (GPT-4)
   ↓     Context: 검색된 사내 문서
[OpenAI API]
   ↑ (7) AI 응답 생성
[AI Server]
   ↓ (8) 응답 캐싱 (Redis)
   ↑ (9) JSON 응답 반환
[Backend]
   ↓ (10) 로깅 & 저장
   ↑ (11) 클라이언트에 응답
[Frontend]
   ↓ (12) 답변 표시
[사용자]
```

### 4.3 파일 업로드/다운로드 흐름

#### 업로드
```
[사용자] 파일 선택
   ↓ (1) FormData 생성
[Frontend]
   ↓ (2) POST /api/files/upload
   ↓     Content-Type: multipart/form-data
[Backend]
   ↓ (3) 파일 검증 (크기, 타입)
   ↓ (4) Object Storage에 업로드
[S3/GCS/OCI]
   ↑ (5) 파일 URL 반환
[Backend]
   ↓ (6) DB에 메타데이터 저장
   ↓     (file_id, user_id, path, size, type)
[MySQL]
   ↑ (7) 파일 정보 반환
[Frontend]
```

#### 다운로드
```
[사용자] 파일 다운로드 클릭
   ↓ (1) GET /api/files/{file_id}/download
[Backend]
   ↓ (2) 권한 확인
   ↓ (3) Presigned URL 생성 (유효기간 10분)
[Object Storage]
   ↑ (4) Presigned URL 반환
[Backend]
   ↓ (5) URL 전달
[Frontend]
   ↓ (6) URL로 직접 다운로드
[Object Storage]
   ↓ (7) 파일 스트리밍
[사용자]
```

### 4.4 주간 리포트 자동 생성 흐름

```
[스케줄러] (매주 금요일 18:00)
   ↓ (1) Cron Job 트리거
[Backend]
   ↓ (2) 활성 사용자 목록 조회
[MySQL]
   ↑ (3) 사용자 리스트
[Backend]
   ↓ (4) 각 사용자의 주간 활동 조회
   ↓     (체크리스트, 기록, Q&A 이력)
[MySQL]
   ↑ (5) 활동 데이터
[Backend]
   ↓ (6) POST /generate-report (Internal)
[AI Server]
   ↓ (7) OpenAI API로 리포트 생성
[OpenAI API]
   ↑ (8) 생성된 리포트 텍스트
[AI Server]
   ↑ (9) Markdown 리포트 반환
[Backend]
   ↓ (10) PDF 변환 (옵션)
   ↓ (11) Object Storage에 저장
   ↓ (12) DB에 리포트 메타데이터 저장
   ↓ (13) 사용자에게 알림 (이메일/푸시)
[사용자]
```

---

## 5. 보안 설계

### 5.1 인증 (Authentication)

#### JWT 기반 인증

**로그인 프로세스**:
1. 사용자: 사원번호 + 비밀번호 입력
2. Backend: 
   - 비밀번호 검증 (BCrypt)
   - JWT Access Token 생성 (유효기간: 2시간)
   - JWT Refresh Token 생성 (유효기간: 7일)
3. Frontend: 
   - Access Token: localStorage 저장
   - Refresh Token: httpOnly Cookie 저장 (권장)

**JWT Payload 구조**:
```json
{
  "sub": "user_id",
  "employeeNumber": "2024001",
  "name": "홍길동",
  "role": "EMPLOYEE",
  "iat": 1234567890,
  "exp": 1234574890
}
```

**Token Refresh 흐름**:
```
[Frontend] Access Token 만료 감지
   ↓ POST /api/auth/refresh
   ↓ Cookie: refresh_token
[Backend] Refresh Token 검증
   ↓ 새로운 Access Token 발급
[Frontend] Token 갱신
```

### 5.2 인가 (Authorization)

#### 역할 기반 접근 제어 (RBAC)

```java
// Role 정의
enum Role {
    EMPLOYEE,      // 일반 사원
    MENTOR,        // 멘토
    MANAGER,       // 매니저
    HR,            // 인사팀
    ADMIN          // 시스템 관리자
}

// API 권한 예시
@PreAuthorize("hasRole('EMPLOYEE')")
GET /api/users/me

@PreAuthorize("hasAnyRole('MENTOR', 'MANAGER')")
GET /api/reports/{userId}

@PreAuthorize("hasRole('HR')")
POST /api/documents/templates

@PreAuthorize("hasRole('ADMIN')")
DELETE /api/users/{userId}
```

### 5.3 데이터 보안

#### 암호화

**전송 중 암호화 (Encryption in Transit)**:
- HTTPS/TLS 1.3 사용
- Cloudflare SSL 인증서

**저장 시 암호화 (Encryption at Rest)**:
- 데이터베이스: MySQL Transparent Data Encryption (TDE)
- Object Storage: Server-Side Encryption (SSE)
- 민감 정보: AES-256 암호화
  - 사원번호
  - 개인정보

**비밀번호 해싱**:
```java
// BCrypt (Work Factor: 12)
String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
```

### 5.4 CORS (Cross-Origin Resource Sharing)

**Backend CORS 설정**:
```yaml
# application.yml
cors:
  allowed-origins:
    - https://withbuddy.com
    - https://www.withbuddy.com
    - https://withbuddy.vercel.app
  allowed-methods:
    - GET
    - POST
    - PUT
    - DELETE
    - PATCH
  allowed-headers:
    - Authorization
    - Content-Type
  exposed-headers:
    - X-Total-Count
  max-age: 3600
  allow-credentials: true
```

### 5.5 API Rate Limiting

**계층별 제한**:

| 엔드포인트 | 제한 | 목적 |
|-----------|------|------|
| `/api/auth/login` | 5회/5분 | 무차별 대입 공격 방지 |
| `/api/ai/**` | 30회/분 | AI API 비용 관리 |
| `/api/files/upload` | 10회/시간 | 스토리지 남용 방지 |
| 일반 API | 100회/분 | DDoS 방지 |

**구현**:
```java
// Spring Boot + Redis
@RateLimiter(
    name = "aiApi",
    rateLimiterConfig = "30-per-minute"
)
public ResponseEntity<?> chatWithAI(@RequestBody ChatRequest request) {
    // ...
}
```

### 5.6 입력 검증

**Backend Validation**:
```java
public class UserRegisterRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9]{7}$", message = "사원번호는 7자리 숫자여야 합니다")
    private String employeeNumber;
    
    @NotBlank
    @Size(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$")
    private String password;
    
    @NotBlank
    @Size(min = 2, max = 50)
    private String name;
}
```

**SQL Injection 방지**:
- JPA Prepared Statement 사용
- Native Query 시 파라미터 바인딩 필수

**XSS 방지**:
- 사용자 입력 HTML 이스케이프
- Content Security Policy (CSP) 헤더 설정

---

## 6. API 설계

### 6.1 API 버전 관리

**URL 버전 관리 방식**:
```
https://api.withbuddy.com/v1/users
https://api.withbuddy.com/v2/users
```

### 6.2 주요 API 엔드포인트

#### 인증 (Authentication)

```http
POST /api/v1/auth/login
Content-Type: application/json

Request:
{
  "employeeNumber": "2024001",
  "password": "password123!"
}

Response: 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "uuid",
    "employeeNumber": "2024001",
    "name": "홍길동",
    "role": "EMPLOYEE"
  }
}
```

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=...

Response: 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

```http
POST /api/v1/auth/logout
Authorization: Bearer {token}

Response: 204 No Content
```

#### 사용자 관리

```http
GET /api/v1/users/me
Authorization: Bearer {token}

Response: 200 OK
{
  "id": "uuid",
  "employeeNumber": "2024001",
  "name": "홍길동",
  "department": "개발팀",
  "position": "사원",
  "joinDate": "2024-03-01",
  "onboardingProgress": 45.5
}
```

```http
PATCH /api/v1/users/me
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "name": "홍길동",
  "profileImage": "https://..."
}

Response: 200 OK
{
  "id": "uuid",
  "name": "홍길동",
  "profileImage": "https://..."
}
```

#### AI 도우미

```http
POST /api/v1/ai/chat
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "message": "복지카드는 어떻게 신청하나요?",
  "conversationId": "uuid"  // 선택: 대화 이어가기
}

Response: 200 OK
{
  "conversationId": "uuid",
  "answer": "복지카드는 인사팀 포털에서...",
  "relatedDocuments": [
    {
      "id": "doc-001",
      "title": "복지카드 신청 가이드",
      "url": "/documents/doc-001"
    }
  ],
  "timestamp": "2024-03-16T10:30:00Z"
}
```

```http
GET /api/v1/ai/conversations/{conversationId}
Authorization: Bearer {token}

Response: 200 OK
{
  "id": "uuid",
  "messages": [
    {
      "role": "user",
      "content": "복지카드는 어떻게 신청하나요?",
      "timestamp": "2024-03-16T10:30:00Z"
    },
    {
      "role": "assistant",
      "content": "복지카드는 인사팀 포털에서...",
      "timestamp": "2024-03-16T10:30:05Z"
    }
  ]
}
```

#### 체크리스트

```http
GET /api/v1/checklists
Authorization: Bearer {token}
Query: week=1

Response: 200 OK
{
  "week": 1,
  "items": [
    {
      "id": "check-001",
      "title": "복지카드 신청",
      "description": "인사팀 포털에서 복지카드를 신청하세요",
      "category": "행정",
      "completed": true,
      "completedAt": "2024-03-02T14:00:00Z"
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
```

```http
POST /api/v1/checklists/{checklistId}/complete
Authorization: Bearer {token}

Response: 200 OK
{
  "id": "check-001",
  "completed": true,
  "completedAt": "2024-03-16T10:30:00Z"
}
```

#### 기록 (Records)

```http
POST /api/v1/records
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "title": "첫 주 회고",
  "content": "이번 주에는 개발환경을 세팅하고...",
  "type": "WEEKLY_REVIEW",
  "tags": ["회고", "1주차"]
}

Response: 201 Created
{
  "id": "record-001",
  "title": "첫 주 회고",
  "content": "이번 주에는 개발환경을 세팅하고...",
  "aiSummary": "이번 주 주요 활동: 개발환경 세팅 완료, 팀원들과 첫 미팅...",
  "type": "WEEKLY_REVIEW",
  "tags": ["회고", "1주차"],
  "createdAt": "2024-03-16T10:30:00Z"
}
```

```http
GET /api/v1/records
Authorization: Bearer {token}
Query: type=WEEKLY_REVIEW&page=0&size=10

Response: 200 OK
{
  "content": [...],
  "totalElements": 25,
  "totalPages": 3,
  "size": 10,
  "number": 0
}
```

#### 리포트

```http
GET /api/v1/reports
Authorization: Bearer {token}
Query: page=0&size=10

Response: 200 OK
{
  "content": [
    {
      "id": "report-001",
      "week": 1,
      "title": "1주차 온보딩 리포트",
      "summary": "이번 주 주요 성과...",
      "fileUrl": "https://storage.../report-001.pdf",
      "createdAt": "2024-03-08T18:00:00Z"
    }
  ],
  "totalElements": 4,
  "totalPages": 1
}
```

```http
POST /api/v1/reports/generate
Authorization: Bearer {token}

Response: 202 Accepted
{
  "message": "리포트 생성이 시작되었습니다",
  "estimatedTime": "약 1분 소요"
}
```

#### 문서 관리

```http
GET /api/v1/documents
Authorization: Bearer {token}
Query: category=HR&page=0&size=20

Response: 200 OK
{
  "content": [
    {
      "id": "doc-001",
      "title": "복지카드 신청 가이드",
      "category": "HR",
      "fileUrl": "https://storage.../doc-001.pdf",
      "fileSize": 1024000,
      "uploadedAt": "2024-01-15T09:00:00Z"
    }
  ],
  "totalElements": 45
}
```

```http
POST /api/v1/documents/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

Request:
- file: (binary)
- category: "HR"
- title: "신규 문서"

Response: 201 Created
{
  "id": "doc-new",
  "title": "신규 문서",
  "category": "HR",
  "fileUrl": "https://storage.../doc-new.pdf",
  "fileSize": 2048000
}
```

```http
GET /api/v1/documents/{documentId}/download
Authorization: Bearer {token}

Response: 200 OK
{
  "downloadUrl": "https://storage.../presigned-url?expires=...",
  "expiresIn": 600
}
```

### 6.3 AI 서버 내부 API (Backend ↔ AI)

```http
POST http://ai-server:5000/chat
Content-Type: application/json
X-Internal-Secret: {shared_secret}

Request:
{
  "message": "복지카드는 어떻게 신청하나요?",
  "userId": "uuid",
  "context": []
}

Response: 200 OK
{
  "answer": "복지카드는 인사팀 포털에서...",
  "relatedDocuments": ["doc-001", "doc-015"],
  "confidence": 0.92
}
```

```http
POST http://ai-server:5000/summarize
Content-Type: application/json
X-Internal-Secret: {shared_secret}

Request:
{
  "text": "이번 주에는 개발환경을 세팅하고...",
  "maxLength": 200
}

Response: 200 OK
{
  "summary": "주요 활동: 개발환경 세팅 완료, 팀 미팅 참석..."
}
```

```http
POST http://ai-server:5000/generate-report
Content-Type: application/json
X-Internal-Secret: {shared_secret}

Request:
{
  "userId": "uuid",
  "week": 1,
  "activities": {
    "completedChecklists": [...],
    "records": [...],
    "chatHistory": [...]
  }
}

Response: 200 OK
{
  "report": "# 1주차 온보딩 리포트\n\n## 주요 성과\n...",
  "format": "markdown"
}
```

### 6.4 에러 응답 표준

```json
{
  "timestamp": "2024-03-16T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "사원번호는 7자리 숫자여야 합니다",
  "path": "/api/v1/auth/login",
  "errors": [
    {
      "field": "employeeNumber",
      "message": "사원번호는 7자리 숫자여야 합니다",
      "rejectedValue": "123"
    }
  ]
}
```

**표준 HTTP 상태 코드**:
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

## 7. 모니터링 & 로깅

### 7.1 애플리케이션 모니터링

#### Spring Boot Actuator

**활성화 엔드포인트**:
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - metrics
          - info
          - prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

**주요 메트릭**:
- CPU, 메모리 사용량
- JVM 힙 메모리
- HTTP 요청 수, 응답 시간
- 데이터베이스 커넥션 풀
- 캐시 히트율

#### 헬스체크 엔드포인트

```http
GET /actuator/health

Response:
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500000000000,
        "free": 250000000000
      }
    }
  }
}
```

### 7.2 에러 트래킹

#### 추천: Sentry

**장점**:
- 실시간 에러 알림
- 에러 그룹핑 및 우선순위
- 사용자 영향도 추적
- 스택 트레이스 자동 수집
- 무료 티어 제공 (월 5,000 이벤트)

**Backend 설정**:
```yaml
# application.yml
sentry:
  dsn: ${SENTRY_DSN}
  environment: ${SPRING_PROFILES_ACTIVE}
  traces-sample-rate: 0.1
  send-default-pii: false
```

**Frontend 설정**:
```javascript
// main.jsx
import * as Sentry from "@sentry/react";

Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,
  environment: import.meta.env.VITE_APP_ENV,
  integrations: [
    new Sentry.BrowserTracing(),
    new Sentry.Replay()
  ],
  tracesSampleRate: 0.1,
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0
});
```

**대안**:
- Rollbar
- Bugsnag
- Datadog APM

### 7.3 로깅 전략

#### 로그 레벨

| 환경 | Level | 설명 |
|------|-------|------|
| Development | DEBUG | 모든 로그 출력 |
| Staging | INFO | 일반 정보 이상 |
| Production | WARN | 경고 및 에러만 |

#### 로그 포맷 (JSON)

```json
{
  "timestamp": "2024-03-16T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.withbuddy.api.AuthController",
  "message": "User login successful",
  "userId": "uuid",
  "employeeNumber": "2024001",
  "ip": "203.0.113.1",
  "userAgent": "Mozilla/5.0...",
  "requestId": "req-12345",
  "duration": 234
}
```

#### 로그 저장소

**옵션 1: GitHub (개발/테스트)**
- GitHub Actions 워크플로우 로그
- 제한: 90일 보관
- 용도: CI/CD 로그, 테스트 로그

**옵션 2: CloudWatch Logs (AWS 선택 시)**
- 중앙집중식 로그 관리
- 로그 그룹별 보관 기간 설정
- CloudWatch Insights로 쿼리

**옵션 3: ELK Stack (권장)**
```
[Application] → [Logstash] → [Elasticsearch] → [Kibana]
```

**장점**:
- 강력한 검색 및 필터링
- 실시간 대시보드
- 로그 집계 및 분석

#### 로그 수집 아키텍처

```
[Backend/AI Servers]
   ↓ Filebeat
[Logstash]
   ↓ 파싱 & 변환
[Elasticsearch]
   ↓ 시각화
[Kibana Dashboard]
```

### 7.4 성능 모니터링

#### 추천 도구

**APM (Application Performance Monitoring)**:
1. **Datadog APM** (유료, 권장)
   - 분산 트레이싱
   - 실시간 성능 메트릭
   - 인프라 모니터링 통합

2. **New Relic** (유료)
   - 자동화된 성능 분석
   - 사용자 경험 모니터링

3. **Pinpoint** (오픈소스, 무료)
   - 네이버 개발
   - Java 애플리케이션 특화
   - 무료

#### 주요 모니터링 지표

**Backend**:
- API 응답 시간 (p50, p95, p99)
- 초당 요청 수 (RPS)
- 에러율
- 데이터베이스 쿼리 시간
- AI API 호출 시간

**Frontend**:
- First Contentful Paint (FCP)
- Largest Contentful Paint (LCP)
- Time to Interactive (TTI)
- Cumulative Layout Shift (CLS)

**Infrastructure**:
- CPU 사용률
- 메모리 사용률
- 디스크 I/O
- 네트워크 대역폭

### 7.5 알림 (Alerting)

#### 알림 채널
- Slack (권장)
- 이메일
- PagerDuty (온콜 대응 필요 시)

#### 알림 규칙 예시

```yaml
alerts:
  - name: High Error Rate
    condition: error_rate > 5%
    duration: 5m
    severity: critical
    channels: [slack, email]
    
  - name: Slow API Response
    condition: p95_response_time > 2s
    duration: 10m
    severity: warning
    channels: [slack]
    
  - name: Database Connection Pool Exhausted
    condition: db_pool_usage > 90%
    duration: 2m
    severity: critical
    channels: [slack, email]
    
  - name: High Memory Usage
    condition: memory_usage > 85%
    duration: 15m
    severity: warning
    channels: [slack]
```

---

## 8. 배포 전략

### 8.1 환경 구성

```
Development → Staging → Production
```

#### Development (개발)
- **목적**: 로컬 개발 및 기능 테스트
- **인프라**: 로컬 머신
- **데이터베이스**: 로컬 MySQL
- **배포**: 수동 실행

#### Staging (스테이징)
- **목적**: 프로덕션 배포 전 최종 검증
- **인프라**: 프로덕션과 동일한 클라우드 환경 (축소 버전)
- **데이터베이스**: 별도 Staging DB (프로덕션 데이터 복제본)
- **배포**: GitHub Actions (develop 브랜치)

#### Production (프로덕션)
- **목적**: 실제 서비스 운영
- **인프라**: 풀스펙 클라우드 환경
- **데이터베이스**: 프로덕션 DB (백업 및 이중화)
- **배포**: GitHub Actions (main 브랜치)

### 8.2 CI/CD 파이프라인

#### GitHub Actions 워크플로우

**Backend CI/CD**:
```yaml
# .github/workflows/backend-deploy.yml
name: Backend Deploy

on:
  push:
    branches: [ main ]
    paths:
      - 'backend/**'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run tests
        run: |
          cd backend
          ./gradlew test
      
  build-and-deploy:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build JAR
        run: |
          cd backend
          ./gradlew bootJar
      - name: Deploy to Server
        run: |
          # SCP로 JAR 파일 전송
          # SSH로 서버 재시작
```

**Frontend CI/CD** (Vercel):
```yaml
# vercel.json
{
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "framework": "vite",
  "env": {
    "VITE_API_BASE_URL": "@api-base-url"
  }
}
```

- Vercel GitHub 연동으로 자동 배포
- main 브랜치: 프로덕션 배포
- develop 브랜치: 프리뷰 배포

**AI Service CI/CD**:
```yaml
# .github/workflows/ai-deploy.yml
name: AI Service Deploy

on:
  push:
    branches: [ main ]
    paths:
      - 'ai/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to Server
        run: |
          # requirements.txt 업데이트
          # 서비스 재시작
```

### 8.3 배포 프로세스

#### 배포 흐름

```
[개발자] 코드 작성
   ↓ git commit
   ↓ git push origin feature/xxx
[GitHub]
   ↓ PR 생성
[CI] 자동 테스트 실행
   ↓ 테스트 통과
[코드 리뷰]
   ↓ 승인
[Merge to develop]
   ↓ 자동 배포
[Staging 환경]
   ↓ QA 테스트
   ↓ 승인
[Merge to main]
   ↓ 자동 배포
[Production 환경]
```

#### 배포 체크리스트

**배포 전**:
- [ ] 모든 테스트 통과
- [ ] 코드 리뷰 완료
- [ ] 데이터베이스 마이그레이션 스크립트 준비
- [ ] 환경변수 확인
- [ ] 롤백 계획 수립

**배포 중**:
- [ ] 서비스 헬스체크
- [ ] 로그 모니터링
- [ ] 성능 지표 확인

**배포 후**:
- [ ] 주요 기능 Smoke Test
- [ ] 에러율 모니터링 (30분)
- [ ] 사용자 피드백 확인
- [ ] 배포 완료 공지

### 8.4 무중단 배포 (Zero-Downtime)

#### Blue-Green 배포

```
[Load Balancer]
   ↓ 100% 트래픽
[Blue (현재 버전 v1.0)]

배포 시작:
[Load Balancer]
   ↓ 100% 트래픽
[Blue (v1.0)]
[Green (v1.1)] ← 새 버전 배포 & 헬스체크

검증 완료 후:
[Load Balancer]
   ↓ 100% 트래픽 전환
[Green (v1.1)]
[Blue (v1.0)] ← 대기 (롤백 가능)
```

#### 롤링 배포

```
인스턴스: [A] [B] [C]

Step 1: [A (v1.1)] [B (v1.0)] [C (v1.0)]
Step 2: [A (v1.1)] [B (v1.1)] [C (v1.0)]
Step 3: [A (v1.1)] [B (v1.1)] [C (v1.1)]
```

### 8.5 데이터베이스 마이그레이션

#### Flyway 사용 (권장)

```sql
-- V1__init.sql
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    employee_number VARCHAR(7) UNIQUE NOT NULL,
    ...
);

-- V2__add_onboarding_progress.sql
ALTER TABLE users ADD COLUMN onboarding_progress DECIMAL(5,2) DEFAULT 0.0;
```

**설정**:
```yaml
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

**마이그레이션 전략**:
1. 항상 Backward Compatible 유지
2. 컬럼 삭제는 2단계로 진행:
   - v1: 컬럼 사용 중단 (deprecated)
   - v2: 컬럼 삭제
3. Staging에서 먼저 테스트
4. 마이그레이션 전 백업 필수

### 8.6 백업 & 복구

#### 데이터베이스 백업

**자동 백업**:
```bash
# cron: 매일 새벽 3시
0 3 * * * /scripts/backup-mysql.sh
```

```bash
#!/bin/bash
# backup-mysql.sh
DATE=$(date +%Y%m%d_%H%M%S)
mysqldump -u $DB_USER -p$DB_PASSWORD withbuddy \
  | gzip > /backups/withbuddy_$DATE.sql.gz

# S3/GCS/OCI에 업로드
aws s3 cp /backups/withbuddy_$DATE.sql.gz \
  s3://withbuddy-backups/db/

# 30일 이상 된 백업 삭제
find /backups -name "*.sql.gz" -mtime +30 -delete
```

**보관 정책**:
- 일일 백업: 30일 보관
- 주간 백업: 3개월 보관
- 월간 백업: 1년 보관

#### 복구 절차

```bash
# 1. 서비스 중지
systemctl stop withbuddy-backend

# 2. 백업 복원
gunzip < withbuddy_20240316.sql.gz | mysql -u root -p withbuddy

# 3. 서비스 시작
systemctl start withbuddy-backend

# 4. 헬스체크
curl http://localhost:8080/actuator/health
```

---

## 9. 확장성 & 성능 최적화

### 9.1 수평 확장 (Horizontal Scaling)

#### Backend 확장
```
[Load Balancer]
   ├─→ [Backend Instance 1]
   ├─→ [Backend Instance 2]
   └─→ [Backend Instance 3]
        ↓
   [Shared: MySQL, Redis]
```

**고려사항**:
- Stateless 설계 (세션은 Redis에 저장)
- 파일 업로드는 Object Storage 사용
- 스케줄러는 단일 인스턴스에서만 실행 (ShedLock 사용)

#### AI Server 확장
- OpenAI API 호출은 병렬 처리 가능
- 벡터 검색 결과 캐싱
- 요청 큐잉 (RabbitMQ/SQS)

### 9.2 캐싱 전략

#### Redis 캐시 레이어

**캐시 대상**:
1. **사용자 정보** (TTL: 1시간)
2. **문서 목록** (TTL: 10분)
3. **AI 응답** (TTL: 24시간, 동일 질문)
4. **체크리스트** (TTL: 1시간)

```java
@Cacheable(value = "user", key = "#userId")
public User findUserById(String userId) {
    return userRepository.findById(userId);
}

@CacheEvict(value = "user", key = "#userId")
public void updateUser(String userId, UserUpdateRequest request) {
    // ...
}
```

### 9.3 데이터베이스 최적화

#### 인덱스 전략

```sql
-- 사용자 조회 (로그인)
CREATE INDEX idx_employee_number ON users(employee_number);

-- 체크리스트 조회
CREATE INDEX idx_user_week ON checklists(user_id, week);

-- 문서 검색
CREATE INDEX idx_document_category ON documents(category, created_at);

-- 리포트 조회
CREATE INDEX idx_report_user_week ON reports(user_id, week);
```

#### 쿼리 최적화
- N+1 문제 해결: `@EntityGraph`, `join fetch` 사용
- 페이지네이션: `Pageable` 사용
- Bulk 연산: `@Modifying` 쿼리

#### 커넥션 풀 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 9.4 CDN 활용

**Vercel Edge Network**:
- 정적 파일 자동 CDN 배포
- 전 세계 엣지 노드

**Cloudflare CDN**:
- API 응답 캐싱 (선택적)
- 이미지 최적화
- DDoS 방어

---

## 부록

### A. 기술 스택 버전 정보

| 카테고리 | 기술 | 버전 |
|---------|------|------|
| Backend | Java | 21 |
| | Spring Boot | 3.5.11 |
| | MySQL | 8.0 |
| Frontend | React | 18+ |
| | Vite | 최신 |
| AI | Python | 3.10+ |
| | FastAPI | 최신 |
| Infrastructure | Redis | 7+ |

### B. 환경변수 체크리스트

**Backend**:
- [ ] `SPRING_DATASOURCE_URL`
- [ ] `SPRING_DATASOURCE_USERNAME`
- [ ] `SPRING_DATASOURCE_PASSWORD`
- [ ] `JWT_SECRET`
- [ ] `REDIS_URL`
- [ ] `AWS_ACCESS_KEY_ID` (Object Storage)
- [ ] `AWS_SECRET_ACCESS_KEY`
- [ ] `SENTRY_DSN`

**Frontend**:
- [ ] `VITE_API_BASE_URL`
- [ ] `VITE_SENTRY_DSN`

**AI**:
- [ ] `OPENAI_API_KEY`
- [ ] `REDIS_URL`
- [ ] `PINECONE_API_KEY` (벡터 DB)

### C. 참고 문서

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [React Documentation](https://react.dev/)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
- [MySQL 8.0 Reference](https://dev.mysql.com/doc/refman/8.0/en/)

### D. 용어 정리

- **VCN**: Virtual Cloud Network, 클라우드 내 가상 네트워크
- **JWT**: JSON Web Token, 토큰 기반 인증
- **CORS**: Cross-Origin Resource Sharing, 교차 출처 리소스 공유
- **Rate Limiting**: API 호출 빈도 제한
- **CDN**: Content Delivery Network, 콘텐츠 전송 네트워크
- **APM**: Application Performance Monitoring, 애플리케이션 성능 모니터링

---

**문서 버전**: 1.0.0  
**작성일**: 2026-03-16  
**다음 리뷰 예정**: 2026-04-16
