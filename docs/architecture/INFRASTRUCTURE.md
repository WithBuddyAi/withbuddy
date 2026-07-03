# WithBuddy 인프라 구조

> 클라우드 인프라 및 네트워크 구성

**최종 업데이트**: 2026-07-03
**버전**: 0.121.6
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
용도: 외부에서 접근 가능한 Backend blue/green

| 리소스 | 포트 | 접근 |
|-------|------|------|
| Backend Blue (Spring Boot) | 8080 | Vercel, 운영자 |
| Backend Green (Spring Boot) | 8080 | 운영 전환 대상 슬롯 |

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
| OCI MySQL DB System 9.7.0 | 3306 | Backend only |

**Private Subnet - Cache/MQ (예: 10.0.4.0/24)**  
용도: Redis / RabbitMQ 전용

| 리소스 | 포트 | 접근 |
|-------|------|------|
| Redis Server | 6379 | Backend, 내부 운영자 |
| RabbitMQ Server | 5672 / 15672 | Backend, 내부 운영자 |

**라우팅 테이블**:
```
Destination         Target
10.0.0.0/16        Local
```

### 2.4 운영 검증 기준 (2026-07-03)

- 현재 운영 경로는 `Frontend -> Backend -> DB/Redis/RabbitMQ` 분리 구조다.
- Backend는 아래 private endpoint만 사용한다.
  - MySQL: `<DB_PRIVATE_IP>:3306`
  - Redis: `<REDIS_PRIVATE_IP>:6379`
  - RabbitMQ: `<RABBITMQ_PRIVATE_IP>:5672`
- 과거 혼합 역할 Compute 서버(MySQL + Redis + RabbitMQ)는 현행 운영 경로에서 제외됐다.

검증 명령:

```bash
nc -vz -w 5 <DB_PRIVATE_IP> 3306
nc -vz -w 5 <REDIS_PRIVATE_IP> 6379
nc -vz -w 5 <RABBITMQ_PRIVATE_IP> 5672
curl -fsS https://<API_DOMAIN>/actuator/health
```

### 2.5 통신 경로 요약

- Frontend → Backend: Public HTTPS → Backend (8080)
- Backend ↔ AI: LPG (VCN-B ↔ VCN-A), 8000
- Backend → MySQL: VCN-B 내부, 3306
- Backend → Redis: VCN-B 내부, 6379
- Backend → RabbitMQ: VCN-B 내부, 5672

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
    Port: 6379
    Destination: 10.0.4.0/24
    Description: To Redis (VCN-B)

  - Type: Custom TCP
    Protocol: TCP
    Port: 5672
    Destination: 10.0.4.0/24
    Description: To RabbitMQ (VCN-B)

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

### 3.5 Redis Security Group (VCN-B)

```yaml
Name: nsg-withbuddy-redis
Description: Redis cache security group

Inbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 6379
    Source: <VCN_B_CIDR>
    Description: From Backend subnet

Outbound Rules:
  - None
```

### 3.6 RabbitMQ Security Group (VCN-B)

```yaml
Name: nsg-withbuddy-rabbitmq
Description: RabbitMQ messaging security group

Inbound Rules:
  - Type: Custom TCP
    Protocol: TCP
    Port: 5672
    Source: <VCN_B_CIDR>
    Description: From Backend subnet
  - Type: Custom TCP
    Protocol: TCP
    Port: 15672
    Source: <ADMIN_FIXED_IP_OR_CIDR>
    Description: Management console

Outbound Rules:
  - None
```

### 3.7 운영 복구 메모 (2026-04-09)

로그인 API 타임아웃 장애(`Backend -> DB 3306 timeout`)를 실제 OCI 설정 기준으로 점검한 결과, 직접 원인은
`<BACKEND_DB_SUBNET_NAME>` 보안 목록(Security List) egress 누락이었다.

