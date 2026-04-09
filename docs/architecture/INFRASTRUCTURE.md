# WithBuddy 인프라 구조

> 클라우드 인프라 및 네트워크 구성

**최종 업데이트**: 2026-04-09  
**버전**: 1.3.2  
**작성일**: 2026-03-27

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

### 1.1 클라우드 선택

WithBuddy는 **Oracle Cloud(OCI)** 로 결정했음.

**선정 이유**:
- 비용 효율 (Always Free/저비용 리소스 활용)
- 오사카 리전 제공
- Arm 기반 A1 인스턴스 가성비

### 1.2 인프라 구성 요소

[![Infrastructure Overview](./images/infrastructure-overview.png)](./images/infrastructure-overview.png)

모바일에서는 이미지를 탭해 원본을 연 뒤 확대해서 확인하세요.

---

## 2. 네트워크 구성

### 2.1 VCN (Virtual Cloud Network) 설계

현재 구성은 **오사카 리전**에서 **두 개 테넌시 분리**로 운영한다.

[![Network Topology](./images/network-topology.png)](./images/network-topology.png)

모바일에서는 이미지를 탭해 원본을 연 뒤 확대해서 확인하세요.

**중요**: 두 VCN의 CIDR은 반드시 겹치지 않아야 한다.

### 2.2 Cross-Tenancy LPG 피어링

**필수 정보**:
- VCN-A CIDR, VCN-B CIDR
- 양쪽 LPG OCID
- 라우트 테이블 및 보안 목록/NSG

**라우트 테이블 예시**:
```
VCN-A:
Destination         Target
10.0.0.0/16         LPG-A (to VCN-B)

VCN-B:
Destination         Target
10.1.0.0/16         LPG-B (to VCN-A)
```

### 2.3 Subnet 구성

#### VCN-A (AI 테넌시)

**Private Subnet - AI (10.1.2.0/24)**  
용도: AI 서버 전용 (Private)

| 리소스 | 포트 | 접근 |
|-------|------|------|
| AI Server (FastAPI) | 8000 | Backend only (LPG) |

**라우팅 테이블**:
```
Destination         Target
10.0.0.0/16         LPG-A (to VCN-B)
0.0.0.0/0           NAT Gateway (아웃바운드, 선택)
```

#### VCN-B (Backend/DB/Core Services 테넌시)

**Public Subnet (10.0.1.0/24)**  
용도: 외부에서 접근 가능한 Backend

| 리소스 | 포트 | 접근 |
|-------|------|------|
| Backend (Spring Boot) | 8080 | Vercel, 운영자 |

**라우팅 테이블**:
```
Destination         Target
0.0.0.0/0          Internet Gateway
10.0.0.0/16        Local
10.1.0.0/16        LPG-B (to VCN-A)
```

**Private Subnet - DB (10.0.3.0/24)**  
용도: 데이터베이스

| 리소스 | 포트 | 접근 |
|-------|------|------|
| MySQL 8.0 | 3306 | Backend only |

**라우팅 테이블**:
```
Destination         Target
10.0.0.0/16        Local
```

### 2.4 통신 경로 요약

- Frontend → Backend: Public HTTPS → Backend (8080)
- Backend ↔ AI: LPG (VCN-B ↔ VCN-A), 8000
- Backend → MySQL: VCN-B 내부, 3306

---

## 3. 보안 그룹 (NSG/보안 목록)

### 3.1 Backend Public Access

```yaml
Name: sl-withbuddy-backend-public
Description: Backend 공개 접근 규칙 (Public Subnet)

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
  - Type: All
    Protocol: All
    Destination: 0.0.0.0/0
    Description: 일반 아웃바운드
```

### 3.2 Backend Security Group (VCN-B)

```yaml
Name: nsg-withbuddy-backend
Description: Backend (Spring Boot) security group

Inbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 8080
    Source: 0.0.0.0/0
    Description: Public API (필요 시 Vercel IP로 제한)

Outbound Rules:
  - Type: MySQL
    Protocol: TCP
    Port: 3306
    Destination: 10.0.3.0/24
    Description: To MySQL (VCN-B)

  - Type: Custom TCP
    Protocol: TCP
    Port: 8000
    Destination: <VCN-A CIDR>
    Description: To AI Server via LPG

  - Type: HTTPS
    Protocol: TCP
    Port: 443
    Destination: 0.0.0.0/0
    Description: Anthropic Claude API, Object Storage
```

