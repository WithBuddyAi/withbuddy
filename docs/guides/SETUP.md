# 개발 환경 설정 가이드

> WithBuddy 로컬 개발 환경 구축 완벽 가이드

**최종 업데이트**: 2026-03-24  
**버전**: 1.1.1

## 📋 목차
- [필수 요구사항](#필수-요구사항)
- [MySQL 설치 및 설정](#mysql-설치-및-설정)
- [Backend 설정](#backend-설정)
- [Frontend 설정](#frontend-설정)
- [AI 서버 설정](#ai-서버-설정)
- [통합 실행 확인](#통합-실행-확인)
- [문제 해결](#문제-해결)

---

## 필수 요구사항

### 소프트웨어 버전
- **Java**: 21 (OpenJDK 또는 Oracle JDK)
- **Node.js**: 20+ & npm/yarn
- **Python**: 3.11+
- **MySQL**: 8.0
- **Docker Desktop**: 4.0+ (선택, AI 서버 컨테이너 실행 시)

### 권장 개발 도구
- IntelliJ IDEA / VS Code
- Postman / Insomnia (API 테스트)
- MySQL Workbench / DBeaver (DB 관리)

---

## 프로젝트 표준

| 구분 | 디렉토리 | 프로젝트명 | 식별자/패키지 | 기본 포트 |
|------|----------|------------|---------------|-----------|
| Backend | `backend/` | withbuddy | `com.withbuddy` | 8080 |
| Frontend | `frontend/` | withbuddy-frontend | `VITE_*` env 사용 | 5173 |
| AI | `ai/` | withbuddy-ai | `app.main:app` | 8000 |

**Backend 생성 기준**:
- Project name / Artifact: `withbuddy`
- Group / Package: `com.withbuddy`
- Build: Gradle (Groovy)
- Java: 21

---

## MySQL 설치 및 설정

### Ubuntu/Debian
```bash
# MySQL 설치
sudo apt update
sudo apt install mysql-server

# MySQL 보안 설정
sudo mysql_secure_installation

# MySQL 접속
sudo mysql

# 데이터베이스 및 사용자 생성
CREATE DATABASE withbuddy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'withbuddy'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON withbuddy.* TO 'withbuddy'@'localhost';
FLUSH PRIVILEGES;
EXIT;

# MySQL 재시작
sudo systemctl restart mysql
sudo systemctl enable mysql
```

### macOS (Homebrew)
```bash
# Homebrew로 설치
brew install mysql@8.0
brew services start mysql@8.0

# 데이터베이스 생성
mysql -u root
CREATE DATABASE withbuddy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'withbuddy'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON withbuddy.* TO 'withbuddy'@'localhost';
FLUSH PRIVILEGES;
```

### Windows
```
1. MySQL Installer 다운로드
   https://dev.mysql.com/downloads/installer/

2. MySQL Server 8.0 설치

3. MySQL Workbench에서 데이터베이스 생성
   CREATE DATABASE withbuddy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## Backend 설정

### 1. 환경변수 설정

**IntelliJ IDEA (권장)**
```
1. Run > Edit Configurations
2. Environment Variables 클릭
3. 다음 변수 추가:

SPRING_DB_PASSWORD=your_password
JWT_SECRET=your-jwt-secret-key-at-least-32-characters-long
JWT_ACCESS_EXPIRATION=7200000
JWT_REFRESH_EXPIRATION=604800000
AI_API_URL=http://localhost:8000
```

**Ubuntu 서버 (배포용)**
```bash
# ~/.bashrc 또는 /etc/environment에 추가
export SPRING_DB_PASSWORD=your_password
export JWT_SECRET=your-jwt-secret-key
export JWT_ACCESS_EXPIRATION=7200000
export JWT_REFRESH_EXPIRATION=604800000
export AI_API_URL=http://localhost:8000

# 적용
source ~/.bashrc
```

### 2. application.yml 확인

**application-dev.yml**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/withbuddy
    username: withbuddy
    password: ${SPRING_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
        show_sql: true

jwt:
  secret: ${JWT_SECRET}
  access-expiration: ${JWT_ACCESS_EXPIRATION}   # 2시간 (7200000ms)
  refresh-expiration: ${JWT_REFRESH_EXPIRATION} # 7일 (604800000ms)

ai:
  api:
    url: ${AI_API_URL}

server:
  port: 8080
```

### 3. 실행
```bash
cd backend

# 빌드
./gradlew clean build

# 실행
./gradlew bootRun

# 또는 JAR 실행
java -jar build/libs/withbuddy-0.0.1-SNAPSHOT.jar

# 확인
# http://localhost:8080
# http://localhost:8080/swagger-ui.html
```

---

## Frontend 설정

### 1. 의존성 설치
```bash
cd frontend
npm install
# 또는
yarn install
```

### 2. 환경변수 설정

**.env.local (로컬 개발용)**
```bash
VITE_API_BASE_URL=http://localhost:8080/api
```

**.env.production (배포용)**
```bash
VITE_API_BASE_URL=https://api.withbuddy.com/api
```

### 3. 실행
```bash
# 개발 서버
npm run dev

# 빌드
npm run build

# 빌드 미리보기
npm run preview
```

### 4. 확인
- 개발 서버: http://localhost:5173
- Vite 빌드: dist/ 디렉토리 생성됨

---

## AI 서버 설정

### 1. Docker Compose로 실행 (선택)

루트 디렉토리의 `docker-compose.yml`은 AI 서버만 빠르게 띄우기 위한 로컬 개발용 설정입니다.

```bash
cd withbuddy

# 필요 시 현재 셸 또는 .env 에 환경변수 설정
# ANTHROPIC_API_KEY=your_anthropic_api_key
# SLACK_BOT_TOKEN=xoxb-...
# SLACK_APP_TOKEN=xapp-...
# AI_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000

docker compose up --build ai

# 확인
curl http://localhost:8000/health
```

기본 동작:
- `ai/` 디렉토리를 `/app`으로 마운트해 코드 변경이 즉시 반영됩니다.
- ChromaDB 데이터는 Docker 볼륨 `ai_chroma_db`에 저장됩니다.
- 기본 포트는 `8000:8000`입니다.

### 2. 가상환경 생성
```bash
cd ai

# 가상환경 생성
python -m venv venv

# 가상환경 활성화
source venv/bin/activate  # macOS/Linux
venv\Scripts\activate     # Windows
```

### 2. 의존성 설치
```bash
pip install -r requirements.txt
```

### 3. 환경변수 설정

**.env 파일 생성**
```bash
# ai/.env
ANTHROPIC_API_KEY=your_anthropic_api_key
CHROMA_PERSIST_DIR=./chroma_db
```

### 4. 실행
```bash
# uvicorn 실행
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# 확인
# http://localhost:8000/docs (Swagger UI)
# http://localhost:8000/redoc (ReDoc)
```

---

## 통합 실행 확인

### 1. 서비스 상태 확인
```bash
# Backend
curl http://localhost:8080/actuator/health

# Frontend
# 브라우저에서 http://localhost:5173 접속

# AI
curl http://localhost:8000/health
```

### 2. 통합 테스트 시나리오
```
1. Frontend(5173) → 회원가입
2. Frontend → 로그인
3. My Buddy → AI에게 질문 (Frontend → Backend → AI)
4. 데일리 기록 작성 → AI 요약 요청
```

### 3. 로그 확인
```bash
# Backend 로그
./gradlew bootRun --info

# Frontend 로그
npm run dev

# AI 서버 로그
uvicorn app.main:app --reload --log-level debug
```

---

## 문제 해결

### MySQL 연결 오류
```bash
# 에러: Access denied for user
→ SPRING_DB_PASSWORD 환경변수 확인
→ MySQL 사용자 권한 확인

# 에러: Unknown database 'withbuddy'
→ 데이터베이스 생성 확인
```

### Backend 실행 오류
```bash
# 에러: Could not find or load main class
→ ./gradlew clean build 재실행

# 에러: Port 8080 already in use
→ 다른 애플리케이션 종료 또는 포트 변경
```

### Frontend 실행 오류
```bash
# 에러: ENOENT: no such file or directory
→ node_modules 삭제 후 npm install 재실행

# 에러: Network error
→ VITE_API_BASE_URL 확인
```

### AI 서버 오류
```bash
# 에러: ModuleNotFoundError
→ pip install -r requirements.txt 재실행

# 에러: Anthropic API key not found
→ .env 파일 확인

# 에러: docker compose 실행 시 빌드 또는 포트 충돌
→ Docker Desktop 실행 상태 확인
→ 8000 포트 사용 중 프로세스 종료 또는 compose 포트 변경
```

---

## 다음 단계

- [배포 가이드](../architecture/DEPLOYMENT.md) - 실제 서버 배포
- [API 문서](../API.md) - API 엔드포인트
- [기여 가이드](CONTRIBUTING.md) - 개발 참여 방법