- 증상: `<BACKEND_PRIVATE_IP> -> <DB_PRIVATE_IP>:3306` 타임아웃, `/api/v1/auth/login` 응답 지연/타임아웃
- 원인: DB ingress(3306)는 있었지만, 내부망 대상 egress가 `10.2.0.0/16` 위주로만 구성됨
- 복구: 동일 서브넷 내부 통신 허용 egress 규칙 추가

```yaml
Mandatory Egress Rules (same-subnet/shared-subnet 운영 시):
  - Destination: <VCN_B_CIDR>, Protocol: TCP, Port: 3306 (MySQL)
  - Destination: <VCN_B_CIDR>, Protocol: TCP, Port: 6379 (Redis)
  - Destination: <VCN_B_CIDR>, Protocol: TCP, Port: 5672 (RabbitMQ)
```

검증 명령:

```bash
# Backend 서버에서
nc -vz -w 5 <DB_PRIVATE_IP> 3306
curl -X POST https://<API_DOMAIN>/api/v1/auth/login ...
```

---

## 4. 스토리지 구조

### 4.1 Object Storage (OCI)

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

```text
Allow dynamic-group <BACKEND_DYNAMIC_GROUP> to manage objects in compartment <COMPARTMENT_NAME>
Allow dynamic-group <BACKEND_DYNAMIC_GROUP> to read buckets in compartment <COMPARTMENT_NAME>
Allow group <OPS_GROUP> to manage object-family in compartment <COMPARTMENT_NAME>
```

**접근 방식**:
- ✅ Backend: OCI Instance Principal / 승인된 CLI profile 기반 접근
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
Service: OCI MySQL DB System
Version: 9.7.0
Endpoint: Private only
Storage: Managed by OCI DB System profile
Backup:
  Retention: OCI 정책 기준
  Method: DB System backup / restore
Maintenance:
  Window: OCI 관리 창 기준
```

---

## 5. 서버 스펙

오사카 리전 기준 실제 운영 사양:

### 5.1 Backend Server (Tenancy B)
```yaml
Topology: Blue/Green
Instances:
  - Blue: VM.Standard.A1.Flex / 2 OCPU / 12 GB
  - Green: VM.Standard.A1.Flex / 2 OCPU / 12 GB
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

### 5.3 Database Service (Tenancy B)
```yaml
Type: OCI MySQL DB System
Database Version: 9.7.0
Access: Private - DB (VCN-B)
Mode: Read/write
Crash Recovery: Enabled
```

### 5.4 Redis Service (Tenancy B)
```yaml
Type: Oracle Cloud Compute
Shape: VM.Standard.E2.1.Micro
Role: Redis cache
Subnet: Private - Cache/MQ (VCN-B)
```

### 5.5 RabbitMQ Service (Tenancy B)
```yaml
Type: Oracle Cloud Compute
Shape: VM.Standard.E2.1.Micro
Role: RabbitMQ messaging
Subnet: Private - Cache/MQ (VCN-B)
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
# 1. DB System 백업 목록 확인
oci mysql backup list \
  --compartment-id <COMPARTMENT_OCID>

# 2. DB System 정보 확인
oci mysql db-system get \
  --db-system-id <MYSQL_DB_SYSTEM_OCID>

# 3. 복구 작업은 OCI Console 또는 승인된 운영 절차로 수행
# 4. 애플리케이션 연결 정보는 <SPRING_DB_URL> 기준으로 갱신
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
  - Secrets: OCI Vault / HashiCorp Vault
  
Logs:
  - Storage: Grafana Loki / ELK
  - Retention: 90 days
```

---

## 8. 모니터링 & 알림

### 8.1 Grafana / Prometheus 메트릭

```yaml
Backend:
  - CPUUtilization
  - MemoryUtilization
  - /actuator/health
  - HTTP 5xx rate

Database:
  - Connect latency
  - Active connections
  - Backup status

Redis/RabbitMQ:
  - TCP reachability
  - Queue backlog
  - Consumer count
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
  - Grafana Alerting -> Discord
  - Email: ops@withbuddy.com
```

