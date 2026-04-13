# WithBuddy Backend

> Java Spring Boot 기반 백엔드 서버

## 담당팀
**@WithBuddyAi/backend-developers**
- @김지원(백엔드 9회차)
- @홍성하(백엔드 9회차)

## 기술 스택

### Core
- **Language**: Java 21
- **Framework**: Spring Boot 3.5+
- **Build Tool**: Gradle (Groovy)
- **Configuration**: YAML

### Dependencies
- **Lombok**: 코드 간소화
- **Spring Web**: RESTful API
- **Spring Security**: 인증/인가
- **Spring Data JPA**: ORM
- **Flyway**: DB 마이그레이션 버전 관리
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

## 프로젝트 표준

| 항목 | 값 |
|------|-----|
| 디렉토리 | `backend/` |
| 프로젝트명 | `withbuddy` |
| Group/Package | `com.withbuddy` |
| Build | Gradle (Groovy) |
| Java | 21 |
| 기본 포트 | 8080 |

## 📁 프로젝트 구조

### 현재 구조 (As-Is)

```
backend/
└── src/main/java/com/withbuddy/
    ├── auth/
    └── global/
```

### 목표 구조 (To-Be, 팀 표준)

> 고도화 개발은 아래 구조를 기준으로 작성하고, 기존 코드는 기능 단위로 점진 전환한다.

```
backend/
└── src/main/java/com/withbuddy/
    ├── presentation/                  # API 입출력 계층
    │   ├── auth/
    │   │   ├── AuthController.java
    │   │   ├── request/
    │   │   └── response/
    │   └── chat/
    │       ├── ChatController.java
    │       ├── request/
    │       └── response/
    ├── application/                   # 유스케이스/트랜잭션 계층
    │   ├── auth/
    │   │   ├── AuthService.java
    │   │   └── AuthUseCase.java
    │   └── chat/
    │       ├── ChatService.java
    │       └── ChatUseCase.java
    ├── domain/                        # 핵심 도메인 계층
    │   ├── auth/
    │   │   ├── User.java
    │   │   ├── Company.java
    │   │   ├── UserRepository.java        # 도메인 포트(인터페이스)
    │   │   └── policy/
    │   └── chat/
    │       ├── ChatMessage.java
    │       ├── ChatRepository.java        # 도메인 포트(인터페이스)
    │       └── policy/
    ├── infrastructure/                # 외부 시스템 연동/구현체
    │   ├── persistence/
    │   │   ├── auth/
    │   │   │   ├── JpaUserRepository.java
    │   │   │   └── SpringDataUserRepository.java
    │   │   └── chat/
    │   ├── ai/
    │   │   └── AiClient.java
    │   ├── redis/
    │   └── mq/
    └── global/                        # 횡단 공통
        ├── config/
        ├── security/
        ├── exception/
        └── response/
```

### 폴더별 파일 규칙

| 폴더 | 포함 파일 | 주의 |
|------|-----------|-----------|
| `presentation/*` | `*Controller`, Request/Response DTO, API 어노테이션 | 요청/응답 처리만 담당 |
| `application/*` | `*Service`, `*UseCase`, 트랜잭션 처리 | 유스케이스 흐름만 담당 |
| `domain/*` | Entity, Value Object, Domain Service, Repository 인터페이스(Port) | 비즈니스 규칙만 담당 |
| `infrastructure/persistence/*` | JPA Repository 구현체, Query 구현, Mapper | DB/외부 연동만 담당 |
| `infrastructure/ai`, `infrastructure/redis`, `infrastructure/mq` | 외부 API/캐시/메시징 클라이언트 | 연동 코드만 담당 |
| `global/config` | Security, Swagger, Jackson, WebMvc, JPA 설정 | 전역 설정만 담당 |
| `global/exception` | 예외 클래스, 전역 예외 처리기 | 예외 처리만 담당 |
| `global/response` | 공통 응답 포맷, 에러 코드 | 공통 포맷만 담당 |

### 리소스 규칙

```
src/main/resources/
├── application.yaml         # 공통 기본값
├── application-local.yml    # 로컬 개발
├── application-prod.yml     # 운영
└── db/migration/            # Flyway 마이그레이션 스크립트 (V1~)
```

프로필/환경변수는 운영 가이드를 따른다. 상세는 `docs/guides/ENV.md`를 참조한다.

## 시작하기

### 1. 사전 요구사항
```bash
# Java 21 설치 확인
java -version

# Gradle 설치 확인 (또는 gradlew 사용)
gradle -version
```

### 2. 프로젝트 클론 (필요한 경우에만)
```bash
git clone https://github.com/WithBuddyAi/withbuddy.git
cd withbuddy/backend
```

> GitHub Actions CI/CD로 자동 배포를 운영 중이라면, 위 `git clone`은 **평소 배포 단계에서는 불필요**합니다.  
> 아래 상황에서만 필요합니다.
> - 로컬 개발 환경 최초 세팅
> - 신규 서버 초기 구축(최초 1회)
> - CI 장애 시 서버 수동 복구/긴급 배포

### 3. 환경 변수 설정
`src/main/resources/application-local.yml` 파일 생성:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/withbuddy
    username: your_username
    password: ${DB_PASSWORD} # 환경변수 활용
    
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

## 개발 가이드

