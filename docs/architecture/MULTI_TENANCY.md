# WithBuddy 멀티 테넌시 아키텍처

> 여러 회사가 동시에 사용하는 SaaS 구조

**최종 업데이트**: 2026-04-30
**버전**: 0.43.2

---

## 📋 목차

- [1. 개요](#1-개요)
- [2. 테넌트 식별](#2-테넌트-식별)
- [3. 데이터베이스 스키마](#3-데이터베이스-스키마)
- [4. 인증 흐름](#4-인증-흐름)
- [5. 데이터 격리 전략](#5-데이터-격리-전략)
- [6. 회사별 설정](#6-회사별-설정)
- [7. 마이그레이션 전략](#7-마이그레이션-전략)

---

## 1. 개요

### 1.1 멀티 테넌시란?

WithBuddy는 **여러 회사가 동시에 사용**하는 SaaS 서비스로 설계되었습니다.

#### 멀티 테넌시 특징
- 🏢 **여러 회사(테넌트) 지원**: 각 회사는 고유한 `companyCode`로 식별
- 🔒 **데이터 격리**: 회사별 데이터 완전 분리
- 👥 **사원번호 중복 허용**: 다른 회사는 같은 사원번호 사용 가능
- 📊 **회사별 커스터마이징**: 체크리스트, 문서, 설정 등 회사별 관리

### 1.2 사용 예시

```
회사 A (테크 주식회사, companyCode: 1001)
├── 김지원 (사원번호: 20260001)
│   ├── 체크리스트 10개
│   └── 기록 25개
└── 박민수 (사원번호: 20260002)

회사 B (제조 주식회사, companyCode: 1002)  
├── 이영희 (사원번호: 20260001)  ← 같은 사번!
│   ├── 체크리스트 8개
│   └── 기록 30개
└── 최철수 (사원번호: 20260002)
```

---

## 2. 테넌트 식별

### 2.1 Company 모델

```java
@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private Integer companyCode;  // 회사 고유 코드 (예: 1001)
    
    @Column(nullable = false, length = 100)
    private String companyName;   // 회사명
    
    @Column(length = 50)
    private String industry;      // 업종
    
    @Column(nullable = false)
    private Boolean active = true; // 활성 상태
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 2.2 User 모델 (멀티 테넌시)

```java
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_company_employee",
            columnNames = {"company_id", "employee_number"}
        )
    }
)
public class User {
    @Id
    private String id;  // UUID
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    
    @Column(name = "employee_number", nullable = false, length = 20)
    private String employeeNumber;  // 회사 내에서만 고유
    
    @Column(nullable = false, length = 50)
    private String name;
    
    @Column(length = 50)
    private String department;
    
    @Column(length = 50)
    private String position;
    
    @Enumerated(EnumType.STRING)
    private Role role = Role.EMPLOYEE;
    
    private LocalDate joinDate;
    private String profileImage;
    
    // UNIQUE: (company_id, employee_number)
    // 같은 회사 내에서 사원번호는 고유
    // 다른 회사는 같은 사원번호 사용 가능
}
```

---

## 3. 데이터베이스 스키마

### 3.1 ERD (핵심 테이블)

```
┌─────────────────┐
│   companies     │
├─────────────────┤
│ id (PK)         │
│ company_code    │◄─────┐
│ company_name    │      │
│ industry        │      │
│ active          │      │
└─────────────────┘      │
                         │ FK
                         │
┌─────────────────┐      │
│     users       │      │
├─────────────────┤      │
│ id (PK)         │      │
│ company_id ─────┼──────┘
│ employee_number │
│ name            │
│ department      │
│ position        │
│ role            │
│ join_date       │
└─────────────────┘
        │ FK
        │
        ├──────────────────┬──────────────────┬──────────────────┐
        │                  │                  │                  │
        ↓                  ↓                  ↓                  ↓
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  checklists     │  │    records      │  │    reports      │  │   documents     │
├─────────────────┤  ├─────────────────┤  ├─────────────────┤  ├─────────────────┤
│ id (PK)         │  │ id (PK)         │  │ id (PK)         │  │ id (PK)         │
│ user_id (FK)────┤  │ user_id (FK)────┤  │ user_id (FK)────┤  │ company_id (FK)─┤
│ company_id (FK)─┤  │ company_id (FK)─┤  │ company_id (FK)─┤  │ category        │
│ week            │  │ title           │  │ week            │  │ title           │
│ items (JSON)    │  │ content         │  │ summary         │  │ file_url        │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────────┘
```

**중요**: 모든 주요 데이터 테이블에 `company_id` FK 추가

### 3.2 SQL 스키마

#### Companies 테이블

```sql
CREATE TABLE companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_code INT UNIQUE NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    industry VARCHAR(50),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_company_code (company_code),
    INDEX idx_active (active)
);
```

#### Users 테이블

```sql
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    company_id BIGINT NOT NULL,
    employee_number VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    department VARCHAR(50),
    position VARCHAR(50),
    role VARCHAR(20) DEFAULT 'EMPLOYEE',
    join_date DATE,
    profile_image VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id),
    UNIQUE KEY uk_company_employee (company_id, employee_number),
    INDEX idx_company_id (company_id)
);
```

#### Checklists 테이블

```sql
CREATE TABLE checklists (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    company_id BIGINT NOT NULL,
    week INT NOT NULL,
    title VARCHAR(100),
    items JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (company_id) REFERENCES companies(id),
    INDEX idx_company_week (company_id, week),
    INDEX idx_user_week (user_id, week)
);
```

---

## 4. 인증 흐름

### 4.1 로그인 프로세스

```
[사용자]
   ↓ (1) 회사코드 + 사원번호 + 이름 입력
   {
     "companyCode": 1001,
     "name": "김지원",
     "employeeNumber": "20260001"
   }
[Frontend]
   ↓ (2) POST /api/v1/auth/login
[Backend]
   ↓ (3) Company 테이블에서 companyCode로 회사 조회
   SELECT * FROM companies WHERE company_code = 1001
   
   ↓ (4) User 테이블에서 (company_id, employee_number) 조회
   SELECT * FROM users 
   WHERE company_id = 1 AND employee_number = '20260001'
   
   ↓ (5) name 일치 여부 검증
   if (user.name != "김지원") throw Exception
   
   ↓ (6) JWT 생성 (companyCode 포함)
   {
     "sub": "user-uuid-123",
     "companyCode": 1001,
     "companyId": 1,
     "employeeNumber": "20260001",
     "name": "김지원",
     "role": "EMPLOYEE"
   }
[Frontend]
   ↓ (7) JWT 저장 (localStorage)
[사용자]
```

### 4.2 JWT Payload (멀티 테넌시)

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

---

## 5. 데이터 격리 전략

### 5.1 Row Level Security (행 수준 보안)

모든 API 요청은 JWT에서 `companyId`를 추출하여 자동으로 필터링:

```java
@RestController
@RequestMapping("/api/v1/checklists")
public class ChecklistController {
    
    @GetMapping
    public ResponseEntity<?> getChecklists(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long companyId = userDetails.getCompanyId();
        
        // 자동으로 해당 회사의 데이터만 조회
        List<Checklist> checklists = checklistRepository
            .findByCompanyId(companyId);
        
        return ResponseEntity.ok(checklists);
    }
    
    @PostMapping
    public ResponseEntity<?> createChecklist(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestBody ChecklistRequest request
    ) {
        Long companyId = userDetails.getCompanyId();
        
        // 생성 시 자동으로 companyId 설정
        Checklist checklist = new Checklist();
        checklist.setCompanyId(companyId);
        checklist.setUserId(userDetails.getUserId());
        // ...
        
        return ResponseEntity.ok(checklistRepository.save(checklist));
    }
}
```

### 5.2 JPA Repository 패턴

```java
public interface ChecklistRepository extends JpaRepository<Checklist, String> {
    
    // 회사별 조회
    List<Checklist> findByCompanyId(Long companyId);
    
    // 회사 + 사용자별 조회
    List<Checklist> findByCompanyIdAndUserId(Long companyId, String userId);
    
    // 회사 + 주차별 조회
    List<Checklist> findByCompanyIdAndWeek(Long companyId, Integer week);
}

public interface RecordRepository extends JpaRepository<Record, String> {
    
    List<Record> findByCompanyIdAndUserId(Long companyId, String userId);
    
    List<Record> findByCompanyIdAndUserIdAndType(
        Long companyId, 
        String userId, 
        RecordType type
    );
}
```

### 5.3 Service Layer 예시

```java
@Service
@RequiredArgsConstructor
public class ChecklistService {
    
    private final ChecklistRepository checklistRepository;
    
    public List<Checklist> getChecklists(Long companyId, String userId) {
        // 자동으로 회사별 필터링
        return checklistRepository.findByCompanyIdAndUserId(companyId, userId);
    }
    
    public Checklist createChecklist(Long companyId, String userId, ChecklistRequest request) {
        Checklist checklist = new Checklist();
        checklist.setCompanyId(companyId);  // 필수!
        checklist.setUserId(userId);
        checklist.setWeek(request.getWeek());
        // ...
        
        return checklistRepository.save(checklist);
    }
}
```

---

## 6. 회사별 설정

### 6.1 CompanySettings 모델

```java
@Entity
@Table(name = "company_settings")
public class CompanySettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "company_id")
    private Company company;
    
    // 온보딩 기간 (주 단위)
    private Integer onboardingWeeks = 12;
    
    // 체크리스트 템플릿 ID
    private String checklistTemplateId;
    
    // AI 기능 활성화 여부
    private Boolean aiEnabled = true;
    
    // 리포트 자동 생성 주기 (주 단위)
    private Integer reportGenerationInterval = 1;
    
    // 로고 URL
    private String logoUrl;
    
    // 테마 색상
    private String primaryColor = "#3B82F6";
}
```

### 6.2 API 예시

```http
GET /api/v1/companies/me
Authorization: Bearer {token}

