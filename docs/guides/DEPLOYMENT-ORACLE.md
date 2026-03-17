# Oracle Cloud 배포 가이드

> WithBuddy Oracle Cloud 배포 완벽 가이드

## 📋 목차
- [인프라 개요](#인프라-개요)
- [Oracle Cloud 리소스 생성](#oracle-cloud-리소스-생성)
- [VCN 및 네트워크 설정](#vcn-및-네트워크-설정)
- [Backend 배포](#backend-배포-oracle-compute)
- [AI 서버 배포](#ai-서버-배포-oracle-compute)
- [MySQL 배포](#mysql-배포-oracle-compute)
- [Frontend 배포](#frontend-배포-cloudflare-pages)
- [GitHub Actions 설정](#github-actions-설정)

---

## 인프라 개요

```
┌─────────────────────────────────────────────────────────┐
│                    인터넷 사용자                          │
└─────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
        ▼                 ▼                 ▼
   ┌──────────┐    ┌──────────┐      ┌──────────┐
   │Cloudflare│    │  Oracle  │      │  Oracle  │
   │  Pages   │    │ Compute  │      │ Compute  │
   └──────────┘    └──────────┘      └──────────┘
   Frontend         Backend            AI Server
   (React)         Spring Boot         FastAPI
                        │
                        │ VCN Private Network
                        ▼
                   ┌──────────┐
                   │  Oracle  │
                   │ Compute  │
                   └──────────┘
                   MySQL 8.0
```

### 리소스 구성
```yaml
Frontend:
  - Platform: Cloudflare Pages
  - Domain: withbuddy.pages.dev (또는 커스텀 도메인)
  - CDN: 자동 제공
  - HTTPS: 자동 제공

Backend:
  - Provider: Oracle Cloud Compute (Always Free Tier 가능)
  - OS: Ubuntu 22.04
  - Shape: VM.Standard.E2.1.Micro (Always Free)
  - IP: 공인 IP 할당
  - Port: 8080 (Spring Boot)

AI Server:
  - Provider: Oracle Cloud Compute
  - OS: Ubuntu 22.04
  - Shape: VM.Standard.E2.1.Micro (Always Free)
  - IP: 공인 IP 할당 (또는 Private IP)
  - Port: 8000 (FastAPI)

MySQL:
  - Provider: Oracle Cloud Compute
  - OS: Ubuntu 22.04
  - Shape: VM.Standard.E2.1.Micro (Always Free)
  - IP: Private IP만 사용 (VCN 내부)
  - Port: 3306
```

---

## Oracle Cloud 리소스 생성

### 1. 컴퓨트 인스턴스 생성 (3개)

**Backend 인스턴스**
```
Name: withbuddy-backend
Image: Canonical Ubuntu 22.04
Shape: VM.Standard.E2.1.Micro (Always Free)
Network: 공인 IP 할당
Boot Volume: 50GB
```

**AI 서버 인스턴스**
```
Name: withbuddy-ai
Image: Canonical Ubuntu 22.04
Shape: VM.Standard.E2.1.Micro (Always Free)
Network: 공인 IP 할당
Boot Volume: 50GB
```

**MySQL 인스턴스**
```
Name: withbuddy-mysql
Image: Canonical Ubuntu 22.04
Shape: VM.Standard.E2.1.Micro (Always Free)
Network: Private IP만 (공인 IP 미할당)
Boot Volume: 50GB
```

### 2. SSH 키 생성 및 등록
```bash
# 로컬에서 SSH 키 생성
ssh-keygen -t rsa -b 4096 -f ~/.ssh/withbuddy_oracle

# 공개키 내용 복사
cat ~/.ssh/withbuddy_oracle.pub

# Oracle Cloud 인스턴스 생성 시 공개키 붙여넣기
```

---

## VCN 및 네트워크 설정

### 1. VCN (Virtual Cloud Network) 생성
```
Name: withbuddy-vcn
CIDR Block: 10.0.0.0/16
```

### 2. 서브넷 생성
```
Public Subnet:
  Name: withbuddy-public-subnet
  CIDR: 10.0.1.0/24
  Purpose: Backend, AI 서버

Private Subnet:
  Name: withbuddy-private-subnet
  CIDR: 10.0.2.0/24
  Purpose: MySQL
```

### 3. 보안 목록 (Security List) 설정

**Public Subnet 보안 규칙**
```
Ingress Rules:
  - 22 (SSH) from 0.0.0.0/0
  - 8080 (Backend) from 0.0.0.0/0
  - 8000 (AI) from 0.0.0.0/0
  - 443 (HTTPS) from 0.0.0.0/0

Egress Rules:
  - All traffic to 0.0.0.0/0
```

**Private Subnet 보안 규칙**
```
Ingress Rules:
  - 3306 (MySQL) from 10.0.1.0/24 (Public Subnet만)

Egress Rules:
  - All traffic to 0.0.0.0/0
```

### 4. 인터넷 게이트웨이 설정
```
Name: withbuddy-internet-gateway
Attached to: withbuddy-vcn
```

### 5. 라우팅 테이블
```
Public Subnet Route Table:
  Destination: 0.0.0.0/0
  Target: Internet Gateway
```

---

## Backend 배포 (Oracle Compute)

### 1. 서버 접속
```bash
ssh -i ~/.ssh/withbuddy_oracle ubuntu@<BACKEND_PUBLIC_IP>
```

### 2. 시스템 업데이트
```bash
sudo apt update
sudo apt upgrade -y
```

### 3. Java 21 설치
```bash
sudo apt install openjdk-21-jdk -y
java -version
```

### 4. 디렉토리 생성
```bash
mkdir -p /home/ubuntu/withbuddy
```

### 5. systemd 서비스 생성

**/etc/systemd/system/withbuddy.service**
```ini
[Unit]
Description=WithBuddy Spring Boot Application
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/withbuddy
ExecStart=/usr/bin/java -jar /home/ubuntu/withbuddy/app.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="jdbc:mysql://10.0.2.10:3306/withbuddy?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=withbuddy \
  --spring.datasource.password="${DB_PASSWORD}" \
  --ai.api.url="${AI_API_URL}"

Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

Environment="DB_PASSWORD=your_password"
Environment="JWT_SECRET=your_jwt_secret"
Environment="AI_API_URL=http://10.0.1.20:8000"

[Install]
WantedBy=multi-user.target
```

### 6. 방화벽 설정 (UFW)
```bash
sudo apt install ufw
sudo ufw allow 22
sudo ufw allow 8080
sudo ufw enable
sudo ufw status
```

---

## AI 서버 배포 (Oracle Compute)

### 1. 서버 접속
```bash
ssh -i ~/.ssh/withbuddy_oracle ubuntu@<AI_PUBLIC_IP>
```

### 2. Python 3.11 설치
```bash
sudo apt update
sudo apt install python3.11 python3.11-venv python3-pip -y
```

### 3. 프로젝트 디렉토리 생성
```bash
mkdir -p /home/ubuntu/withbuddy/ai
```

### 4. systemd 서비스 생성

**/etc/systemd/system/withbuddy-ai.service**
```ini
[Unit]
Description=WithBuddy AI FastAPI Application
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/withbuddy/ai
ExecStart=/home/ubuntu/withbuddy/ai/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=on-failure
RestartSec=10

Environment="OPENAI_API_KEY=your_api_key"
Environment="PINECONE_API_KEY=your_api_key"
Environment="PINECONE_ENVIRONMENT=your_env"
Environment="PINECONE_INDEX_NAME=withbuddy-documents"

[Install]
WantedBy=multi-user.target
```

### 5. 방화벽 설정
```bash
sudo ufw allow 22
sudo ufw allow 8000
sudo ufw enable
```

---

## MySQL 배포 (Oracle Compute)

### 1. 서버 접속
```bash
# Public Subnet의 Backend 서버를 통해 접속
ssh -i ~/.ssh/withbuddy_oracle ubuntu@<BACKEND_PUBLIC_IP>
ssh ubuntu@<MYSQL_PRIVATE_IP>
```

### 2. MySQL 설치
```bash
sudo apt update
sudo apt install mysql-server -y
```

### 3. MySQL 보안 설정
```bash
sudo mysql_secure_installation
```

### 4. 데이터베이스 및 사용자 생성
```bash
sudo mysql

CREATE DATABASE withbuddy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'withbuddy'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON withbuddy.* TO 'withbuddy'@'%';
FLUSH PRIVILEGES;
EXIT;
```

### 5. 외부 접속 허용 설정

**/etc/mysql/mysql.conf.d/mysqld.cnf**
```ini
[mysqld]
bind-address = 0.0.0.0
```

```bash
sudo systemctl restart mysql
```

### 6. 자동 백업 스크립트

**/home/ubuntu/backup-mysql.sh**
```bash
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/home/ubuntu/mysql-backups"
mkdir -p $BACKUP_DIR

mysqldump -u withbuddy -p'your_password' withbuddy | gzip > $BACKUP_DIR/withbuddy_$DATE.sql.gz

# 7일 이상 된 백업 삭제
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
```

```bash
chmod +x /home/ubuntu/backup-mysql.sh

# cron 등록 (매일 새벽 3시)
crontab -e
0 3 * * * /home/ubuntu/backup-mysql.sh
```

---

## Frontend 배포 (Cloudflare Pages)

### 1. Cloudflare 계정 생성
https://pages.cloudflare.com

### 2. GitHub 연동
```
1. Cloudflare Pages > Create a project
2. Connect to Git
3. WithBuddyAi/withbuddy 선택
4. 설정:

Framework preset: Vite
Build command: npm run build
Build output directory: dist
Root directory: frontend
```

### 3. 환경변수 설정
```
Settings > Environment Variables

Production:
  VITE_API_BASE_URL=http://<BACKEND_PUBLIC_IP>:8080

Preview:
  VITE_API_BASE_URL=http://<BACKEND_PUBLIC_IP>:8080
```

### 4. 커스텀 도메인 (선택)
```
Settings > Custom domains
도메인 추가 및 DNS 설정
```

---

## GitHub Actions 설정

### 1. GitHub Secrets 설정

**Repository > Settings > Secrets and variables > Actions**
```
SSH_PRIVATE_KEY=<withbuddy_oracle 개인키 전체 내용>
BACKEND_SERVER_IP=<Backend 공인 IP>
AI_SERVER_IP=<AI 서버 공인 IP>
SERVER_USER=ubuntu
DB_PASSWORD=<MySQL 비밀번호>
JWT_SECRET=<JWT Secret>
AI_API_URL=http://10.0.1.20:8000
OPENAI_API_KEY=<OpenAI API Key>
PINECONE_API_KEY=<Pinecone API Key>
```

### 2. Backend 배포 워크플로우

**.github/workflows/backend-deploy.yml**
```yaml
name: Deploy Backend to Oracle Cloud

on:
  push:
    branches: [ main ]
    paths:
      - 'backend/**'
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Make gradlew executable
        working-directory: ./backend
        run: chmod +x gradlew
      
      - name: Build with Gradle
        working-directory: ./backend
        run: ./gradlew clean build -x test --no-daemon
      
      - name: List JAR files
        working-directory: ./backend
        run: ls -lh build/libs/
      
      - name: Deploy to Oracle Cloud
        env:
          SSH_PRIVATE_KEY: ${{ secrets.SSH_PRIVATE_KEY }}
          SERVER_IP: ${{ secrets.BACKEND_SERVER_IP }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
          JWT_SECRET: ${{ secrets.JWT_SECRET }}
          AI_API_URL: ${{ secrets.AI_API_URL }}
        run: |
          mkdir -p ~/.ssh
          chmod 700 ~/.ssh
          echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
          chmod 600 ~/.ssh/id_rsa
          ssh-keyscan -H "$SERVER_IP" >> ~/.ssh/known_hosts 2>/dev/null
          
          echo "=== Deploying JAR ==="
          scp -i ~/.ssh/id_rsa -o StrictHostKeyChecking=no \
            backend/build/libs/withbuddy-0.0.1-SNAPSHOT.jar \
            $SERVER_USER@$SERVER_IP:/home/ubuntu/withbuddy/app.jar
          
          echo "=== Starting Application ==="
          ssh -i ~/.ssh/id_rsa -o StrictHostKeyChecking=no $SERVER_USER@$SERVER_IP \
            "DB_PASSWORD='$DB_PASSWORD' JWT_SECRET='$JWT_SECRET' AI_API_URL='$AI_API_URL' bash -s" << 'ENDSSH'
          
          # 기존 프로세스 종료
          pkill -9 -f "java -jar" || true
          sleep 3
          
          # 재확인
          if pgrep -f "java -jar"; then
            echo "Still running, force killing..."
            pkill -9 -f "java -jar"
            sleep 2
          fi          
          
          # 환경변수 확인
          echo "DB_PASSWORD length: ${#DB_PASSWORD}"
          echo "JWT_SECRET length: ${#JWT_SECRET}"
          
          # 애플리케이션 시작
          nohup java -jar /home/ubuntu/withbuddy/app.jar \
            --spring.profiles.active=prod \
            --spring.datasource.url="jdbc:mysql://10.0.2.10:3306/withbuddy?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true" \
            --spring.datasource.username=withbuddy \
            --spring.datasource.password="${DB_PASSWORD}" \
            --jwt.secret="${JWT_SECRET}" \
            --ai.api.url="${AI_API_URL}" \
            > /home/ubuntu/withbuddy/app.log 2>&1 &
          
          APP_PID=$!
          echo "Started with PID: $APP_PID"
          sleep 10
          
          if ps -p $APP_PID > /dev/null 2>&1; then
            echo "✅ Application is running (PID: $APP_PID)"
            ps aux | grep "java -jar" | grep -v grep
            echo ""
            echo "=== Last 30 lines of log ==="
            tail -30 /home/ubuntu/withbuddy/app.log
          else
            echo "❌ Application failed to start"
            echo "=== Last 100 lines of log ==="
            tail -100 /home/ubuntu/withbuddy/app.log
            exit 1
          fi
          ENDSSH
      
      - name: Deployment completed
        run: echo "✅ Backend deployment completed!"
```

### 3. AI 서버 배포 워크플로우

**.github/workflows/ai-deploy.yml**
```yaml
name: Deploy AI Server to Oracle Cloud

on:
  push:
    branches: [ main ]
    paths:
      - 'ai/**'
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v3
      
      - name: Deploy to Oracle Cloud
        env:
          SSH_PRIVATE_KEY: ${{ secrets.SSH_PRIVATE_KEY }}
          SERVER_IP: ${{ secrets.AI_SERVER_IP }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          PINECONE_API_KEY: ${{ secrets.PINECONE_API_KEY }}
        run: |
          mkdir -p ~/.ssh
          chmod 700 ~/.ssh
          echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
          chmod 600 ~/.ssh/id_rsa
          ssh-keyscan -H "$SERVER_IP" >> ~/.ssh/known_hosts 2>/dev/null
          
          echo "=== Deploying AI Application ==="
          scp -i ~/.ssh/id_rsa -r -o StrictHostKeyChecking=no \
            ai/* \
            $SERVER_USER@$SERVER_IP:/home/ubuntu/withbuddy/ai/
          
          echo "=== Starting AI Server ==="
          ssh -i ~/.ssh/id_rsa -o StrictHostKeyChecking=no $SERVER_USER@$SERVER_IP \
            "OPENAI_API_KEY='$OPENAI_API_KEY' PINECONE_API_KEY='$PINECONE_API_KEY' bash -s" << 'ENDSSH'
          
          cd /home/ubuntu/withbuddy/ai
          
          # 가상환경 확인 및 생성
          if [ ! -d "venv" ]; then
            python3.11 -m venv venv
          fi
          
          source venv/bin/activate
          pip install -r requirements.txt
          
          # 기존 프로세스 종료
          pkill -9 -f "uvicorn" || true
          sleep 2
          
          # .env 파일 생성
          cat > .env << EOF
OPENAI_API_KEY=${OPENAI_API_KEY}
PINECONE_API_KEY=${PINECONE_API_KEY}
PINECONE_ENVIRONMENT=gcp-starter
PINECONE_INDEX_NAME=withbuddy-documents
EOF
          
          # 애플리케이션 시작
          nohup venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 \
            > /home/ubuntu/withbuddy/ai/app.log 2>&1 &
          
          APP_PID=$!
          echo "Started with PID: $APP_PID"
          sleep 5
          
          if ps -p $APP_PID > /dev/null 2>&1; then
            echo "✅ AI Server is running (PID: $APP_PID)"
            tail -20 /home/ubuntu/withbuddy/ai/app.log
          else
            echo "❌ AI Server failed to start"
            tail -50 /home/ubuntu/withbuddy/ai/app.log
            exit 1
          fi
          ENDSSH
      
      - name: Deployment completed
        run: echo "✅ AI Server deployment completed!"
```

---

## 배포 확인

### 서비스 상태 확인
```bash
# Backend
curl http://<BACKEND_PUBLIC_IP>:8080/actuator/health

# AI Server
curl http://<AI_PUBLIC_IP>:8000/health

# Frontend
# 브라우저에서 Cloudflare Pages URL 접속
```

### 로그 확인
```bash
# Backend
ssh -i ~/.ssh/withbuddy_oracle ubuntu@<BACKEND_PUBLIC_IP>
tail -f /home/ubuntu/withbuddy/app.log

# AI Server
ssh -i ~/.ssh/withbuddy_oracle ubuntu@<AI_PUBLIC_IP>
tail -f /home/ubuntu/withbuddy/ai/app.log
```

---

## 비용 최적화 (Always Free Tier)

Oracle Cloud Always Free Tier 활용:
- ✅ VM.Standard.E2.1.Micro 인스턴스 2개 무료
- ✅ 총 200GB 블록 볼륨 무료
- ✅ 10TB 아웃바운드 트래픽 무료

Cloudflare Pages:
- ✅ 무제한 요청 무료
- ✅ 무제한 대역폭 무료
- ✅ CDN 무료

**총 비용: $0** (무료!)

---

## 다음 단계

- [개발 환경 설정](./SETUP.md) - 로컬 개발 환경 구축
- [API 문서](../PLANNED_API.md) - API 엔드포인트