### 3.3 AI Server Security Group (VCN-A)

```yaml
Name: nsg-withbuddy-ai
Description: AI Server (FastAPI) security group

Inbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 8000
    Source: <VCN-B CIDR>
    Description: From Backend via LPG only

Outbound Rules:
  - Type: HTTPS
    Protocol: TCP
    Port: 443
    Destination: 0.0.0.0/0
    Description: Anthropic Claude API
```

### 3.4 MySQL Security Group (VCN-B)

```yaml
Name: nsg-withbuddy-mysql
Description: MySQL Database security group

Inbound Rules:
  - Type: MySQL
    Protocol: TCP
    Port: 3306
    Source: <VCN-B CIDR>
    Description: From Backend subnet

Outbound Rules:
  - None (데이터베이스는 아웃바운드 불필요)
```

---

## 4. 스토리지 구조

### 4.1 Object Storage (OCI)

#### 저장소 역할 분리

WithBuddy는 두 OCI tenancy를 분리 운영하므로 Object Storage도 역할을 분리한다.

- **Tenancy A Object Storage**: 원본 파일 저장소 (`primary`)
- **Tenancy B Object Storage**: 백업 파일 저장소 (`backup`)

핵심 원칙:
- 파일 업로드는 항상 Backend를 통해 제어한다.
- DB에는 외부 공개 URL이 아니라 `namespace + bucket + object key` 메타데이터를 저장한다.
- 다운로드 URL은 요청 시점에만 presigned URL로 생성한다.
- Frontend는 Object Storage에 직접 쓰지 않는다.

#### 권장 버킷 구조

```text
Tenancy A
├── withbuddy-primary-documents
├── withbuddy-primary-assets
└── withbuddy-primary-reports

Tenancy B
├── withbuddy-backup-documents
├── withbuddy-backup-reports
└── withbuddy-backup-db
```

#### 권장 object key 구조

```text
companies/{companyCode}/documents/{documentId}/{timestamp}-{uuid}.pdf
companies/{companyCode}/assets/logo/{timestamp}-{uuid}.png
companies/{companyCode}/users/{userId}/profile/{timestamp}-{uuid}.jpg
companies/{companyCode}/reports/{reportId}/{timestamp}-{uuid}.pdf
db/daily/{yyyy}/{mm}/{dd}/withbuddy.sql.gz
db/weekly/{yyyy}/{ww}/withbuddy.sql.gz
db/monthly/{yyyy}/{mm}/withbuddy.sql.gz
logs/{yyyy}/{mm}/{dd}/backend.log.gz
```

#### 저장 대상별 배치

| 저장 대상 | 위치 | 비고 |
|------|------|------|
| 회사 문서 원본 | Tenancy A `withbuddy-primary-documents` | 문서 업로드 원본 |
| 회사 로고/프로필 이미지 | Tenancy A `withbuddy-primary-assets` | 공개 저장 금지 |
| AI 생성 리포트 PDF | Tenancy A `withbuddy-primary-reports` | 최신본 우선 보관 |
| 문서/리포트 백업본 | Tenancy B backup buckets | 평시 조회용 아님 |
| DB dump 백업 | Tenancy B `withbuddy-backup-db` | 주기 백업 |
| ChromaDB | AI 서버 로컬 디스크 | MVP에서는 Object Storage 분리 안 함 |

#### 접근 방식

- ✅ Backend: OCI SDK/CLI 또는 Instance Principal 기반 접근
- ✅ Frontend: Backend가 발급한 presigned URL로 임시 다운로드
- ✅ Backup Worker: Primary object를 Backup bucket으로 비동기 복제
- ❌ Public Read: 없음
- ❌ Frontend direct upload: 없음

#### Lifecycle 정책

```yaml
Primary documents:
  - Standard 유지: 업로드 후 즉시
  - Infrequent Access 전환: 90일
  - Archive 전환: 365일

Primary reports:
  - Standard 유지: 30일
  - Infrequent Access 전환: 31일
  - Archive 전환: 180일

Backup DB:
  - Daily 보관: 30일
  - Weekly 보관: 90일
  - Monthly 보관: 365일
```

