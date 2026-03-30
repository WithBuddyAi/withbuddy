# WithBuddy Backend

> Java Spring Boot 기반 백엔드 서버

## 👥 담당팀
**@WithBuddyAi/backend-developers**
- @김지원(백엔드 9회차)
- @홍성하(백엔드 9회차)

## 🛠 기술 스택

### Core
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.11
- **Build Tool**: Gradle (Groovy)
- **Configuration**: YAML

### Dependencies
- **Lombok**: 코드 간소화
- **Spring Web**: RESTful API
- **Spring Security**: 인증/인가
- **Spring Data JPA**: ORM
- **MySQL Driver**: DB 연결

### Database
- **MySQL 8.0**

### Authentication
- **Spring Security + JWT**

### API Documentation
- **Swagger (SpringDoc OpenAPI)**

### Development Tools
- **IntelliJ IDEA**
- **Postman**: API 테스트

### External Communication
- **FastAPI (AI 서버)**: REST API 통신 (팀장 준수님과 추가 논의 필요)

## 🧭 프로젝트 표준

| 항목 | 값 |
|------|-----|
| 디렉토리 | `backend/` |
| 프로젝트명 | `withbuddy` |
| Group/Package | `com.withbuddy` |
| Build | Gradle (Groovy) |
| Java | 21 |
| 기본 포트 | 8080 |

## 📁 프로젝트 구조

```
backend/
├── src/main/java/com/withbuddy/
│   ├── domain/             # 도메인별 패키지
│   │   ├── user/           # 사용자 관리
│   │   ├── buddy/          # AI 버디
│   │   └── chat/           # 채팅
│   ├── global/             # 공통 기능
│   │   ├── config/         # 설정 (Security, Swagger, JPA)
│   │   ├── security/       # JWT 인증/인가
│   │   ├── exception/      # 전역 예외 처리
│   │   └── common/         # 공통 DTO/Entity
│   └── external/           # 외부 API
│       └── ai/             # FastAPI 통신
└── src/main/resources/
    ├── application.yml
    ├── application-local.yml
    ├── application-dev.yml
    └── application-prod.yml
```

## 🚀 시작하기

### 1. 사전 요구사항
```bash
# Java 21 설치 확인
java -version

# Gradle 설치 확인 (또는 gradlew 사용)
gradle -version
```

### 2. 프로젝트 클론
```bash
git clone https://github.com/WithBuddyAi/withbuddy.git
cd withbuddy/backend
```

### 3. 환경 변수 설정
`src/main/resources/application-local.yml` 파일 생성:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/withbuddy
    username: your_username
    password: ${DB_PASSWORD} // 환경변수 활용
    
jwt:
  secret: your_jwt_secret_key_here
  expiration: 86400000  # 24시간 (밀리초)
```

> ⚠️ **중요**: `application-local.yml` 파일은 `.gitignore`에 포함되어 있습니다. 절대 커밋하지 마세요!

### 4. MySQL 데이터베이스 생성
```sql
CREATE DATABASE withbuddy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 5. 애플리케이션 실행
```bash
# Gradle Wrapper 사용
./gradlew bootRun

# 또는 IntelliJ에서 직접 실행
# WithbuddyApplication.java 파일 우클릭 → Run
```

### 6. API 문서 확인
서버 실행 후 브라우저에서:
```
http://localhost:8080/swagger-ui.html
```

## 🔧 개발 가이드

### 브랜치 전략
```
main (프로덕션)
  └── develop (개발)
       ├── feature/user-auth       # 사용자 인증
       ├── feature/buddy-service   # AI 버디 기능
       ├── feature/chat-system     # 채팅 시스템
       ├── release/1.0.0           # 배포 준비
       └── hotfix/1.0.1-fix-login  # 긴급 수정
```

- 기능 브랜치는 `develop`에서 분기
- 긴급 수정은 `main`에서 `hotfix/*`로 분기
- 운영 반영 후 `main` 변경은 반드시 `develop`에도 동기화

### 커밋 컨벤션
```
feat: 새로운 기능 추가
fix: 버그 수정
refactor: 코드 리팩토링
docs: 문서 수정
test: 테스트 코드 추가/수정
chore: 빌드 설정, 의존성 업데이트

예시:
feat: Add user registration API
fix: Resolve JWT token expiration issue
refactor: Improve user service code structure
```

### 코드 스타일
- **들여쓰기**: 4 spaces
- **네이밍**:
  - 클래스: PascalCase (`UserService`, `BuddyController`)
  - 메서드/변수: camelCase (`getUserById`, `userName`)
  - 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`, `API_VERSION`)
- **Lombok 활용**: `@Getter`, `@Builder`, `@NoArgsConstructor` 등

### API 설계 원칙
```
# RESTful API 규칙
GET    /api/v1/users          # 사용자 목록 조회
GET    /api/v1/users/{id}     # 특정 사용자 조회
POST   /api/v1/users          # 사용자 생성
PUT    /api/v1/users/{id}     # 사용자 전체 수정
PATCH  /api/v1/users/{id}     # 사용자 부분 수정
DELETE /api/v1/users/{id}     # 사용자 삭제
```

### 응답 형식
```json
{
  "success": true,
  "message": "요청이 성공적으로 처리되었습니다",
  "data": {
    "id": 1,
    "name": "홍길동"
  }
}
```

## 🔐 보안 가이드

### 환경 변수 관리
```yaml
# ✅ Good - 환경별 파일 분리
application-local.yml   # 로컬 개발 (gitignore)
application-dev.yml     # 개발 서버
application-prod.yml    # 프로덕션 (gitignore)

# ❌ Bad - 절대 커밋 금지!
- DB 비밀번호
- JWT Secret Key
- API Keys
```

### JWT 토큰 플로우
```
1. POST /api/v1/auth/login → JWT 토큰 발급
2. 이후 모든 요청 헤더에 포함: Authorization: Bearer {token}
3. 서버에서 토큰 검증 → 사용자 인증
```

## 📝 테스트

### 단위 테스트
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests UserServiceTest
```

### API 테스트 (Postman)
1. Postman Collection 가져오기 (docs/ 폴더 참고)
2. Environment 변수 설정 (baseUrl, token 등)
3. 테스트 실행

## 📚 참고 문서

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/3.5.11/reference/html/)
- [Spring Security + JWT Guide](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Swagger/SpringDoc](https://springdoc.org/)
- [JPA Best Practices](https://thorben-janssen.com/tips-to-boost-your-hibernate-performance/)

## 🐛 트러블슈팅

### MySQL 연결 오류
```
Error: Communications link failure
→ MySQL 서버 실행 확인
→ application.yml의 DB 정보 확인
```

### JWT 토큰 만료
```
Error: JWT expired
→ 로그인 다시 수행
→ jwt.expiration 설정 확인
```

### Port 충돌
```
Error: Port 8080 is already in use
→ application.yml에서 포트 변경
server:
  port: 8081
```

## 📞 문의

- 기술 문의: @WithBuddyAi/backend-developers
- 이슈 등록: GitHub Issues
- 긴급 문의: 개발 채널 https://discord.com/channels/1463843582187278431/1481226881104744561

---

**Last Updated**: 2026-03-24