Response:
{
  "id": 1,
  "companyCode": 1001,
  "companyName": "테크 주식회사",
  "industry": "IT",
  "settings": {
    "onboardingWeeks": 12,
    "aiEnabled": true,
    "logoUrl": "https://storage.../logo.png",
    "primaryColor": "#3B82F6"
  }
}
```

---

## 7. 마이그레이션 전략

### 7.1 단계별 마이그레이션

#### Phase 1: 스키마 변경

```sql
-- companies 테이블 생성
CREATE TABLE companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_code INT UNIQUE NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    industry VARCHAR(50),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- users 테이블에 company_id 추가
ALTER TABLE users ADD COLUMN company_id BIGINT NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_user_company 
    FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE users ADD UNIQUE KEY uk_company_employee (company_id, employee_number);

-- 기타 테이블에 company_id 추가
ALTER TABLE checklists ADD COLUMN company_id BIGINT NOT NULL;
ALTER TABLE records ADD COLUMN company_id BIGINT NOT NULL;
ALTER TABLE reports ADD COLUMN company_id BIGINT NOT NULL;
ALTER TABLE documents ADD COLUMN company_id BIGINT NOT NULL;

-- 인덱스 추가
CREATE INDEX idx_checklists_company ON checklists(company_id);
CREATE INDEX idx_records_company ON records(company_id);
CREATE INDEX idx_reports_company ON reports(company_id);
CREATE INDEX idx_documents_company ON documents(company_id);
```

#### Phase 2: 기존 데이터 마이그레이션

```sql
-- 기본 회사 생성
INSERT INTO companies (company_code, company_name, industry) 
VALUES (1001, 'Default Company', 'IT');

