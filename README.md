# WithBuddy (위드버디)

> 신입사원 온보딩을 돕는 AI 비서 서비스

**최종 업데이트**: 2026-05-09
**버전**: 0.61.4

[![GitHub issues](https://img.shields.io/github/issues/WithBuddyAi/withbuddy)](https://github.com/WithBuddyAi/withbuddy/issues)
[![GitHub pull requests](https://img.shields.io/github/issues-pr/WithBuddyAi/withbuddy)](https://github.com/WithBuddyAi/withbuddy/pulls)
[![Copyright](https://img.shields.io/badge/Copyright-WithBuddy_Team-blue.svg)](https://github.com/WithBuddyAi/withbuddy)

---

## 프로젝트 소개

**WithBuddy**는 신입사원의 성공적인 온보딩을 지원하는 AI 기반 통합 비서 서비스입니다.

### 핵심 가치
> "신입사원의 첫 3개월이 회사 생활을 결정합니다"

**신입사원의 고민**
- 궁금한 건 많은데 누구한테 물어봐야 할지 모르겠어요
- 해야 할 일이 뭔지 정확히 모르겠어요  
- 내가 잘하고 있는지 불안해요

**WithBuddy의 솔루션**
- **AI 에이전트**: 사내 문서 기반 Q&A, 주차별 체크리스트
- **스마트 기록**: AI 자동 요약, 주간 회고
- **진행률 관리**: 시각화된 온보딩 로드맵
- **AI 리포트**: 자동 생성 주간 리포트
- **문서함**: 인사/행정 문서 통합 관리

---

## 팀원

| 역할 | 이름 | GitHub |
|------|------|--------|
| 👑 **PM** | 신** | [@pmsooa](https://github.com/pmsooa) |
| 🤖 **AI 팀장** | 김** | [@junsu22](https://github.com/junsu22) |
| 🔧 **Backend** | 김** | [@wldnjsdl745](https://github.com/wldnjsdl745) |
| 🔧 **Backend** | 홍** | [@aerofleet](https://github.com/aerofleet) |
| 🎨 **Frontend** | 장** | [@sumin16](https://github.com/sumin16) |
| 🎨 **Design** | 박** | [@xhkdm1](https://github.com/xhkdm1) |

---

## 기술 스택

| 분야 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.5+, MySQL 8.0, Redis, RabbitMQ, JWT, Flyway |
| **Frontend** | React 18, JavaScript (ES6+), Vite, Tailwind CSS |
| **AI** | Python 3.11, FastAPI, LangChain, LangGraph, ChromaDB, Claude API |
| **배포** | Oracle Cloud (Backend/AI/MySQL/Object Storage), Vercel (Frontend) |
| **CI/CD** | GitHub Actions |

---

## 프로젝트 표준

| 구분 | 디렉토리 | 프로젝트명 | 식별자/패키지 | 기본 포트 |
|------|----------|------------|---------------|-----------|
| Backend | `backend/` | withbuddy | `com.withbuddy` | 8080 |
| Frontend | `frontend/` | withbuddy-frontend | `VITE_*` env 사용 | 5173 |
| AI | `ai/` | withbuddy-ai | `app.main:app` | 8000 |

---

## 빠른 시작

### 필수 요구사항
- Java 21, Node.js 20+, Python 3.11+, MySQL 8.0
- Docker Desktop 4.0+ (선택, AI 서버 컨테이너 실행 시)

### 로컬 실행

**1. Repository 클론**
```bash
git clone git@github.com:WithBuddyAi/withbuddy.git
cd withbuddy
```

**2. MySQL 설정**
```sql
CREATE DATABASE withbuddy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'withbuddy'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON withbuddy.* TO 'withbuddy'@'localhost';
```

**3. Backend 실행**
```bash
cd backend
# IntelliJ: Run > Edit Configurations > Environment Variables
# DB_PASSWORD=your_password
# JWT_SECRET=your-secret-key-min-32-chars
# AI_SERVER_BASE_URL=http://localhost:8000
# REDIS_URL=redis://localhost:6379
# RABBITMQ_URL=amqp://guest:guest@localhost:5672
# STORAGE_API_AUTH_ENABLED=true
# STORAGE_API_KEY_VALUE=<storage_admin_key>

./gradlew bootRun
# http://localhost:8080
```

> Backend는 기동 시 Flyway 마이그레이션(`backend/src/main/resources/db/migration`)을 자동 실행합니다.

**4. Frontend 실행**
```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

**5. AI 서버 실행**

옵션 A. Docker Compose로 AI 서버만 실행
```bash
docker compose up --build ai
# http://localhost:8000/docs
```

> `docker-compose.yml`은 AI 서버 전용 로컬 실행 파일입니다. `ANTHROPIC_API_KEY`, `SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN`, `AI_CORS_ALLOWED_ORIGINS`는 현재 쉘 환경변수 또는 `.env`로 주입할 수 있습니다.

옵션 B. 로컬 Python 환경에서 직접 실행
```bash
cd ai
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# .env 파일 생성
echo "ANTHROPIC_API_KEY=your_key" > .env
echo "CHROMA_PERSIST_DIR=./chroma_db" >> .env

uvicorn app.main:app --reload
# http://localhost:8000/docs
```

>  **상세 가이드**: [docs/guides/SETUP.md](./docs/guides/SETUP.md)

---

## withBuddy 디렉토리 구조

```
withbuddy/
├─ .github/
│  ├─ ISSUE_TEMPLATE/                               # BE/CI  (Issue 템플릿 관리)
│  │  ├─ bug_report.md                              # BE/CI  (New Issue 생성시 제공되는 버그 리포트 관리 템플릿)
│  │  └─ config.yml                                 # BE/CI  (New Issue 생성시 안내되는 기여 가이드 링크) 
│  └─ workflows/               
│     ├─ ci.yml                                     # BE/CI  (변경 영역 빌드/테스트 자동 검증)
│     ├─ ai-deploy.yml                              # AI/CI  (AI 서버 자동 배포 워크플로우)
│     ├─ backend-deploy.yml                         # BE/CI  (Backend 자동 배포 워크플로우)
│     └─ pr-autofill.yml                            # BE/CI  (PR 본문 자동 생성 워크플로우)
│ 
├─ ai/                                              # AI (기능 구현 시작 후 이 폴더에서 관리)
│ 
├─ backend/                                         # BE
│  ├─ src/main/resources/
│  │  ├─ application.yaml                           # 공통 설정
│  │  ├─ application-prod.yml                       # 운영 설정
│  │  ├─ schema.sql                                 # 참고용 초기 스키마/시드
│  │  └─ db/migration/                              # Flyway 마이그레이션
│  │     ├─ V1__create_companies.sql
│  │     ├─ ...
│  │     └─ V10__backfill_seed_rows_idempotent.sql
│  ├─ build.gradle                                  # 백엔드 빌드 설정
│  └─ README.md                                     # 백엔드 개발/배포 가이드
│ 
├─ docs/
│  ├─ architecture/
│  │  ├─ AI_ARCHITECTURE.md                         # AI  (AI 아키텍처)
│  │  ├─ ARCHITECTURE.md                            # BE/CI  (시스템 아키텍처)
│  │  ├─ DEPLOYMENT-ORACLE.md                       # BE/CI  (OCI 배포 가이드)
│  │  ├─ DEPLOYMENT.md                              # BE/CI  (배포 가이드)
│  │  └─ INFRASTRUCTURE.md                          # BE/CI  (인프라 구조)
│  │  ├─ AI_ARCHITECTURE.md                         # AI  (AI 아키텍처)
│  │  ├─ ARCHITECTURE.md                            # BE/CI  (시스템 아키텍처)
│  │  ├─ DEPLOYMENT-ORACLE.md                       # BE/CI  (OCI 배포 가이드)
│  │  ├─ INFRASTRUCTURE.md                          # BE/CI  (인프라 구조)
│  │  ├─ AI_SERVER_GUIDE.md                         # AI/CI  (AI 서버 운영/배포 점검 가이드)
│  │  └─ Redis_RMQ_SSE.md                           # BE     (Redis & RabbitMQ 상세 아키텍처 가이드)
│  │
│  ├─ erd/                                          # BE (MVP 단계에서 진행)
│  ├─ migration/
│  │  └─ MIGRATION.md                               # BE/CI  (Flyway 마이그레이션 가이드)
│  ├─ storage/                                      # BE/CI  (스토리지 API/운영/DDL 문서)
│  │
│  ├─ guides/ 
│  │  ├─ COLLABORATION.md                           # BE/CI  (협업 규칙 📚 필독)
│  │  ├─ CONTRIBUTING.md                            # BE/CI  (기여 가이드 📚 필독)
│  │  ├─ COLLABORATION.md                           # BE/CI  (협업 규칙 📚 필독)
│  │  ├─ CONTRIBUTING.md                            # BE/CI  (기여 가이드 📚 필독)
│  │  ├─ AI-DEPENDENCIES.md                         # BE/AI  (AI 의존성 파일 관리 가이드)
│  │  ├─ CONFLICT-MINIMIZATION.md                   # BE/CI  (충돌 최소화 작업 매뉴얼 📚 필독)
│  │  ├─ CONFLICT-RECOVERY.md                       # BE/CI  (충돌/오염 상태 복구 매뉴얼📚 필독)
│  │  ├─ ENV.md                                     # BE/CI  (환경변수 및 GitHub Secrets 가이드)
│  │  ├─ GITHUB-FLOW-SETUP.md                       # BE/CI  (GitHub flow 설정 체크리스트)
│  │  ├─ GITHUB-SSH.md                              # BE/CI  (GitHub SSH 키 설정 가이드 📚 필독)
│  │  └─ SETUP.md                                   # BE/CI  (개발 환경 설정 가이드 📚 필독)
│  │ 
│  ├─ storage/
│  │  ├─ API_SPEC.md                                # BE/CI  (객체 스토리지 API 명세서)
│  │  ├─ DB_DDL.sql                                 # BE/CI  (DDL)
│  │  ├─ ERD.md                                     # BE/CI  (ERD)
│  │  ├─ OPERATIONS_RUNBOOK.md                      # BE/CI  (객체 스토리지 운영 및 기록일지)
│  │  └─ README.md                                  # BE/CI  (객체 스토리지 리드미)
│  │
│  ├─ API.md                                        # BE  (API 명세서)
│  ├─ MULTI_TENANCY.md                              # BE  (멀티 테넌시 아키텍처)
│  ├─ PLANNED_API.md                                # BE  (Planned API)
│  └─ SECURITY.md                                   # BE/CI  (보안 설계)
│                               
├─ frontend/                                        # FE  (기능 구현 시작 후 이 폴더에서 관리)
├─ .gitignore                                       # BE/FE/AI/CI  (지속 관리)
└─ README.md                                        # PM/BE/CI (MVP 설계 단계 이후 관리)

*** BE - Backend | FE - Frontend | PM - Project Manager | CI - Cloud Infrastructure
```

---

## 문서 바로가기

### 시작하기
- **[개발 환경 설정](./docs/guides/SETUP.md)** - MySQL, 환경변수, 실행 방법
- **[GitHub SSH 키 설정 가이드](./docs/guides/GITHUB-SSH.md)** - GitHub 연동용 SSH 설정
- **[환경변수 관리](./docs/guides/ENV.md)** - 서비스 환경변수 + GitHub Actions `Environment: production` 시크릿 설정

### 협업
- **[협업 규칙](./docs/guides/COLLABORATION.md)** - 브랜치 및 PR 가이드
- **[기여 가이드](./docs/guides/CONTRIBUTING.md)** - 브랜치, 커밋, PR
- **[GitHub flow 설정 체크리스트](./docs/guides/GITHUB-FLOW-SETUP.md)** - Branch Protection, CI, CODEOWNERS, PR 자동 본문 설정
- **[기여 가이드](./docs/guides/CONTRIBUTING.md)** - 브랜치, 커밋, PR, 코드 작성 기준

### 아키텍처
- **[시스템 구조](./docs/architecture/ARCHITECTURE.md)** - 인프라, 서버 구성
- **[AI 서버 운영 가이드](docs/architecture/AI_SERVER_GUIDE.md)** - AI 서버 점검 기준, CI/CD 선행조건
- **[Redis & RabbitMQ 아키텍처](docs/architecture/Redis_RMQ_SSE.md)** - Redis 캐싱, RabbitMQ 메시징 상세 설계 (v2.5)
- **[OCI 배포 가이드](./docs/architecture/DEPLOYMENT-ORACLE.md)** - 서버 배포/Secrets/운영 체크리스트
- **[DB 마이그레이션 가이드](./docs/migration/MIGRATION.md)** - Flyway 버전 관리, 검증 쿼리, 운영 규칙
- **[데이터베이스](./docs/erd/erd.md)** - ERD, 테이블 설계
- **[스토리지 문서 세트](./docs/storage/README.md)** - Storage API, 운영 Runbook, DDL, ERD

### API
- **[API 명세서](docs/PLANNED_API.md)** - 전체 엔드포인트
- **[Swagger UI](http://localhost:8080/swagger-ui.html)** - 로컬 API 문서

> 프로젝트 문서는 `docs/` 디렉토리에서 주제별로 확인할 수 있습니다.

---

## 기여하기

### 버그 발견 시
👉 [Bug Report](https://github.com/WithBuddyAi/withbuddy/issues/new?template=bug_report.md)

### 기능 제안
👉 [Feature Request](https://github.com/WithBuddyAi/withbuddy/issues/new?template=feature_request.md)

### 기여 프로세스
1. **Issue 확인** → 작업 선택 및 할당
2. **브랜치 생성** → `develop`에서 `feature/SCRUM-68-add-feature`
3. **개발 & 커밋** → `feat: Add feature`
4. **Pull Request** → `develop` 대상으로 리뷰 요청
5. **코드 리뷰** → 최소 1 approve
6. **Merge** → Squash and Merge

> [기여 가이드 자세히 보기](docs/guides/CONTRIBUTING.md)

---

## 빌더스 리그 2026

| 단계 | 기간 | 목표 |
|------|------|------|
| **MVP 개발** | 03.17 ~ 05.14 (9주) | 핵심 기능 완성 |
| **고도화** | 05.18 ~ 06.25 (5주) | 서비스 개선, 베타 테스트 |
| **런칭** | 06.29 ~ 07.16 (3주) | 정식 런칭, 엑스포 |

**목표**
- 베타 테스터 100명 확보
- 실제 기업 3곳 파일럿 테스트
- 사용자 만족도 4.5/5.0 이상

---

## 연락처

- **GitHub**: [@WithBuddyAi](https://github.com/WithBuddyAi)
- **Email**: team@withbuddy.ai

---

## 라이센스

**Copyright © 2026 WithBuddy Team. All Rights Reserved.**

본 프로젝트는 교육 및 포트폴리오 목적으로 공개되었습니다. 소스 코드의 모든 권리는 WithBuddy 팀에게 있으며, 다음 조건 하에 제한적으로 사용이 허가됩니다.

### 허용되는 사용

**학습 및 참고 목적**
- 소스 코드 열람 및 학습
- 기술 스택 및 아키텍처 연구
- 개인 학습을 위한 로컬 실행

**포트폴리오 및 경력 증명**
- 이력서 및 포트폴리오에 프로젝트 참여 명시
- 면접 시 코드 설명 및 시연
- 개인 GitHub 프로필에 기여 내역 표시

### 금지되는 사용

**상업적 이용**
- 본 소프트웨어를 이용한 상업적 서비스 운영
- 유료 제품 또는 서비스에 코드 활용
- 수익 창출 목적의 사용

**재배포 및 2차 저작물**
- 소스 코드의 전체 또는 일부 재배포
- 코드 기반 파생 제품 개발 및 판매
- 타 프로젝트에 코드 복사 또는 이식

**무단 변경 및 배포**
- 수정된 버전의 공개 배포
- Fork 후 독립 프로젝트로 전환
- 상표 및 저작권 표시 제거

### 특별 사용 허가

다음의 경우 사전 서면 동의를 통해 사용이 가능합니다:

1. **상업적 사용**: 실제 서비스 운영 또는 수익화
2. **기업 도입**: 조직 내부에서의 활용
3. **교육 기관**: 강의 자료 또는 교육 목적 사용
4. **연구 목적**: 학술 연구 또는 논문 작성

### 문의 및 협업

**라이센스 관련 문의**
- 이메일: team@withbuddy.ai
- 제목: [라이센스 문의] 사용 목적 및 범위

**협업 및 채용 제안**
- WithBuddy 팀은 프로젝트 협업 및 채용 제안을 환영합니다
- 동일 이메일로 연락 주시기 바랍니다

---

### 오픈소스 라이브러리 고지

본 프로젝트는 다음의 오픈소스 소프트웨어를 사용합니다:

**Backend**
- Spring Boot (Apache 2.0)
- Spring Security (Apache 2.0)
- MySQL Connector (GPL 2.0 with FOSS Exception)
- Lombok (MIT)

**Frontend**
- React (MIT)
- Tailwind CSS (MIT)
- Vite (MIT)

**AI/ML**
- FastAPI (MIT)
- LangChain (MIT)
- LangGraph (MIT)
- Anthropic SDK (MIT)
- ChromaDB (Apache 2.0)

각 라이브러리의 라이선스는 해당 프로젝트의 원본 라이선스를 따릅니다.

---

### 면책 조항

본 소프트웨어는 "있는 그대로" 제공되며, 명시적이든 묵시적이든 어떠한 종류의 보증도 하지 않습니다. 저작권자는 소프트웨어의 사용으로 인해 발생하는 어떠한 청구, 손해 또는 기타 책임에 대해서도 책임을 지지 않습니다.

---

<div align="center">

**Made with ❤️ by WithBuddy Team**

[🏠 홈페이지](https://withbuddy.itsdev.kr) • [📖 문서](./docs) • [💬 논의](https://github.com/WithBuddyAi/withbuddy/discussions)

</div>

---

## 변경 이력

- 2026-04-20: `docs/architecture/Redis_RMQ_SSE.md` 추가에 따라 디렉토리 구조 및 문서 링크 반영. Redis 캐싱·RabbitMQ 메시징 상세 아키텍처 가이드 (v2.5).
- 2026-04-14: 백엔드 배포 기준을 Flyway 중심으로 업데이트. baseline 설정(`SPRING_FLYWAY_BASELINE_*`)과 V10 시드 보정 마이그레이션 반영 내용을 문서 링크와 함께 정리.
- 2026-04-11: 스토리지(Object Storage) 반영에 맞춰 기술 스택/배포 항목을 갱신하고, 백엔드 로컬 실행 환경변수 예시에 `REDIS_URL`, `RABBITMQ_URL`, `STORAGE_API_*`를 추가. `docs/storage` 문서 경로를 디렉토리 구조와 문서 링크에 반영.
- 2026-04-02: 브랜치 예시의 Jira 서브태스크 키 표기를 `SCRUM-##` 대문자로 통일.
- 2026-03-30: GitHub Actions `pr-autofill.yml` 워크플로우를 디렉토리 구조/협업 문서 링크에 반영.
