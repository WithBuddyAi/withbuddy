# WithBuddy (위드버디)

> 신입사원 온보딩을 돕는 AI 비서 서비스

**최종 업데이트**: 2026-05-16
**버전**: 0.72.9

[![GitHub issues](https://img.shields.io/github/issues/WithBuddyAi/withbuddy)](https://github.com/WithBuddyAi/withbuddy/issues)
[![GitHub pull requests](https://img.shields.io/github/issues-pr/WithBuddyAi/withbuddy)](https://github.com/WithBuddyAi/withbuddy/pulls)
[![Copyright](https://img.shields.io/badge/Copyright-WithBuddy_Team-blue.svg)](https://github.com/WithBuddyAi/withbuddy)

---
**🔗데모 바로가기**

심사위원분들은 아래 링크와 테스트 계정으로 직접 체험하실 수 있습니다.

| 구분 | 링크 | 
|------|------|
|서비스 접속 | https://withbuddy.itsdev.kr |
|API 명세 (Swagger) | https://api-wb.itsdev.kr/swagger-ui/index.html |

---
**🧪 테스트 계정**

아래 계정 정보로 로그인하면 각 온보딩 단계를 직접 체험할 수 있습니다.
테스트 기준일은 **2026-05-13**이며, 입사 전(D-5), 입사 당일(D+0), 입사 이후(D+5) 상태의 계정을 제공합니다.  
회사 코드, 사번, 이름을 입력하면 로그인됩니다.

**로그인 방법**: 서비스 접속 → 회사코드 / 사번 / 이름 입력 → 로그인
| 계정 | 회사코드 | 사번 | 이름 | 입사시점 | 체험 포인트 |
|------|------|--------|--------|--------|--------|
| ADMIN | WB0001 | 20250001 |김하늘|2025-01-01|USER 계정 생성|
| TEST-1 | WB0001 | 20260008 |김혜린|2026-05-08 (D+5)|Welcome 카드 + Buddy Nudge + Quick Tap|
| TEST-2 | WB0001 | 20260009 |이채윤|2026-05-13 (D+0)|Welcome 카드 + Buddy Nudge + Quick Tap|
| TEST-3 | WB0001 | 20260010 |정재혁|2026-05-18 (D-5)|Welcome 카드 + Buddy Nudge + Quick Tap|

---

## 프로젝트 소개

**WithBuddy**는 신입사원의 반복 질문을 자동화하는 사내 문서 기반 AI 온보딩 에이전트입니다.

### 핵심 가치
> **"신입사원은 묻지 못하고, 사수는 반복 답변에 지쳐 있습니다."**

**해결하는 문제**
신입사원은 궁금한 게 많지만 사수 눈치가 보여 질문을 망설입니다.
사수는 같은 질문에 반복 응대하느라 본업에 집중하지 못합니다.

자체 설문조사에서 신입사원이 입사 초기 가장 큰 어려움으로 꼽은 1위는 '눈치 보여서 묻기 어려움(41.1%)'이었습니다.
사수 187명 설문에서 67.4%가 신입 응대로 업무에 부정적 영향을 받았고,
57.2%는 신입이 문서를 어디서 찾아야 할지 몰라서 사수에게 묻는다고 답했습니다.

문제는 문서의 부재가 아니라 탐색 가능성입니다.

WithBuddy는 회사 문서를 학습해 신입의 자연어 질문에 즉시 답변합니다.
신입은 눈치 보지 않고 첫날부터 질문할 수 있고,
사수는 같은 질문에 반복 응대하는 시간을 돌려받습니다.

양면 고객 구조
| 구분 | 신입사원 | 기업(Admin) |
|------|------|--------|
|역할|실제 사용자|실제 구매자|
|문제|눈치 보여서 못 묻는다|사수가 반복 응대에 시간을 쓴다|
|가치|즉시 답변 + 심리적 안전감|반복 응대 감소 + 운영 효율|

---

**WithBuddy의 솔루션(핵심기능)**

- **RAG Q&A**: 회사 문서 기반 자연어 질문 → 즉시 답변 + 출처 문서 표시
- **Buddy Nudge**: 입사일 기준(D+0, D+3, D+7 등) 먼저 말을 걸어오는 온보딩 카드
- **로그인**: 회사코드 + 사번 + 이름으로 로그인. 별도 회원가입 없이 관리자가 생성한 계정으로 즉시 접속
- **관리자 페이지**: 이름·사번·입사일 입력으로 신입 계정 즉시 생성. 생성 즉시 로그인 가능
- **담당자 연결**: 답변할 수 없는 질문은 관련 담당자 정보(부서, 이름, 연락처) 제공

---

**서비스 성공 지표**
| 구분 | 지표 | 목표 | 실측 |
|------|------|--------|--------|
|북극성|D+7 RAG 답변 수신 경험률 (입사 당일 포함 7일간 기준)|70% 이상|    |
|보조|D+0 첫 인터랙션 발생률|80% 이상|    |
|보조|D+7 재방문률|30% 이상|    |
|보조|Quick Tap 클릭 유저율|40% 이상|    |
|가드레일|IN SCOPE 정답률|80% 이상|98% 이상|
|가드레일|미답변 비율 (no_result/AI전체 답변 수)|20% 이하|    |
|가드레일|OUT OF SCOPE 인식 정확도|70% 이상|100% |   
|가드레일|TTA (최초 로그인 → 첫 RAG 답변)|5분 이내|0.7초|

---

## 팀원

| 역할 | 이름 | GitHub |
|------|------|--------|
| 🖊️ **PM** | 신** | [@pmsooa](https://github.com/pmsooa) |
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
| **AI** | Python 3.11, FastAPI, LangChain, LangGraph, ChromaDB, Claude Haiku 4.5, Gemini Embedding 2 |
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
│  ├─ CODEOWNERS                                  # PR 리뷰 소유자 설정
│  ├─ pull_request_template.md                    # PR 템플릿
│  ├─ ISSUE_TEMPLATE/                               # BE/CI  (Issue 템플릿 관리)
│  │  ├─ bug_report.md                              # BE/CI  (New Issue 생성시 제공되는 버그 리포트 관리 템플릿)
│  │  └─ config.yml                                 # BE/CI  (New Issue 생성시 안내되는 기여 가이드 링크) 
│  └─ workflows/               
│     ├─ ci.yml                                     # BE/CI  (변경 영역 빌드/테스트 자동 검증)
│     ├─ ai-deploy.yml                              # AI/CI  (AI 서버 자동 배포 워크플로우)
│     ├─ backend-deploy.yml                         # BE/CI  (Backend 자동 배포 워크플로우)
│     ├─ notion-sync.yml                            # PM/CI  (Notion 동기화 워크플로우)
│     ├─ pr-autofill.yml                            # BE/CI  (PR 본문 자동 생성 워크플로우)
│     └─ release-tag.yml                            # CI     (릴리즈 태그 워크플로우)
│ 
├─ ai/                                              # AI
│  ├─ app/                                          # FastAPI 앱 구성
│  ├─ agents/                                       # AI 에이전트
│  ├─ chains/                                       # LangChain 체인
│  ├─ core/                                         # 핵심 설정/공통 로직
│  ├─ memory/                                       # 대화 메모리
│  ├─ routers/                                      # API 라우터
│  ├─ tasks/                                        # 백그라운드 작업
│  ├─ utils/                                        # 유틸리티
│  ├─ main.py                                       # AI 서버 진입점
│  ├─ requirements.txt                              # Python 의존성
│  └─ README.md                                     # AI 서버 가이드
│ 
├─ backend/                                         # BE
│  ├─ src/main/java/com/withbuddy/                  # Spring Boot 애플리케이션 코드
│  ├─ src/main/resources/
│  │  ├─ application.yaml                           # 공통 설정
│  │  ├─ application-prod.yml                       # 운영 설정
│  │  ├─ schema.sql                                 # 참고용 초기 스키마/시드
│  │  └─ db/migration/                              # Flyway 마이그레이션
│  │     ├─ V1__create_companies.sql
│  │     ├─ ...
│  │     └─ V15__add_recommended_contacts_json_to_chat_messages.sql
│  ├─ src/test/java/com/withbuddy/                  # 테스트 코드
│  ├─ gradle/wrapper/                               # Gradle Wrapper 설정
│  ├─ build.gradle                                  # 백엔드 빌드 설정
│  ├─ settings.gradle                               # Gradle 프로젝트 설정
│  └─ README.md                                     # 백엔드 개발/배포 가이드
│ 
├─ docs/
│  ├─ README.md                                     # 문서 인덱스
│  ├─ api/
│  │  ├─ API_CURRENT.md                             # BE  (현재 API 명세서)
│  │  ├─ API_PLANNED.md                             # BE  (Planned API)
│  │  ├─ Redis_RMQ_AI_API.md                        # BE/AI (Redis/RMQ/AI 연동 API)
│  │  ├─ STORAGE_API_SPEC.md                        # BE/CI (스토리지 API 명세)
│  │  └─ README.md                                  # API 문서 인덱스
│  ├─ architecture/
│  │  ├─ AI_ARCHITECTURE.md                         # AI  (AI 아키텍처)
│  │  ├─ ARCHITECTURE.md                            # BE/CI  (시스템 아키텍처)
│  │  ├─ INFRASTRUCTURE.md                          # BE/CI  (인프라 구조)
│  │  ├─ MULTI_TENANCY.md                           # BE     (멀티 테넌시 아키텍처)
│  │  ├─ OCI_OBJECT_STORAGE_STRATEGY.md             # BE/CI  (OCI Object Storage 전략)
│  │  ├─ STORAGE_STRATEGY.md                        # BE/CI  (스토리지 전략)
│  │  ├─ SECURITY.md                                # BE/CI  (보안 설계)
│  │  ├─ AI_SERVER_GUIDE.md                         # AI/CI  (AI 서버 운영/배포 점검 가이드)
│  │  ├─ Redis_RMQ_SSE.md                           # BE     (Redis & RabbitMQ 상세 아키텍처 가이드)
│  │  ├─ README.md                                  # 아키텍처 문서 인덱스
│  │  └─ images/                                    # 아키텍처 이미지
│  │
│  ├─ data/                                         # BE (DB/ERD/마이그레이션 문서)
│  │  ├─ ERD.md                                     # BE     (ERD, 테이블 설계)
│  │  ├─ ERD.png                                    # BE     (ERD 이미지)
│  │  ├─ MIGRATION.md                               # BE/CI  (Flyway 마이그레이션 가이드)
│  │  ├─ README.md                                  # 데이터 문서 인덱스
│  │  └─ storage/                                   # 스토리지 DDL/ERD 백업 문서
│  ├─ operations/
│  │  ├─ DEPLOYMENT-ORACLE.md                       # BE/CI  (OCI 배포 가이드)
│  │  ├─ README.md                                  # BE/CI  (운영 문서 인덱스)
│  │  └─ storage/                                   # 스토리지 운영 기록/런북
│  ├─ guides/
│  │  ├─ COLLABORATION.md                           # BE/CI  (협업 규칙 📚 필독)
│  │  ├─ CONTRIBUTING.md                            # BE/CI  (기여 가이드 📚 필독)
│  │  ├─ AI-DEPENDENCIES.md                         # BE/AI  (AI 의존성 파일 관리 가이드)
│  │  ├─ CONFLICT-MINIMIZATION.md                   # BE/CI  (충돌 최소화 작업 매뉴얼 📚 필독)
│  │  ├─ CONFLICT-RECOVERY.md                       # BE/CI  (충돌/오염 상태 복구 매뉴얼📚 필독)
│  │  ├─ ENV.md                                     # BE/CI  (환경변수 및 GitHub Secrets 가이드)
│  │  ├─ GITHUB-FLOW-SETUP.md                       # BE/CI  (GitHub flow 설정 체크리스트)
│  │  ├─ GITHUB-SSH.md                              # BE/CI  (GitHub SSH 키 설정 가이드 📚 필독)
│  │  ├─ SETUP.md                                   # BE/CI  (개발 환경 설정 가이드 📚 필독)
│  │  ├─ WORKFLOW.md                                # BE/CI  (작업 흐름 가이드)
│  │  ├─ README.md                                  # 가이드 문서 인덱스
│  │  └─ images/                                    # 가이드 이미지
│  │
│  ├─ planning/
│  │  └─ PRD_v4.0.md                                # PM     (서비스 기획 문서)
│  │
│  ├─ storage/
│  │  ├─ DB_DDL.sql                                 # BE/CI  (DDL)
│  │  ├─ ERD.md                                     # BE/CI  (ERD)
│  │  ├─ OPERATIONS_RUNBOOK.md                      # BE/CI  (객체 스토리지 운영 및 기록일지)
│  │  ├─ OPS_LOG_2026-04-11_OBJECT_STORAGE.md       # BE/CI  (객체 스토리지 작업 로그)
│  │  └─ README.md                                  # BE/CI  (객체 스토리지 리드미)
│
├─ frontend/                                        # FE
│  ├─ public/                                       # 정적 리소스
│  ├─ src/                                          # React 애플리케이션 코드
│  ├─ package.json                                  # 프론트엔드 의존성/스크립트
│  ├─ vite.config.js                                # Vite 설정
│  ├─ tailwind.config.js                            # Tailwind 설정
│  ├─ vercel.json                                   # Vercel 배포 설정
│  └─ README.md                                     # 프론트엔드 가이드
│
├─ .postman/                                        # Postman 컬렉션/환경 파일
├─ .gitignore                                       # BE/FE/AI/CI  (지속 관리)
├─ devnote.md                                       # 개발 노트
├─ docker-compose.yml                               # 로컬 AI 서버 실행용 Compose 설정
├─ sync_notion.py                                   # Notion 동기화 스크립트
└─ README.md                                        # PM/BE/CI (MVP 설계 단계 이후 관리)

*** BE - Backend | FE - Frontend | PM - Project Manager | CI - Cloud Infrastructure
```

---

## 문서 바로가기

### 기획
- **[PRD v4.0](./docs/planning/PRD_v4.0.md)** - 서비스 기획 전체 명세

### 시작하기
- **[개발 환경 설정](./docs/guides/SETUP.md)** - MySQL, 환경변수, 실행 방법
- **[GitHub SSH 키 설정 가이드](./docs/guides/GITHUB-SSH.md)** - GitHub 연동용 SSH 설정
- **[환경변수 관리](./docs/guides/ENV.md)** - 서비스 환경변수 + GitHub Actions `Environment: production` 시크릿 설정

### 협업
- **[협업 규칙](./docs/guides/COLLABORATION.md)** - 브랜치 및 PR 가이드
- **[GitHub flow 설정 체크리스트](./docs/guides/GITHUB-FLOW-SETUP.md)** - Branch Protection, CI, CODEOWNERS, PR 자동 본문 설정
- **[기여 가이드](./docs/guides/CONTRIBUTING.md)** - 브랜치, 커밋, PR, 코드 작성 기준

### 아키텍처
- **[시스템 구조](./docs/architecture/ARCHITECTURE.md)** - 인프라, 서버 구성
- **[AI 서버 운영 가이드](./docs/architecture/AI_SERVER_GUIDE.md)** - AI 서버 점검 기준, CI/CD 선행조건
- **[Redis & RabbitMQ 아키텍처](./docs/architecture/Redis_RMQ_SSE.md)** - Redis 캐싱, RabbitMQ 메시징 상세 설계 (v2.5)
- **[OCI 배포 가이드](./docs/operations/DEPLOYMENT-ORACLE.md)** - 서버 배포/Secrets/운영 체크리스트
- **[DB 마이그레이션 가이드](./docs/data/MIGRATION.md)** - Flyway 버전 관리, 검증 쿼리, 운영 규칙
- **[데이터베이스](./docs/data/ERD.md)** - ERD, 테이블 설계
- **[스토리지 문서 세트](./docs/storage/README.md)** - Storage API, 운영 Runbook, DDL, ERD

### API
- **[API 명세서](./docs/api/API_CURRENT.md)** - 현재 구현된 전체 엔드포인트
- **[Planned API](./docs/api/API_PLANNED.md)** - 계획 중인 API
- **[Swagger UI](http://localhost:8080/swagger-ui/index.html)** - 로컬 API 문서

> 프로젝트 문서는 `docs/` 디렉토리에서 주제별로 확인할 수 있습니다.

---

## 빌더스 리그 2026

| 단계 | 기간 | 목표 |
|------|------|------|
| **MVP 개발** | 03.17 ~ 05.14 (9주) | 핵심 기능 완성 |
| **고도화** | 05.18 ~ 06.25 (5주) | 서비스 개선, 베타 테스트 |
| **런칭** | 06.29 ~ 07.16 (3주) | 정식 런칭, 엑스포 |

**목표**
- 파일럿 기업 2개사 이상 도입
- 도입 의향 인터뷰 긍정 반응 확인
- D+7 RAG 답변 수신 경험률 70% 이상

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

> [기여 가이드 자세히 보기](./docs/guides/CONTRIBUTING.md)

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

- 2026-05-11: 5/8 SSH 보안 강화, 5/9 Cloudflare Tunnel 배포 전환, 5/10 보안 체크리스트 완비
- 2026-05-06: PRD v4.0 기반으로 README 전면 개편. 데모 접속 링크, 테스트 계정, 핵심 가치 제안, 서비스 성공 지표, 기획 문서 섹션 추가.
- 2026-04-20: `docs/architecture/Redis_RMQ_SSE.md` 추가에 따라 디렉토리 구조 및 문서 링크 반영. Redis 캐싱·RabbitMQ 메시징 상세 아키텍처 가이드 (v2.5).
- 2026-04-14: 백엔드 배포 기준을 Flyway 중심으로 업데이트. baseline 설정(`SPRING_FLYWAY_BASELINE_*`)과 V10 시드 보정 마이그레이션 반영 내용을 문서 링크와 함께 정리.
- 2026-04-11: 스토리지(Object Storage) 반영에 맞춰 기술 스택/배포 항목을 갱신하고, 백엔드 로컬 실행 환경변수 예시에 `REDIS_URL`, `RABBITMQ_URL`, `STORAGE_API_*`를 추가. `docs/storage` 문서 경로를 디렉토리 구조와 문서 링크에 반영.
- 2026-04-02: 브랜치 예시의 Jira 서브태스크 키 표기를 `SCRUM-##` 대문자로 통일.
- 2026-03-30: GitHub Actions `pr-autofill.yml` 워크플로우를 디렉토리 구조/협업 문서 링크에 반영.