---

## 부록

### A. OCI 서비스 매핑

| 기능 | Oracle Cloud (OCI) |
|------|---------------------|
| 컴퓨팅 | Compute (Backend blue/green A1.Flex, Redis E2.1.Micro, RabbitMQ E2.1.Micro) |
| 데이터베이스 | OCI MySQL DB System 9.7.0 |
| 스토리지 | Object Storage |
| 로드밸런서 | Load Balancer |
| 네트워크 | VCN + Local VCN Peering (LPG) |

### B. 비용 예측 (월간, OCI 기준)

MVP 기준 실제 인스턴스 스펙:

```
AI Server (A1.Flex 4 OCPU / 24GB):        TBD
Backend Blue (A1.Flex 2 OCPU / 12GB):     TBD
Backend Green (A1.Flex 2 OCPU / 12GB):    TBD
Database (OCI MySQL DB System 9.7.0):    TBD
Redis (E2.1.Micro):                       TBD
RabbitMQ (E2.1.Micro):                    TBD
Load Balancer:                            TBD
Object Storage:                           TBD
Data Transfer:                            TBD
                                  ──────────
Total:                                    TBD
```

현재는 OCI 과금 기준에 따라 변동 폭이 커서 추정치를 보류한다.

### C. 체크리스트

**인프라 구축 순서**:
- [ ] VCN 생성
- [ ] Subnet 구성 (Public, Private-App, Private-DB)
- [ ] Internet Gateway 생성
- [ ] NAT Gateway 생성
- [ ] 라우팅 테이블 설정
- [ ] 보안 그룹 생성
- [ ] Load Balancer 생성
- [ ] Compute 인스턴스 생성 (Backend blue/green, AI, Redis, RabbitMQ)
- [ ] OCI MySQL DB System 생성
- [ ] Object Storage 버킷 생성
- [ ] IAM 역할 설정
- [ ] Grafana/Prometheus 알람 설정

---

## 변경 이력

- 2026-07-03: 운영 검증 결과를 반영해 `Backend -> DB/Redis/RabbitMQ private endpoint 분리` 기준을 명시하고, OCI 기준과 맞지 않던 AWS/공용 서버 잔여 서술을 정리했다.
- 2026-07-02: 운영 DB를 OCI Managed MySQL DB System 9.7.0으로 정정하고, Backend blue/green A1.Flex 2 OCPU / 12GB x2, Redis E2.1.Micro, RabbitMQ E2.1.Micro 분리 구조를 반영.
- 2026-04-09: OCI 운영 이슈 복구 내역을 반영하고, shared-subnet 운영 시 필수 egress(3306/6379/5672) 규칙을 명시.
- 2026-04-09: 스토리지/백업/모니터링 섹션을 OCI 기준으로 전면 정리하고, Primary/Backup Object Storage 및 Block Volume 배분 구조를 반영. 체크리스트에 LPG/Service Gateway/DNS-TLS 단계를 추가.
- 2026-04-06: 운영 기준을 `Frontend → Backend → AI`, `DB는 Backend만 접근`으로 정리하고 AI→DB/Redis/RabbitMQ 직접 접근 규칙을 제거.
- 2026-03-27: OCI 확정 반영, 테넌시 분리 구조와 LPG 피어링 추가, 실제 서버 스펙 반영, 보안 규칙 및 부록 업데이트, 다이어그램 이미지 추가.
- 2026-04-01: Redis(캐시)와 RabbitMQ(메시징) 분리 운영을 반영해 통신 경로, RabbitMQ NSG, 브로커 스펙을 추가.
- 2026-04-02: 2.1 VCN 설계 다이어그램을 현재 운영 구조(Tenancy A AI / Tenancy B Backend+DB)로 재정렬하고 미사용 구성 표기를 제거.
