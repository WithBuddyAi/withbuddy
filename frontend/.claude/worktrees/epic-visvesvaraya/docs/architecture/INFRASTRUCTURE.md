# WithBuddy 인프라 구조

> 클라우드 인프라 및 네트워크 구성

**최종 업데이트**: 2026-03-23  
**버전**: 1.1.0

---

## 📋 목차

- [1. 인프라 개요](#1-인프라-개요)
- [2. 네트워크 구성](#2-네트워크-구성)
- [3. 보안 그룹](#3-보안-그룹)
- [4. 스토리지 구조](#4-스토리지-구조)
- [5. 서버 스펙](#5-서버-스펙)
- [6. 확장성 설계](#6-확장성-설계)
- [7. 백업 전략](#7-백업-전략)

---

## 1. 인프라 개요

### 1.1 클라우드 선택지

WithBuddy는 다음 클라우드 중 하나를 선택 예정:

| 클라우드 | 장점 | 단점 | 권장 사유 |
|---------|------|------|----------|
| **AWS** | 가장 많은 서비스, 풍부한 레퍼런스 | 비용 복잡 | 범용적, 안정적 |
| **GCP** | AI/ML 특화, BigQuery | 서비스 수 적음 | AI 워크로드 최적 |
| **Oracle Cloud** | 무료 티어 관대, 가성비 | 생태계 작음 | 스타트업 초기 |

### 1.2 인프라 구성 요소

```
┌─────────────────────────────────────────────────────┐
│              Cloudflare (DNS & CDN)                  │
└──────────────────────┬──────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
        ↓                             ↓
┌───────────────┐            ┌─────────────────────┐
│    Vercel     │            │  Cloud Provider     │
│   (Frontend)  │            │  (Backend/AI/DB)    │
└───────────────┘            └─────────────────────┘
```

---

## 2. 네트워크 구성

### 2.1 VCN (Virtual Cloud Network) 설계

```
┌─────────────────── VCN (10.0.0.0/16) ───────────────────┐
│                                                           │
│  Public Subnet (10.0.1.0/24)                            │
│  ┌──────────────────────────────────────────┐           │
│  │  - Load Balancer (ALB/NLB)               │           │
│  │  - NAT Gateway                            │           │
│  │  - Bastion Host (관리용, 선택)           │           │
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

### 2.2 Subnet 구성

#### Public Subnet (10.0.1.0/24)
**용도**: 외부 인터넷과 통신하는 리소스

| 리소스 | 용도 | 비고 |
|-------|------|------|
| Load Balancer | HTTPS 요청 받기 | ALB 또는 NLB |
| NAT Gateway | Private Subnet의 아웃바운드 | 고가용성 |
| Bastion Host | SSH 접속 (관리용) | 선택 사항 |

**라우팅 테이블**:
```
Destination         Target
0.0.0.0/0          Internet Gateway
10.0.0.0/16        Local
```

#### Private Subnet - App (10.0.2.0/24)
**용도**: 애플리케이션 서버

| 리소스 | 포트 | 접근 |
|-------|------|------|
| Backend (Spring Boot) | 8080 | Load Balancer, Vercel |
| AI Server (FastAPI) | 8000 | Backend only |
| Redis | 6379 | Backend, AI Server |

**라우팅 테이블**:
```
Destination         Target
0.0.0.0/0          NAT Gateway (아웃바운드)
10.0.0.0/16        Local
```

#### Private Subnet - DB (10.0.3.0/24)
**용도**: 데이터베이스

| 리소스 | 포트 | 접근 |
|-------|------|------|
| MySQL 8.0 | 3306 | Backend, AI Server |

**라우팅 테이블**:
```
Destination         Target
10.0.0.0/16        Local
```
**중요**: 외부 인터넷 접근 불가 (보안)

---

## 3. 보안 그룹

### 3.1 Load Balancer Security Group

```yaml
Name: sg-withbuddy-lb
Description: Load Balancer security group

Inbound Rules:
  - Type: HTTPS
    Protocol: TCP
    Port: 443
    Source: 0.0.0.0/0
    Description: Allow HTTPS from internet
    
  - Type: HTTP
    Protocol: TCP
    Port: 80
    Source: 0.0.0.0/0
    Description: Redirect to HTTPS

Outbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 8080
    Destination: sg-withbuddy-backend
    Description: Forward to Backend
```

### 3.2 Backend Security Group

```yaml
Name: sg-withbuddy-backend
Description: Backend (Spring Boot) security group

Inbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 8080
    Source: sg-withbuddy-lb
    Description: From Load Balancer
    
  - Type: Custom TCP
    Protocol: TCP
    Port: 8080
    Source: <Vercel IP ranges>
    Description: From Vercel Frontend

Outbound Rules:
  - Type: MySQL/Aurora
    Protocol: TCP
    Port: 3306
    Destination: sg-withbuddy-mysql
    Description: To MySQL
    
  - Type: Custom TCP
    Protocol: TCP
    Port: 8000
    Destination: sg-withbuddy-ai
    Description: To AI Server
    
  - Type: Custom TCP
    Protocol: TCP
    Port: 6379
    Destination: sg-withbuddy-redis
    Description: To Redis
    
  - Type: HTTPS
    Protocol: TCP
    Port: 443
    Destination: 0.0.0.0/0
    Description: Anthropic Claude API, Object Storage
```

### 3.3 AI Server Security Group

```yaml
Name: sg-withbuddy-ai
Description: AI Server (FastAPI) security group

Inbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 8000
    Source: sg-withbuddy-backend
    Description: From Backend ONLY

Outbound Rules:
  - Type: MySQL/Aurora
    Protocol: TCP
    Port: 3306
    Destination: sg-withbuddy-mysql
    Description: To MySQL
    
  - Type: Custom TCP
    Protocol: TCP
    Port: 6379
    Destination: sg-withbuddy-redis
    Description: To Redis
    
  - Type: HTTPS
    Protocol: TCP
    Port: 443
    Destination: 0.0.0.0/0
    Description: Anthropic Claude API
```

### 3.4 MySQL Security Group

```yaml
Name: sg-withbuddy-mysql
Description: MySQL Database security group

Inbound Rules:
  - Type: MySQL/Aurora
    Protocol: TCP
    Port: 3306
    Source: sg-withbuddy-backend
    Description: From Backend
    
  - Type: MySQL/Aurora
    Protocol: TCP
    Port: 3306
    Source: sg-withbuddy-ai
    Description: From AI Server

Outbound Rules:
  - None (데이터베이스는 아웃바운드 불필요)
```

### 3.5 Redis Security Group

```yaml
Name: sg-withbuddy-redis
Description: Redis Cache security group

Inbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 6379
    Source: sg-withbuddy-backend
    Description: From Backend
    
  - Type: Custom TCP
    Protocol: TCP
    Port: 6379
    Source: sg-withbuddy-ai
    Description: From AI Server

Outbound Rules:
  - None
```

---

## 4. 스토리지 구조

### 4.1 Object Storage (S3/GCS/OCI)

#### 버킷 구조

```
withbuddy-storage/
├── documents/              # 인사/행정 문서
│   ├── templates/         # 문서 템플릿
│   │   └── hr_policy_template.pdf
│   ├── user-uploads/      # 사용자 업로드
│   │   ├── company_1001/
│   │   │   ├── 2024/
│   │   │   │   └── 03/
│   │   │   │       └── document_123.pdf
│   │   │   └── 2024/
│   │   └── company_1002/
│   └── generated/         # AI 생성 리포트
│       └── company_1001/
│           └── reports/
│               └── week_1_report.pdf
├── avatars/               # 프로필 이미지
│   ├── company_1001/
│   │   └── user_uuid_123.jpg
│   └── company_1002/
└── backups/               # 백업 파일
    ├── db/
    │   ├── daily/
    │   ├── weekly/
    │   └── monthly/
    └── logs/
```

**MVP 메모**: ChromaDB 임베딩 파일은 AI 서버 로컬 디스크에 저장하며, 별도 Object Storage로 분리하지 않는다.

#### 접근 권한 정책

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::withbuddy-storage/*"
    }
  ]
}
```

**접근 방식**:
- ✅ Backend: IAM Role 기반 접근
- ✅ Frontend: Presigned URL (임시 다운로드)
- ❌ Public Read: 없음 (모든 파일 Private)

#### Lifecycle 정책

```yaml
documents/:
  - Transition to Infrequent Access: 90 days
  - Transition to Glacier: 1 year
  - Expire: Never

backups/daily/:
  - Expire: 30 days

backups/weekly/:
  - Expire: 90 days

backups/monthly/:
  - Expire: 1 year
```

### 4.2 데이터베이스 스토리지

#### MySQL Storage

```yaml
Instance Type: db.t3.medium (프로덕션)
Storage Type: SSD (gp3)
Allocated Storage: 100 GB
Max Storage: 500 GB (Auto Scaling)
IOPS: 3000
Throughput: 125 MiB/s

Backup:
  Retention: 7 days
  Window: 03:00-04:00 UTC (한국시간 12:00-13:00)
  
Maintenance:
  Window: Sun 04:00-05:00 UTC (한국시간 일요일 13:00-14:00)
```

#### Redis Storage

```yaml
Instance Type: cache.t3.medium
Engine Version: 7.0
Replicas: 1 (고가용성)
Max Memory: 3.09 GB
Eviction Policy: allkeys-lru

Snapshot:
  Frequency: Daily
  Retention: 7 days
```

---

## 5. 서버 스펙

### 5.1 Backend Server

#### 개발 환경
```yaml
Instance Type: t3.small
CPU: 2 vCPU
RAM: 2 GB
Storage: 20 GB SSD
OS: Ubuntu 22.04/24.04 LTS
```

#### 프로덕션 환경
```yaml
Instance Type: t3.medium
CPU: 2 vCPU
RAM: 4 GB
Storage: 50 GB SSD
OS: Ubuntu 22.04/24.04 LTS
Auto Scaling: 2-4 instances
```

### 5.2 AI Server

#### 개발 환경
```yaml
Instance Type: t3.medium
CPU: 2 vCPU
RAM: 4 GB
Storage: 30 GB SSD
OS: Ubuntu 22.04/24.04 LTS
```

#### 프로덕션 환경 (MVP)
```yaml
Instance Type: t3.medium
CPU: 2 vCPU
RAM: 4 GB
Storage: 50 GB SSD
OS: Ubuntu 22.04/24.04 LTS
GPU: 없음
```

### 5.3 Database Server

#### 개발 환경
```yaml
Instance Type: db.t3.micro
CPU: 2 vCPU
RAM: 1 GB
Storage: 20 GB
```

#### 프로덕션 환경
```yaml
Instance Type: db.t3.medium
CPU: 2 vCPU
RAM: 4 GB
Storage: 100 GB (Auto Scaling to 500 GB)
Multi-AZ: Yes (고가용성)
```

### 5.4 Redis Cache

#### 개발 환경
```yaml
Instance Type: cache.t3.micro
RAM: 0.5 GB
```

#### 프로덕션 환경
```yaml
Instance Type: cache.t3.medium
RAM: 3.09 GB
Replicas: 1
```

---

## 6. 확장성 설계

### 6.1 Auto Scaling

#### Backend Auto Scaling 정책

```yaml
Scaling Policy:
  Metric: CPU Utilization
  Target: 70%
  
  Scale Out:
    Threshold: 70% for 2 minutes
    Action: Add 1 instance
    Cooldown: 300 seconds
    
  Scale In:
    Threshold: 30% for 5 minutes
    Action: Remove 1 instance
    Cooldown: 300 seconds

Limits:
  Min Instances: 2
  Max Instances: 4
```

#### Load Balancer 설정

```yaml
Type: Application Load Balancer (ALB)

Health Check:
  Protocol: HTTP
  Path: /actuator/health
  Interval: 30 seconds
  Timeout: 5 seconds
  Healthy Threshold: 2
  Unhealthy Threshold: 3

Target Group:
  Protocol: HTTP
  Port: 8080
  Deregistration Delay: 30 seconds
  
Sticky Sessions:
  Enabled: No (Stateless)
```

### 6.2 데이터베이스 확장

#### Read Replica

```yaml
Master: db.t3.medium (Write)
Replica 1: db.t3.medium (Read)

Traffic Distribution:
  Write: Master
  Read: Load Balanced (Master + Replica)
  
Failover:
  Automatic: Yes
  Failover Time: ~60 seconds
```

#### Connection Pool

```yaml
# Backend - HikariCP
spring.datasource.hikari:
  maximum-pool-size: 20
  minimum-idle: 5
  connection-timeout: 30000
  idle-timeout: 600000
  max-lifetime: 1800000
```

---

## 7. 백업 전략

### 7.1 데이터베이스 백업

#### 자동 백업

```yaml
Frequency: Daily
Time: 03:00 UTC (한국시간 12:00)
Retention: 7 days

Backup Type:
  - Automated Snapshots
  - Transaction Logs (Point-in-Time Recovery)
```

#### 수동 스냅샷

```yaml
Frequency: Weekly (매주 일요일)
Retention: 30 days
Purpose: Major changes, deployments
```

#### 복구 절차

```bash
# 1. 최신 스냅샷 확인
aws rds describe-db-snapshots \
  --db-instance-identifier withbuddy-mysql

# 2. 새 인스턴스로 복원
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier withbuddy-mysql-restored \
  --db-snapshot-identifier snapshot-20260317

# 3. 복원 확인
aws rds describe-db-instances \
  --db-instance-identifier withbuddy-mysql-restored

# 4. 애플리케이션 연결 변경
# (connection string 업데이트)
```

### 7.2 Object Storage 백업

```yaml
Versioning: Enabled
Replication: Cross-Region (선택)

Lifecycle:
  - Current versions: Keep forever
  - Non-current versions: Delete after 30 days
  
Backup:
  - Critical documents: Daily sync to backup bucket
  - Backup bucket: Different region
```

### 7.3 애플리케이션 백업

```yaml
Code:
  - Repository: GitHub
  - Backup: Git commits, tags
  
Configuration:
  - Location: Git repository (encrypted)
  - Secrets: AWS Secrets Manager / HashiCorp Vault
  
Logs:
  - Storage: CloudWatch Logs / ELK
  - Retention: 90 days
```

---

## 8. 모니터링 & 알림

### 8.1 CloudWatch 메트릭

```yaml
Backend:
  - CPUUtilization
  - MemoryUtilization
  - NetworkIn/Out
  - DiskReadOps/WriteOps

Database:
  - CPUUtilization
  - DatabaseConnections
  - ReadLatency/WriteLatency
  - FreeStorageSpace

Load Balancer:
  - TargetResponseTime
  - HealthyHostCount
  - RequestCount
  - HTTPCode_Target_2XX_Count
```

### 8.2 알림 설정

```yaml
Critical Alerts (즉시 알림):
  - Database CPU > 90% for 5 minutes
  - Backend healthy hosts < 1
  - Disk space < 10%
  
Warning Alerts (30분 후 알림):
  - Database CPU > 70% for 10 minutes
  - Backend healthy hosts < 2
  - Disk space < 20%
  
Notification:
  - Slack: #alerts-critical
  - Email: ops@withbuddy.com
```

---

## 부록

### A. 클라우드별 서비스 매핑

| 기능 | AWS | GCP | Oracle Cloud |
|------|-----|-----|--------------|
| 컴퓨팅 | EC2 | Compute Engine | Compute |
| 데이터베이스 | RDS | Cloud SQL | MySQL Database |
| 스토리지 | S3 | Cloud Storage | Object Storage |
| 로드밸런서 | ALB/NLB | Cloud Load Balancing | Load Balancer |
| 캐시 | ElastiCache | Memorystore | Cache (Redis) |
| VPN | VPC | VPC | VCN |

### B. 비용 예측 (월간, AWS 기준)

```
Backend (t3.medium x2):        $60
AI Server (t3.medium):         $30
Database (db.t3.medium):       $70
Redis (cache.t3.medium):       $40
Load Balancer:                 $20
Storage (S3):                  $10
Data Transfer:                 $30
                         ──────────
Total:                        $260/month
```

### C. 체크리스트

**인프라 구축 순서**:
- [ ] VCN 생성
- [ ] Subnet 구성 (Public, Private-App, Private-DB)
- [ ] Internet Gateway 생성
- [ ] NAT Gateway 생성
- [ ] 라우팅 테이블 설정
- [ ] 보안 그룹 생성
- [ ] Load Balancer 생성
- [ ] EC2 인스턴스 생성 (Backend, AI)
- [ ] RDS MySQL 생성
- [ ] ElastiCache Redis 생성
- [ ] S3 버킷 생성
- [ ] IAM 역할 설정
- [ ] CloudWatch 알람 설정

---

**문서 버전**: 1.1.0  
**작성일**: 2026-03-17
