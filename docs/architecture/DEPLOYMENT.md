# WithBuddy 배포 가이드

> CI/CD 파이프라인, 배포 전략 및 운영 가이드

**최종 업데이트**: 2026-03-24  
**버전**: 1.1.1

---

## 📋 목차

- [1. 배포 개요](#1-배포-개요)
- [2. CI/CD 파이프라인](#2-cicd-파이프라인)
- [3. 배포 전략](#3-배포-전략)
- [4. 환경 구성](#4-환경-구성)
- [5. 배포 프로세스](#5-배포-프로세스)
- [6. 롤백 절차](#6-롤백-절차)
- [7. 모니터링 & 로깅](#7-모니터링--로깅)
- [8. 장애 대응](#8-장애-대응)

---

## 1. 배포 개요

### 1.1 배포 환경

| 환경 | 용도 | 접근 |
|------|------|------|
| **Development** | 개발자 로컬 환경 | localhost |
| **Staging** | QA/테스트 환경 | staging.withbuddy.com |
| **Production** | 실제 서비스 환경 | withbuddy.com |

### 1.2 프로젝트 식별자

| 구분 | 디렉토리 | 프로젝트명 | 식별자/패키지 | 기본 포트 |
|------|----------|------------|---------------|-----------|
| Backend | `backend/` | withbuddy | `com.withbuddy` | 8080 |
| Frontend | `frontend/` | withbuddy-frontend | `VITE_*` env 사용 | 5173 |
| AI | `ai/` | withbuddy-ai | `app.main:app` | 8000 |

### 1.2 배포 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                   GitHub Repository                      │
└────────────────────────┬────────────────────────────────┘
                         │ Push/PR Merge
                         ↓
┌─────────────────────────────────────────────────────────┐
│              GitHub Actions (CI/CD)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  Build       │→ │  Test        │→ │  Deploy      │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└────────────────────────┬────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        ↓                ↓                ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Vercel     │  │   Backend    │  │  AI Server   │
│  (Frontend)  │  │   (VCN)      │  │    (VCN)     │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 1.3 배포 흐름

```
개발자 코드 푸시
  ↓
GitHub Actions 트리거
  ↓
빌드 & 테스트
  ↓
성공? ─No→ 알림 & 중단
  │
  Yes
  ↓
배포 (환경별)
  ↓
헬스 체크
  ↓
성공? ─No→ 자동 롤백
  │
  Yes
  ↓
배포 완료 알림
```

---

## 2. CI/CD 파이프라인

### 2.1 GitHub Actions 워크플로우

#### Frontend CI/CD

```yaml
# .github/workflows/frontend.yml
name: Frontend CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'frontend/**'
  pull_request:
    branches: [main]
    paths:
      - 'frontend/**'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json
      
      - name: Install dependencies
        working-directory: ./frontend
        run: npm ci
      
      - name: Run linter
        working-directory: ./frontend
        run: npm run lint
      
      - name: Run tests
        working-directory: ./frontend
        run: npm test
      
      - name: Build
        working-directory: ./frontend
        run: npm run build
        env:
          VITE_API_BASE_URL: ${{ secrets.VITE_API_BASE_URL }}
      
      - name: Deploy to Vercel (Production)
        if: github.ref == 'refs/heads/main'
        uses: amondnet/vercel-action@v25
        with:
          vercel-token: ${{ secrets.VERCEL_TOKEN }}
          vercel-org-id: ${{ secrets.VERCEL_ORG_ID }}
          vercel-project-id: ${{ secrets.VERCEL_PROJECT_ID }}
          working-directory: ./frontend
          vercel-args: '--prod'
      
      - name: Deploy to Vercel (Staging)
        if: github.ref == 'refs/heads/develop'
        uses: amondnet/vercel-action@v25
        with:
          vercel-token: ${{ secrets.VERCEL_TOKEN }}
          vercel-org-id: ${{ secrets.VERCEL_ORG_ID }}
          vercel-project-id: ${{ secrets.VERCEL_PROJECT_ID }}
          working-directory: ./frontend
      
      - name: Notify Slack
        if: always()
        uses: 8398a7/action-slack@v3
        with:
          status: ${{ job.status }}
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

#### Backend CI/CD

```yaml
# .github/workflows/backend.yml
name: Backend CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'backend/**'
  pull_request:
    branches: [main]
    paths:
      - 'backend/**'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test
          MYSQL_DATABASE: withbuddy_test
        ports:
          - 3306:3306
        options: --health-cmd="mysqladmin ping" --health-interval=10s
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'
      
      - name: Grant execute permission for gradlew
        working-directory: ./backend
        run: chmod +x gradlew
      
      - name: Run tests
        working-directory: ./backend
        run: ./gradlew test
        env:
          SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/withbuddy_test
          SPRING_DATASOURCE_USERNAME: root
          SPRING_DATASOURCE_PASSWORD: test
      
      - name: Build with Gradle
        working-directory: ./backend
        run: ./gradlew build -x test
      
      - name: Build Docker image
        if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
        working-directory: ./backend
        run: |
          docker build -t withbuddy-backend:${{ github.sha }} .
          docker tag withbuddy-backend:${{ github.sha }} withbuddy-backend:latest
      
      - name: Push to Container Registry
        if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
        run: |
          echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
          docker push withbuddy-backend:${{ github.sha }}
          docker push withbuddy-backend:latest
      
      - name: Deploy to Server
        if: github.ref == 'refs/heads/main'
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/withbuddy
            docker-compose pull backend
            docker-compose up -d backend
            docker system prune -f
      
      - name: Health Check
        if: github.ref == 'refs/heads/main'
        run: |
          sleep 30
          curl -f https://api.withbuddy.com/actuator/health || exit 1
      
      - name: Notify Slack
        if: always()
        uses: 8398a7/action-slack@v3
        with:
          status: ${{ job.status }}
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

#### AI Server CI/CD

```yaml
# .github/workflows/ai.yml
name: AI Server CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'ai/**'
  pull_request:
    branches: [main]
    paths:
      - 'ai/**'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'
          cache: 'pip'
      
      - name: Install dependencies
        working-directory: ./ai
        run: |
          pip install --break-system-packages -r requirements.txt
          pip install --break-system-packages pytest pytest-cov
      
      - name: Run linter
        working-directory: ./ai
        run: |
          pip install --break-system-packages flake8
          flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics
      
      - name: Run tests
        working-directory: ./ai
        run: pytest --cov=./ --cov-report=xml
      
      - name: Build Docker image
        if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
        working-directory: ./ai
        run: |
          docker build -t withbuddy-ai:${{ github.sha }} .
          docker tag withbuddy-ai:${{ github.sha }} withbuddy-ai:latest
      
      - name: Push to Container Registry
        if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
        run: |
          echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
          docker push withbuddy-ai:${{ github.sha }}
          docker push withbuddy-ai:latest
      
      - name: Deploy to Server
        if: github.ref == 'refs/heads/main'
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/withbuddy
            docker-compose pull ai
            docker-compose up -d ai
            docker system prune -f
      
      - name: Notify Slack
        if: always()
        uses: 8398a7/action-slack@v3
        with:
          status: ${{ job.status }}
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

### 2.2 Docker 구성

#### Backend Dockerfile

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# JAR 파일 복사
COPY build/libs/*.jar app.jar

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 실행
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
```

#### AI Server Dockerfile

```dockerfile
# ai/Dockerfile
FROM python:3.11-slim

WORKDIR /app

# 시스템 패키지 설치
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Python 패키지 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 애플리케이션 코드 복사
COPY . .

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8000/health || exit 1

# 실행
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

#### docker-compose.yml

```yaml
# docker-compose.yml (프로덕션 서버)
version: '3.8'

services:
  backend:
    image: withbuddy-backend:latest
    container_name: withbuddy-backend
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=${DB_URL}
      - SPRING_DATASOURCE_USERNAME=${DB_USER}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    depends_on:
      - redis
    networks:
      - withbuddy-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  ai:
    image: withbuddy-ai:latest
    container_name: withbuddy-ai
    restart: unless-stopped
    ports:
      - "8000:8000"
    environment:
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      - CHROMA_PERSIST_DIR=/app/chroma_db
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - DB_URL=${DB_URL}
    depends_on:
      - redis
    networks:
      - withbuddy-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  redis:
    image: redis:7-alpine
    container_name: withbuddy-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - withbuddy-network
    command: redis-server --appendonly yes
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

volumes:
  redis-data:

networks:
  withbuddy-network:
    driver: bridge
```

---

## 3. 배포 전략

### 3.1 Blue-Green 배포

```
┌─────────────────────────────────────────┐
│         Load Balancer                   │
└────────────┬────────────────────────────┘
             │
   ┌─────────┴─────────┐
   │                   │
   ↓                   ↓
┌──────────┐      ┌──────────┐
│  Blue    │      │  Green   │
│ (현재)   │      │ (신규)   │
│ v1.0.0   │      │ v1.1.0   │
└──────────┘      └──────────┘
```

**프로세스**:
1. Green 환경에 새 버전 배포
2. Green 환경 헬스 체크
3. Green 환경으로 일부 트래픽 라우팅 (10%)
4. 모니터링 (5-10분)
5. 문제 없으면 100% 트래픽 전환
6. Blue 환경 대기 (롤백용)

**구현 (AWS ALB 예시)**:
```bash
# Green 환경 배포
aws ecs update-service \
  --cluster withbuddy-cluster \
  --service withbuddy-backend-green \
  --force-new-deployment

# 헬스 체크 대기
aws ecs wait services-stable \
  --cluster withbuddy-cluster \
  --services withbuddy-backend-green

# 트래픽 전환 (10% → Green)
aws elbv2 modify-listener \
  --listener-arn $LISTENER_ARN \
  --default-actions \
    Type=forward,\
    ForwardConfig='{
      "TargetGroups": [
        {"TargetGroupArn": "'$BLUE_TG'", "Weight": 90},
        {"TargetGroupArn": "'$GREEN_TG'", "Weight": 10}
      ]
    }'

# 5분 모니터링 후 100% 전환
sleep 300
aws elbv2 modify-listener \
  --listener-arn $LISTENER_ARN \
  --default-actions \
    Type=forward,\
    TargetGroupArn=$GREEN_TG
```

### 3.2 Rolling 배포

```
Instance 1: v1.0 → v1.1 ✓
Instance 2: v1.0 (서비스 중)
Instance 3: v1.0 (서비스 중)
           ↓
Instance 1: v1.1 (서비스 중)
Instance 2: v1.0 → v1.1 ✓
Instance 3: v1.0 (서비스 중)
           ↓
Instance 1: v1.1 (서비스 중)
Instance 2: v1.1 (서비스 중)
Instance 3: v1.0 → v1.1 ✓
```

**docker-compose 예시**:
```bash
# 1. 새 이미지 pull
docker-compose pull backend

# 2. 서비스 재시작 (롤링)
docker-compose up -d --no-deps --scale backend=3 --no-recreate backend

# 3. 구버전 컨테이너 제거
docker-compose up -d --remove-orphans
```

### 3.3 Canary 배포

```
┌─────────────────────────────────────┐
│      Load Balancer                  │
└────────┬────────────────────────────┘
         │
    ┌────┴────┐
    │         │
    95%      5%
    ↓         ↓
┌─────────┐ ┌─────────┐
│ Stable  │ │ Canary  │
│ v1.0.0  │ │ v1.1.0  │
└─────────┘ └─────────┘
```

**단계별 트래픽 증가**:
```
Phase 1: 5% → Canary (모니터링 30분)
Phase 2: 25% → Canary (모니터링 1시간)
Phase 3: 50% → Canary (모니터링 2시간)
Phase 4: 100% → Canary (완료)
```

---

## 4. 환경 구성

### 4.1 환경별 설정

#### Development (로컬)

```yaml
# backend/src/main/resources/application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/withbuddy_dev
    username: root
    password: root
  
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update

logging:
  level:
    com.withbuddy: DEBUG
```

#### Staging

```yaml
# backend/src/main/resources/application-staging.yml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

logging:
  level:
    com.withbuddy: INFO
```

#### Production

```yaml
# backend/src/main/resources/application-prod.yml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

logging:
  level:
    com.withbuddy: WARN
```

### 4.2 환경변수 관리

#### .env 파일 (로컬)

```bash
# .env (git ignored)
DB_URL=jdbc:mysql://localhost:3306/withbuddy
DB_USER=root
DB_PASSWORD=root
JWT_SECRET=your-secret-key-change-in-production
ANTHROPIC_API_KEY=sk-...
CHROMA_PERSIST_DIR=./chroma_db
REDIS_HOST=localhost
REDIS_PORT=6379
```

#### GitHub Secrets (CI/CD)

```
Settings → Secrets and variables → Actions

Secrets:
- VERCEL_TOKEN
- VERCEL_ORG_ID
- VERCEL_PROJECT_ID
- DOCKER_USERNAME
- DOCKER_PASSWORD
- SERVER_HOST
- SERVER_USER
- SSH_PRIVATE_KEY
- DB_URL
- DB_USER
- DB_PASSWORD
- JWT_SECRET
- ANTHROPIC_API_KEY
- SLACK_WEBHOOK
```

#### 서버 환경변수 (프로덕션)

```bash
# /opt/withbuddy/.env (서버)
DB_URL=jdbc:mysql://mysql.internal:3306/withbuddy_prod
DB_USER=withbuddy_user
DB_PASSWORD=strong-password-here
JWT_SECRET=production-jwt-secret-key
ANTHROPIC_API_KEY=sk-prod-...
CHROMA_PERSIST_DIR=/opt/withbuddy/ai/chroma_db
REDIS_HOST=redis
REDIS_PORT=6379
```

---

## 5. 배포 프로세스

### 5.1 프로덕션 배포 체크리스트

**배포 전**:
- [ ] 코드 리뷰 완료
- [ ] 모든 테스트 통과 (단위, 통합)
- [ ] Staging 환경 테스트 완료
- [ ] 데이터베이스 마이그레이션 스크립트 준비
- [ ] 롤백 계획 수립
- [ ] 모니터링 대시보드 확인
- [ ] 팀원에게 배포 공지

**배포 중**:
- [ ] 데이터베이스 백업 생성
- [ ] 마이그레이션 실행 (필요시)
- [ ] 새 버전 배포
- [ ] 헬스 체크 통과 확인
- [ ] 기능 테스트 (스모크 테스트)

**배포 후**:
- [ ] 에러 로그 모니터링 (30분)
- [ ] 핵심 메트릭 확인 (응답시간, 에러율)
- [ ] 사용자 피드백 모니터링
- [ ] 배포 완료 공지

### 5.2 배포 명령어

#### Frontend (Vercel)

```bash
# 프로덕션 배포
cd frontend
vercel --prod

# Staging 배포
vercel
```

#### Backend (SSH)

```bash
# 서버 접속
ssh user@api.withbuddy.com

# 배포 디렉토리 이동
cd /opt/withbuddy

# 최신 이미지 pull
docker-compose pull backend

# 서비스 재시작
docker-compose up -d backend

# 로그 확인
docker-compose logs -f backend

# 헬스 체크
curl http://localhost:8080/actuator/health
```

#### AI Server (SSH)

```bash
# 서버 접속
ssh user@api.withbuddy.com

# AI 서비스 재시작
cd /opt/withbuddy
docker-compose pull ai
docker-compose up -d ai

# 로그 확인
docker-compose logs -f ai
```

### 5.3 데이터베이스 마이그레이션

#### Flyway 마이그레이션

```sql
-- backend/src/main/resources/db/migration/V1__initial_schema.sql
CREATE TABLE companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_code INT UNIQUE NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    company_id BIGINT NOT NULL,
    employee_number VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    FOREIGN KEY (company_id) REFERENCES companies(id),
    UNIQUE KEY uk_company_employee (company_id, employee_number)
);
```

**마이그레이션 실행**:
```bash
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

# 애플리케이션 시작 시 자동 실행
# 또는 수동 실행
./gradlew flywayMigrate
```

---

## 6. 롤백 절차

### 6.1 자동 롤백 (헬스 체크 실패)

```yaml
# .github/workflows/backend.yml
- name: Health Check
  id: health_check
  run: |
    for i in {1..5}; do
      if curl -f https://api.withbuddy.com/actuator/health; then
        echo "Health check passed"
        exit 0
      fi
      echo "Health check failed, retry $i/5"
      sleep 10
    done
    exit 1

- name: Rollback on failure
  if: failure() && steps.health_check.conclusion == 'failure'
  run: |
    ssh ${{ secrets.SERVER_USER }}@${{ secrets.SERVER_HOST }} << 'EOF'
      cd /opt/withbuddy
      docker-compose down backend
      docker tag withbuddy-backend:previous withbuddy-backend:latest
      docker-compose up -d backend
    EOF
```

### 6.2 수동 롤백

#### Docker 이전 버전 복원

```bash
# 1. 현재 실행 중인 버전 확인
docker ps | grep withbuddy-backend

# 2. 사용 가능한 이미지 확인
docker images | grep withbuddy-backend

# 3. 이전 버전으로 롤백
cd /opt/withbuddy
docker-compose down backend
docker tag withbuddy-backend:backup withbuddy-backend:latest
docker-compose up -d backend

# 4. 헬스 체크
curl http://localhost:8080/actuator/health
```

#### 데이터베이스 롤백

```bash
# 1. 최신 백업 확인
aws rds describe-db-snapshots \
  --db-instance-identifier withbuddy-mysql \
  --query 'reverse(sort_by(DBSnapshots, &SnapshotCreateTime))[0]'

# 2. 스냅샷으로 복원
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier withbuddy-mysql-restored \
  --db-snapshot-identifier snapshot-20260317-backup

# 3. 애플리케이션 연결 변경
# (환경변수 DB_URL 업데이트)
```

### 6.3 롤백 결정 기준

**즉시 롤백**:
- ❌ 헬스 체크 실패
- ❌ 5xx 에러율 > 5%
- ❌ 응답시간 > 2초 (평소 대비 2배)
- ❌ 핵심 기능 동작 불가

**모니터링 후 판단**:
- ⚠️ 4xx 에러율 증가
- ⚠️ 일부 기능 오류
- ⚠️ 성능 저하 (경미)

---

## 7. 모니터링 & 로깅

### 7.1 헬스 체크 엔드포인트

```java
// Backend Health Check
@RestController
public class HealthController {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());
        
        // Database check
        try {
            dataSource.getConnection().isValid(1);
            health.put("database", "UP");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("status", "DOWN");
        }
        
        // Redis check
        try {
            redisTemplate.opsForValue().get("health-check");
            health.put("redis", "UP");
        } catch (Exception e) {
            health.put("redis", "DOWN");
        }
        
        if ("DOWN".equals(health.get("status"))) {
            return ResponseEntity.status(503).body(health);
        }
        
        return ResponseEntity.ok(health);
    }
}
```

```python
# AI Server Health Check
from fastapi import FastAPI

app = FastAPI()

@app.get("/health")
async def health_check():
    return {
        "status": "UP",
        "timestamp": datetime.now().isoformat(),
        "service": "ai-server"
    }
```

### 7.2 로깅 설정

#### Backend (Logback)

```xml
<!-- backend/src/main/resources/logback-spring.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/withbuddy/backend.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/withbuddy/backend.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

### 7.3 모니터링 메트릭

```yaml
핵심 메트릭:
  가용성:
    - Uptime: > 99.9%
    - Health Check Success Rate: 100%
  
  성능:
    - API Response Time (P95): < 500ms
    - API Response Time (P99): < 1s
    - Database Query Time (P95): < 100ms
  
  에러:
    - 5xx Error Rate: < 0.1%
    - 4xx Error Rate: < 1%
  
  리소스:
    - CPU Usage: < 70%
    - Memory Usage: < 80%
    - Disk Usage: < 80%
```

---

## 8. 장애 대응

### 8.1 장애 유형별 대응

| 장애 | 증상 | 원인 | 대응 |
|------|------|------|------|
| **Backend Down** | 503 에러 | 서버 크래시 | 컨테이너 재시작 |
| **Database Down** | Connection timeout | DB 서버 문제 | 프라이머리 전환 |
| **High Latency** | 느린 응답 | 트래픽 급증 | Auto Scaling |
| **Memory Leak** | OOM 에러 | 메모리 누수 | 컨테이너 재시작 + 분석 |

### 8.2 장애 대응 절차

```
1. 장애 감지 (모니터링 알람)
   ↓
2. 장애 확인 (로그, 메트릭)
   ↓
3. 임시 조치 (재시작, 스케일 업)
   ↓
4. 근본 원인 분석
   ↓
5. 영구 해결책 적용
   ↓
6. 사후 분석 (Post-mortem)
```

### 8.3 긴급 연락망

```yaml
장애 레벨:
  Critical (P0):
    - 서비스 완전 중단
    - 데이터 유실 위험
    → 즉시 전체 팀 호출
  
  High (P1):
    - 주요 기능 장애
    - 성능 심각 저하
    → 30분 내 대응팀 소집
  
  Medium (P2):
    - 일부 기능 장애
    - 성능 경미 저하
    → 업무시간 내 대응
  
  Low (P3):
    - 비핵심 기능 오류
    → 다음 스프린트 계획
```

---

## 부록

### A. 배포 스크립트

```bash
#!/bin/bash
# deploy.sh

set -e

ENV=$1  # dev, staging, prod

if [ -z "$ENV" ]; then
  echo "Usage: ./deploy.sh [dev|staging|prod]"
  exit 1
fi

echo "🚀 Deploying to $ENV environment..."

# Frontend 배포
echo "📦 Deploying Frontend..."
cd frontend
if [ "$ENV" = "prod" ]; then
  vercel --prod
else
  vercel
fi

# Backend 배포
echo "📦 Deploying Backend..."
cd ../backend
./gradlew build -x test
docker build -t withbuddy-backend:latest .

# 서버에 배포
if [ "$ENV" = "prod" ]; then
  ssh user@api.withbuddy.com << 'EOF'
    cd /opt/withbuddy
    docker-compose pull backend
    docker-compose up -d backend
    echo "✅ Backend deployed"
EOF
fi

echo "✅ Deployment complete!"
```

### B. 체크리스트 템플릿

```markdown
## 배포 체크리스트 - [버전] [날짜]

### 배포 전
- [ ] PR 리뷰 완료
- [ ] 테스트 통과 (100%)
- [ ] Staging 테스트 완료
- [ ] DB 마이그레이션 스크립트 준비
- [ ] 롤백 계획 수립
- [ ] 팀 공지

### 배포 중
- [ ] DB 백업 생성
- [ ] 마이그레이션 실행
- [ ] 배포 실행
- [ ] 헬스 체크 통과
- [ ] 스모크 테스트 완료

### 배포 후
- [ ] 에러 로그 모니터링 (30분)
- [ ] 메트릭 정상 확인
- [ ] 사용자 피드백 확인
- [ ] 배포 완료 공지

### 이슈
- 없음

### 롤백 여부
- [ ] 필요 없음
- [ ] 롤백 완료
```

---