**MVP 메모**: ChromaDB 임베딩 파일은 AI 서버 로컬 디스크에 저장하며, 별도 Object Storage로 분리하지 않는다.

관련 상세 정책은 `docs/OCI_OBJECT_STORAGE_STRATEGY.md`를 따른다.

### 4.2 데이터베이스 스토리지

#### Block Volume 배치

WithBuddy는 Managed MySQL이 아니라 **MySQL on Compute**를 사용하므로 Block Volume을 직접 배분한다.

```yaml
Total Block Volume Budget: 200 GB

Backend boot volume:
  Size: 50 GB
  Purpose: OS, 애플리케이션 바이너리, systemd, nginx

Database/Core boot volume:
  Size: 50 GB
  Purpose: OS, MySQL/Redis/RabbitMQ 실행 환경

MySQL data volume:
  Size: 60 GB
  Purpose: /var/lib/mysql 또는 별도 mount path

Core data/log volume:
  Size: 40 GB
  Purpose:
    - Redis AOF/RDB
    - RabbitMQ data
    - application logs
    - 임시 백업 staging
```

운영 원칙:
- Boot volume과 data volume은 분리한다.
- MySQL 데이터는 별도 volume에 둔다.
- Redis/RabbitMQ와 로그는 DB volume과 분리하거나 최소한 경로를 분리한다.
- 장기 보관 파일은 Block Volume이 아니라 Object Storage로 이동한다.

#### MySQL 저장 원칙

```yaml
Engine: MySQL 8.0 on Compute
Primary Storage: OCI Block Volume
File System: ext4 또는 xfs
Backup Retention:
  Daily: 7일
  Weekly: 4주
  Monthly: 12개월
Backup Target: OCI Object Storage (Tenancy B)
```

---

## 5. 서버 스펙

오사카 리전 기준 실제 운영 사양:

### 5.1 Backend Server (Tenancy B)
```yaml
Shape: VM.Standard.A1.Flex
CPU: 2 OCPU
RAM: 12 GB
Network Bandwidth: 2 Gbps
OS: Canonical Ubuntu 24.04
Subnet: Public (VCN-B)
```

### 5.2 AI Server (Tenancy A)
```yaml
Shape: VM.Standard.A1.Flex
CPU: 4 OCPU
RAM: 24 GB
Network Bandwidth: 4 Gbps
OS: Canonical Ubuntu 24.04
Subnet: Private (VCN-A)
```

### 5.3 Database Server (Tenancy B)
```yaml
Shape: VM.Standard.A1.Flex
CPU: 2 OCPU
RAM: 12 GB
Network Bandwidth: 2 Gbps
OS: Canonical Ubuntu 24.04
Subnet: Private - DB (VCN-B)
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
  - mysqldump 또는 xtrabackup
  - gzip 압축 후 OCI Object Storage 업로드
```

#### 수동 스냅샷

```yaml
Frequency: Weekly (매주 일요일)
Retention: 30 days
Purpose: Major changes, deployments
```

#### 복구 절차

```bash
# 1. 백업 파일 다운로드
oci os object get \
  --bucket-name withbuddy-backup-db \
  --name db/daily/2026/04/09/withbuddy.sql.gz \
  --file withbuddy.sql.gz

# 2. 압축 해제
gunzip withbuddy.sql.gz

# 3. 새 DB 또는 복구 대상 DB에 반영
mysql -u withbuddy -p withbuddy < withbuddy.sql

# 4. 애플리케이션 연결 확인 및 헬스체크
```

### 7.2 Object Storage 백업

```yaml
Versioning: Enabled (권장)
Replication: Cross-Tenancy backup bucket

Lifecycle:
  - Primary current versions: 서비스 정책에 따름
  - Backup current versions: 장기 보관
  - Non-current versions: 30일 후 정리 (정책 선택)
  
Backup:
  - Critical documents: 업로드 후 비동기 복제
  - DB dump: 일/주/월 주기로 backup bucket 적재
  - Backup bucket: Tenancy B
```

### 7.3 애플리케이션 백업

```yaml
Code:
  - Repository: GitHub
  - Backup: Git commits, tags
  
Configuration:
  - Location: Git repository + 서버 환경파일 분리
  - Secrets: OCI Vault 또는 서버 환경파일(/etc/*.env)
  
Logs:
  - Storage: OCI Logging 또는 Object Storage archive
  - Retention: 90 days
```