-- 기존 사용자에 회사 할당
UPDATE users SET company_id = 1 WHERE company_id IS NULL;

-- 기존 데이터에 company_id 할당
UPDATE checklists c
INNER JOIN users u ON c.user_id = u.id
SET c.company_id = u.company_id
WHERE c.company_id IS NULL;

UPDATE records r
INNER JOIN users u ON r.user_id = u.id
SET r.company_id = u.company_id
WHERE r.company_id IS NULL;
```

#### Phase 3: 애플리케이션 코드 업데이트

- JWT에 companyCode 추가
- 모든 Repository에 companyId 필터 추가
- API 인증 로직 수정

---

## 8. 멀티 테넌시 장점

### 8.1 비즈니스 측면
- 📈 **확장성**: 무제한 회사 추가 가능
- 💰 **수익 모델**: 회사당 구독 과금
- 🎯 **타겟 다양화**: 대기업부터 스타트업까지

### 8.2 기술 측면
- 🔒 **보안**: 회사별 데이터 완전 격리
- 🛠️ **유지보수**: 단일 코드베이스 관리
- ⚡ **효율성**: 인프라 공유로 비용 절감

### 8.3 사용자 측면
- 🏢 **독립성**: 각 회사만의 설정 가능
- 📊 **커스터마이징**: 회사별 체크리스트, 문서
- 🔐 **프라이버시**: 타 회사 데이터 접근 불가

---

## 부록

### A. 체크리스트

**멀티 테넌시 구현 체크리스트**:

- [ ] Company 테이블 생성
- [ ] User 테이블에 company_id FK 추가
- [ ] 모든 데이터 테이블에 company_id FK 추가
- [ ] UNIQUE 제약조건 수정 (company_id 포함)
- [ ] JWT에 companyCode, companyId 추가
- [ ] Repository 메서드에 companyId 필터 추가
- [ ] Service Layer에서 자동 companyId 설정
- [ ] API 인증 로직 수정
- [ ] 테스트 코드 작성

### B. 참고 자료

- [멀티 테넌시 디자인 패턴](https://docs.microsoft.com/en-us/azure/architecture/guide/multitenant/overview)
- [Spring Data JPA Multi-tenancy](https://spring.io/blog/2022/07/31/how-to-integrate-hibernates-multitenant-feature-with-spring-data-jpa)

---

**문서 버전**: 1.0.0  
**작성일**: 2026-03-17