### 브랜치 전략
```
main (프로덕션)
  └── develop (개발)
       ├── feature/SCRUM-68-user-auth      # 사용자 인증
       ├── feature/SCRUM-72-buddy-service  # AI 버디 기능
       ├── feature/SCRUM-75-chat-system    # 채팅 시스템
       └── hotfix/SCRUM-91-fix-login       # 긴급 수정
```

- 기능 브랜치는 `develop`에서 분기
- 긴급 수정은 `main`에서 `hotfix/*`로 분기
- 브랜치 이름은 `type/SCRUM-##-description` 형식을 사용
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

## 보안 가이드

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

## 테스트

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

## Backend CI/CD 자동배포

백엔드 자동배포 워크플로우는 `.github/workflows/backend-deploy.yml`을 사용한다.

### 1. GitHub Secrets (Environment: production) 등록

#### 현재 등록됨 (백엔드 관련)
- `AI_SERVER_BASE_URL` - AI 서버 URL
- `SPRING_DB_URL` - JDBC URL
- `SPRING_DB_USERNAME` - DB 계정
- `SPRING_DB_PASSWORD` - DB 비밀번호
- `BACKEND_SERVER_HOST` - SSH 배포 대상 호스트
- `BACKEND_SERVER_USER` - SSH 사용자
- `BACKEND_SERVER_SSH_KEY` - SSH 개인키
- `BACKEND_APP_DIR` - 배포 경로
- `BACKEND_HEALTH_URL` - 헬스체크 URL
- `JWT_SECRET` - JWT 시크릿(운영 6개월 주기 로테이션 권장)
- `REDIS_URL` - Redis 연결 URL
- `RABBITMQ_URL` - RabbitMQ 연결 URL
- `SPRING_FLYWAY_BASELINE_ON_MIGRATE` (선택) - 기존 스키마 baseline 처리 여부 (기본값 `true`)
- `SPRING_FLYWAY_BASELINE_VERSION` (선택) - baseline 버전 (기본값 `0`)


> 참고: `AI_SERVER_*`, `AI_APP_DIR`, `ANTHROPIC_API_KEY`, `SLACK_*`는 AI 배포/운영용이므로 백엔드 자동배포 필수값에서 제외했다.
> 캐시/큐 기능을 운영에 포함하므로 `REDIS_URL`, `RABBITMQ_URL`는 production secrets에 반드시 등록한다.

### 2. 서버 선행 조건
- `${BACKEND_APP_DIR}` 경로에 쓰기 권한이 있어야 한다.
- 배포 계정이 `sudo systemctl restart withbuddy-backend.service`를 비밀번호 없이 실행할 수 있어야 한다.
- 운영 백엔드는 `withbuddy-backend.service` 단일 서비스로만 기동한다. (수동 `java -jar` 금지)
- 환경변수 파일은 `/etc/withbuddy-backend.env`를 사용한다.

### 3. 배포 방법
1. `main` 브랜치에 `backend/**` 변경을 push한다.
2. GitHub Actions `Deploy Backend`가 자동 실행된다.
3. 빌드 성공 시 JAR 업로드 → `/etc/withbuddy-backend.env` 갱신 → `withbuddy-backend.service` 재시작 → 헬스체크까지 수행된다.
4. 백엔드 기동 시 Flyway가 `db/migration` 스크립트를 자동 적용하며, 기존 스키마 환경은 baseline 설정으로 이관된다.

## 참고 문서

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/reference/)
- [Spring Security + JWT Guide](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Swagger/SpringDoc](https://springdoc.org/)
- [JPA Best Practices](https://thorben-janssen.com/tips-to-boost-your-hibernate-performance/)

## 트러블슈팅

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
→ 수동 java 프로세스/중복 서비스 여부 확인
  sudo lsof -iTCP:8080 -sTCP:LISTEN -n -P
  ps -ef | grep 'java -jar' | grep -v grep
→ 운영 정책은 withbuddy-backend.service 단일 기동 유지
```

## 문의

- 기술 문의: @WithBuddyAi/backend-developers
- 이슈 등록: GitHub Issues
- 긴급 문의: 개발 채널 https://discord.com/channels/1463843582187278431/1481226881104744561

---

**Last Updated**: 2026-04-14

## 변경 이력

- 2026-04-14: 배포 설정을 Flyway 기준으로 정리. `SPRING_SQL_INIT_MODE` 전달을 제거하고 `SPRING_FLYWAY_BASELINE_*`(선택) 운영값 안내를 추가.
- 2026-04-09: 문서 메타데이터를 현재 수정 상태에 맞게 정리하고 변경 이력 순서를 최신 기준으로 재정렬.
- 2026-04-07: SSH 배포 대상 시크릿을 `BACKEND_SERVER_HOST` 단일 기준으로 정리.
- 2026-04-07: 배포 재시작 방식을 `nohup`/`pkill`에서 `withbuddy-backend.service` 단일 systemd 재시작으로 통일.
- 2026-04-06: CI/CD 환경변수 설명 정리 (`BACKEND_HEALTH_URL`, `JWT_SECRET`, `REDIS_URL`, `RABBITMQ_URL`).
- 2026-04-06: 캐시/큐 운영 반영으로 `REDIS_URL`, `RABBITMQ_URL`를 필수값으로 상향.
- 2026-04-02: 브랜치 예시와 네이밍 규칙의 Jira 키 표기를 `SCRUM-##` 대문자로 통일.