---

## 8. 모니터링 & 알림

### 8.1 OCI Monitoring 메트릭

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

Object Storage:
  - Bucket size
  - Object count
  - Backup failure count
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

### A. OCI 서비스 매핑

| 기능 | Oracle Cloud (OCI) |
|------|---------------------|
| 컴퓨팅 | Compute (VM.Standard.A1.Flex) |
| 데이터베이스 | MySQL on Compute (VM.Standard.A1.Flex) |
| 블록 스토리지 | Block Volume |
| 파일 스토리지 | Object Storage |
| 로드밸런서 | Load Balancer |
| 네트워크 | VCN + Local VCN Peering (LPG) |
| 모니터링 | Monitoring / Logging / Alarms |
| 시크릿 | Vault |

### B. 비용 예측 (월간, OCI 기준)

MVP 기준 실제 인스턴스 스펙:

```
AI Server (A1.Flex 4 OCPU / 24GB):        Always Free 초과 가능
Backend (A1.Flex 2 OCPU / 12GB):          Always Free 초과 가능
Database (A1.Flex 2 OCPU / 12GB):         Always Free 초과 가능
Load Balancer:                            Always Free 대상 아님 또는 조건 확인 필요
Object Storage:                           Always Free 범위 내 활용 가능
Data Transfer:                            월 10TB Always Free 범위 활용 가능
                                  ──────────
Total:                                    Always Free 단독 운영은 어려울 수 있음
```

참고용 Always Free 기준:

- AMD 기반 VM 2개: 각 1/8 OCPU, 1GB RAM
- Ampere A1: 월 3,000 OCPU-시간 / 18,000GB-시간 범위
- Block Volume: 총 200GB
- Object Storage: 계정 상태에 따라 20GB 또는 30GB 해석 필요
- Outbound Data Transfer: 월 10TB

현재 문서의 운영 사양은 Always Free 한도를 넘길 수 있으므로, 비용 없이 운영하려면 별도 축소 사양 설계가 필요하다.

### C. 체크리스트

**인프라 구축 순서**:
- [ ] Tenancy/Compartment 구조 확정 (Tenancy A: AI, Tenancy B: Backend/DB/Core)
- [ ] VCN 생성
- [ ] Subnet 구성 (Public, Private-App, Private-DB)
- [ ] Internet Gateway 생성
- [ ] NAT Gateway 생성
- [ ] Service Gateway 생성 (Object Storage 사설 경로 사용 시)
- [ ] 라우팅 테이블 설정
- [ ] NSG/Security List 구성
- [ ] LPG 생성 및 피어링 연결 (Tenancy A ↔ Tenancy B)
- [ ] Compute 인스턴스 생성 (Backend, AI, DB/Core)
- [ ] Block Volume 생성 및 마운트
- [ ] Load Balancer 생성 및 Health Check 연결
- [ ] Object Storage bucket 생성 (Primary/Backup, Versioning/Lifecycle/Replication)
- [ ] IAM Dynamic Group / Policy 또는 API Key 설정
- [ ] DNS / TLS 인증서 설정
- [ ] OCI Monitoring / Alarms / Logging 설정

---

## 변경 이력

- 2026-04-09: 스토리지/백업/모니터링 섹션을 OCI 기준으로 전면 정리하고, Primary/Backup Object Storage 및 Block Volume 배분 구조를 반영. 체크리스트에 LPG/Service Gateway/DNS-TLS 단계를 추가.
- 2026-04-06: 운영 기준을 `Frontend → Backend → AI`, `DB는 Backend만 접근`으로 정리하고 AI→DB/Redis/RabbitMQ 직접 접근 규칙을 제거.
- 2026-04-02: 2.1 VCN 설계 다이어그램을 현재 운영 구조(Tenancy A AI / Tenancy B Backend+DB)로 재정렬하고 미사용 구성 표기를 제거.
- 2026-04-01: Redis(캐시)와 RabbitMQ(메시징) 분리 운영을 반영해 통신 경로, RabbitMQ NSG, 브로커 스펙을 추가.
- 2026-03-27: OCI 확정 반영, 테넌시 분리 구조와 LPG 피어링 추가, 실제 서버 스펙 반영, 보안 규칙 및 부록 업데이트, 다이어그램 이미지 추가.

