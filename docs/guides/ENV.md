# Environment Variables

WithBuddy 프로젝트의 환경변수 설정 가이드입니다.

**최종 업데이트**: 2026-03-30  
**버전**: 1.1.1

## 📋 목차

- [Backend (Java/Spring)](#backend-javaspring)
- [Frontend (Node.js)](#frontend-nodejs)
- [AI (Python)](#ai-python)
- [공통 설정](#공통-설정)

---

## Backend (Java/Spring)

### 필수 환경변수

```bash
# 데이터베이스 설정
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/withbuddy?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver

# JWT 설정
JWT_SECRET=your-secret-key-min-256-bits
JWT_EXPIRATION=86400000  # 24시간 (밀리초)

# 서버 설정
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev  # dev, prod, test
```

### 선택 환경변수

```bash
# JPA/Hibernate 설정
HIBERNATE_DDL_AUTO=update  # create, create-drop, update, validate, none
SHOW_SQL=false  # SQL 쿼리 로깅
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MySQL8Dialect

# 커넥션 풀 설정
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=5
SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=20000

# 로깅 설정
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_WITHBUDDY=DEBUG
LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG  # SQL 로깅

# 파일 업로드
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=10MB
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=10MB

# CORS 설정
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

# 이메일 설정 (선택)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your-email@gmail.com
SPRING_MAIL_PASSWORD=your-app-password
```

### application.yml 예시

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: ${SPRING_DATASOURCE_DRIVER_CLASS_NAME:com.mysql.cj.jdbc.Driver}
  jpa:
    hibernate:
      ddl-auto: ${HIBERNATE_DDL_AUTO:update}
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
    show-sql: ${SHOW_SQL:false}
  
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

server:
  port: ${SERVER_PORT:8080}
```

---

## Frontend (Node.js)

### 필수 환경변수

```bash
# API 엔드포인트
VITE_API_BASE_URL=http://localhost:8080/api
VITE_API_TIMEOUT=30000

# 환경 구분
VITE_APP_ENV=development  # development, production, staging
```

### 선택 환경변수

```bash
# 외부 서비스 API 키
VITE_GOOGLE_ANALYTICS_ID=GA-XXXXXXXXX
VITE_SENTRY_DSN=https://xxx@sentry.io/xxx

# 기능 플래그
VITE_ENABLE_AI_FEATURES=true
VITE_ENABLE_ANALYTICS=false

# 개발 도구
VITE_DEV_PORT=5173
VITE_ENABLE_MOCK_API=false
```

### .env 파일 예시

```env
# .env.development
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_ENV=development
VITE_ENABLE_MOCK_API=true

# .env.production
VITE_API_BASE_URL=https://api.withbuddy.com/api
VITE_APP_ENV=production
VITE_ENABLE_ANALYTICS=true
```

---

## AI (Python)

### 필수 환경변수

```bash
# AI 모델 설정
ANTHROPIC_API_KEY=sk-ant-xxxxxxxx
MODEL_NAME=claude
MODEL_TEMPERATURE=0.7

# 서버 설정
AI_SERVER_PORT=8000
AI_BIND_HOST=0.0.0.0
CHROMA_PERSIST_DIR=./chroma_db
```

### 선택 환경변수

```bash
# 모델 파라미터
MAX_TOKENS=2048
TOP_P=0.9
FREQUENCY_PENALTY=0.0
PRESENCE_PENALTY=0.0

# 캐싱 설정
REDIS_URL=redis://localhost:6379
CACHE_TTL=3600  # 1시간

# 로깅
LOG_LEVEL=INFO  # DEBUG, INFO, WARNING, ERROR
LOG_FORMAT=json

# 외부 서비스
HUGGINGFACE_TOKEN=hf_xxxxxx  # Hugging Face (선택)
```

### .env 파일 예시

```env
# .env
ANTHROPIC_API_KEY=sk-ant-xxxxxxxx
MODEL_NAME=claude
MODEL_TEMPERATURE=0.7
AI_SERVER_PORT=8000
CHROMA_PERSIST_DIR=./chroma_db

# Redis (선택)
REDIS_URL=redis://localhost:6379
CACHE_TTL=3600
```

---

## 공통 설정

### GitHub Actions Secrets

CI/CD에서 사용되는 민감한 정보는 GitHub Secrets에 저장:

```
Settings → Secrets and variables → Actions → New repository secret
```

**필수 Secrets:**
- `DB_PASSWORD` - 데이터베이스 비밀번호
- `JWT_SECRET` - JWT 서명 키
- `ANTHROPIC_API_KEY` - Anthropic Claude API 키

### AI 배포용 Environment Secrets (production)

`Deploy AI Server` 워크플로우(`.github/workflows/ai-deploy.yml`)는 아래 `production` 환경 시크릿을 사용한다.

```bash
${{ secrets.AI_SERVER_HOST }}=217.142.242.239
${{ secrets.AI_SERVER_USER }}=ubuntu
${{ secrets.AI_SERVER_SSH_KEY }}=<AI 서버 접속 개인키 전체>
${{ secrets.AI_APP_DIR }}=/home/ubuntu/withbuddy/ai
${{ secrets.AI_SERVICE_NAME }}=withbuddy-ai
```

등록 상태:
- 위 5개 시크릿은 GitHub Actions `Environment: production`에 등록 완료됨 (확인일: 2026-03-30).

주의:
- 위 값은 Repository secrets가 아니라 **Environment secrets (production)** 기준이다.
- `${{ secrets.AI_SERVER_HOST }}`는 서버 주소이며, `AI_BIND_HOST=0.0.0.0` 같은 애플리케이션 바인딩 값과 의미가 다르다.

**선택 Secrets:**
- `SENTRY_DSN` - 에러 트래킹 DSN
- `AWS_ACCESS_KEY_ID` - AWS 액세스 키 (배포 시)
- `AWS_SECRET_ACCESS_KEY` - AWS 시크릿 키 (배포 시)

---

## 🔒 보안 가이드

### ⚠️ 절대 커밋하지 말아야 할 것

```bash
# ❌ 위험
.env
.env.local
.env.production
**/application-prod.yml
**/application-secret.yml
```

### ✅ .gitignore에 추가

```gitignore
# Environment variables
.env
.env.*
!.env.example

# Backend
backend/src/main/resources/application-prod.yml
backend/src/main/resources/application-secret.yml

# Frontend
frontend/.env.local
frontend/.env.production.local

# AI
ai/.env
ai/.env.local
```

### 📝 .env.example 파일 생성

각 디렉토리에 `.env.example` 파일을 만들어 필요한 환경변수 템플릿 제공:

```bash
# backend/.env.example
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/withbuddy?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=
SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
JWT_SECRET=
SERVER_PORT=8080

# frontend/.env.example
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_ENV=development

# ai/.env.example
ANTHROPIC_API_KEY=
MODEL_NAME=claude
AI_SERVER_PORT=8000
```

---

## 🚀 환경별 설정

### Development (개발)

```bash
# 로컬 개발 환경
SPRING_PROFILES_ACTIVE=dev
VITE_APP_ENV=development
LOG_LEVEL=DEBUG
VITE_ENABLE_MOCK_API=true
```

### Staging (스테이징)

```bash
# 테스트/검증 환경
SPRING_PROFILES_ACTIVE=staging
VITE_APP_ENV=staging
LOG_LEVEL=INFO
VITE_ENABLE_ANALYTICS=true
```

### Production (프로덕션)

```bash
# 실제 서비스 환경
SPRING_PROFILES_ACTIVE=prod
VITE_APP_ENV=production
LOG_LEVEL=WARNING
VITE_ENABLE_ANALYTICS=true
CORS_ALLOWED_ORIGINS=https://withbuddy.com
```

---

## 📖 참고 자료

- [Spring Boot External Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Vite Environment Variables](https://vitejs.dev/guide/env-and-mode.html)
- [Python dotenv Documentation](https://pypi.org/project/python-dotenv/)

---

## 🆘 트러블슈팅

### 환경변수가 로드되지 않을 때

1. **파일 이름 확인**: `.env` (점으로 시작)
2. **파일 위치 확인**: 프로젝트 루트 또는 각 서비스 루트
3. **문법 확인**: `KEY=value` (공백 없이)
4. **재시작**: 서버 재시작 필요

### 값이 undefined일 때

```bash
# Frontend (Vite)
# ❌ 잘못된 사용
REACT_APP_API_URL=xxx  # Vite는 VITE_ 접두사 필요

# ✅ 올바른 사용
VITE_API_URL=xxx
```

---

## 변경 이력

- 2026-03-30: AI 배포용 `production` Environment Secrets 등록 상태를 추가하고 `${{ secrets.* }}` 표기로 통일.
- 2026-03-23: AI/Backend/Frontend 환경변수 구조 정리.

---

**마지막 업데이트**: 2026-03-30
