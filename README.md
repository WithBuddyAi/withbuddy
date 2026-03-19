# WithBuddy (위드버디)

> 신입사원 온보딩을 돕는 AI 비서 서비스

[![GitHub issues](https://img.shields.io/github/issues/WithBuddyAi/withbuddy)](https://github.com/WithBuddyAi/withbuddy/issues)
[![GitHub pull requests](https://img.shields.io/github/issues-pr/WithBuddyAi/withbuddy)](https://github.com/WithBuddyAi/withbuddy/pulls)
[![Copyright](https://img.shields.io/badge/Copyright-WithBuddy_Team-blue.svg)](https://github.com/WithBuddyAi/withbuddy)

---

## 📖 프로젝트 소개

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

## 👥 팀원

| 역할 | 이름 | GitHub |
|------|------|--------|
| 👑 **PM** | 신** | [@pmsooa](https://github.com/pmsooa) |
| 🤖 **AI 팀장** | 김** | [@junsu22](https://github.com/junsu22) |
| 🔧 **Backend** | 김** | [@wldnjsdl745](https://github.com/wldnjsdl745) |
| 🔧 **Backend** | 홍** | [@aerofleet](https://github.com/aerofleet) |
| 🎨 **Frontend** | 장** | [@sumin16](https://github.com/sumin16) |
| 🎨 **Design** | 박** | [@xhkdm1](https://github.com/xhkdm1) |

---

## 🛠 기술 스택

| 분야 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.5.11, MySQL 8.0, JWT |
| **Frontend** | React 18, TypeScript, Vite, Tailwind CSS |
| **AI** | Python 3.11, FastAPI, LangChain, Pinecone, OpenAI GPT-4o |
| **배포** | Oracle Cloud (Backend/AI/MySQL), Cloudflare Pages (Frontend) |
| **CI/CD** | GitHub Actions |

---

## 🚀 빠른 시작

### 필수 요구사항
- Java 21, Node.js 20+, Python 3.11+, MySQL 8.0

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
# AI_API_URL=http://localhost:8000

./gradlew bootRun
# http://localhost:8080
```

**4. Frontend 실행**
```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

**5. AI 서버 실행**
```bash
cd ai
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# .env 파일 생성
echo "OPENAI_API_KEY=your_key" > .env
echo "PINECONE_API_KEY=your_key" >> .env

uvicorn app.main:app --reload
# http://localhost:8000/docs
```

> 📚 **상세 가이드**: [docs/guides/SETUP.md](./docs/guides/SETUP.md)

---

## 📂 프로젝트 구조

```
withbuddy/
├── backend/          # Spring Boot (Java 21)
├── frontend/         # React + TypeScript
├── ai/               # FastAPI (Python 3.11)
├── docs/             # 📚 모든 문서는 여기에!
│   ├── guides/       # 개발 규칙, 기여 가이드, 환경 변수, GitHub SSH키 설정, 개발 환경 설정 가이드
│   ├── erd           # ERD
│   └── architecture/ # AI 아키텍처, 시스템 아키텍쳐, 배포 가이드, 인프라 구조
└── .github/workflows/ # CI/CD
```

---

## 📚 문서 바로가기

### 시작하기
- **[개발 환경 설정](./docs/guides/SETUP.md)** - MySQL, 환경변수, 실행 방법
- **[배포 가이드 (Oracle Cloud)](./docs/guides/DEPLOYMENT-ORACLE.md)** - Oracle Cloud + Cloudflare 배포
- **[환경변수 관리](./docs/guides/ENV.md)** - application.yml, .env 설정

### 아키텍처
- **[시스템 구조](./docs/architecture/ARCHITECTURE.md)** - 인프라, 서버 구성
- **[데이터베이스](./docs/database/SCHEMA.md)** - ERD, 테이블 설계

### API
<<<<<<< HEAD
- **[API 명세서](docs/API.md)** - 전체 엔드포인트
=======
- **[API 명세서](docs/PLANNED_API.md)** - 전체 엔드포인트
>>>>>>> e8f1093 ([DOCS] Align collaboration guides with Git Flow (#4))
- **[Swagger UI](http://localhost:8080/swagger-ui.html)** - 로컬 API 문서

### 협업
- **[협업 규칙](./docs/guides/COLLABORATION.md)** - 브랜치 및 PR 가이드
- **[기여 가이드](docs/guides/CONTRIBUTING.md)** - 브랜치, 커밋, PR
<<<<<<< HEAD
=======
- **[Git Flow 설정 체크리스트](./docs/guides/GIT-FLOW-SETUP.md)** - Branch Protection, CI, CODEOWNERS
>>>>>>> e8f1093 ([DOCS] Align collaboration guides with Git Flow (#4))
- **[코딩 컨벤션](./docs/conventions/CODING.md)** - Java, TS, Python 규칙

---

> 📊 [전체 로드맵 보기](./docs/ROADMAP.md)

---

## 🤝 기여하기

### 버그 발견 시
👉 [Bug Report](https://github.com/WithBuddyAi/withbuddy/issues/new?template=bug_report.md)

### 기능 제안
👉 [Feature Request](https://github.com/WithBuddyAi/withbuddy/issues/new?template=feature_request.md)

### 기여 프로세스
1. **Issue 확인** → 작업 선택 및 할당
2. **브랜치 생성** → `develop`에서 `feature/123-add-feature`
3. **개발 & 커밋** → `feat: Add feature`
4. **Pull Request** → `develop` 대상으로 리뷰 요청
5. **코드 리뷰** → 최소 1 approve
6. **Merge** → Squash and Merge

> 📖 [기여 가이드 자세히 보기](docs/guides/CONTRIBUTING.md)

---

## 🏆 빌더스 리그 2026

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

## 📞 연락처

- **GitHub**: [@WithBuddyAi](https://github.com/WithBuddyAi)
- **Email**: team@withbuddy.ai
- **Slack**: [WithBuddy Workspace](https://withbuddy.slack.com)

---

## 📄 라이센스

**Copyright © 2026 WithBuddy Team. All Rights Reserved.**

본 프로젝트는 교육 및 포트폴리오 목적으로 공개되었습니다. 소스 코드의 모든 권리는 WithBuddy 팀에게 있으며, 다음 조건 하에 제한적으로 사용이 허가됩니다.

### ✅ 허용되는 사용

**학습 및 참고 목적**
- 소스 코드 열람 및 학습
- 기술 스택 및 아키텍처 연구
- 개인 학습을 위한 로컬 실행

**포트폴리오 및 경력 증명**
- 이력서 및 포트폴리오에 프로젝트 참여 명시
- 면접 시 코드 설명 및 시연
- 개인 GitHub 프로필에 기여 내역 표시

### ❌ 금지되는 사용

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

### 📋 특별 사용 허가

다음의 경우 사전 서면 동의를 통해 사용이 가능합니다:

1. **상업적 사용**: 실제 서비스 운영 또는 수익화
2. **기업 도입**: 조직 내부에서의 활용
3. **교육 기관**: 강의 자료 또는 교육 목적 사용
4. **연구 목적**: 학술 연구 또는 논문 작성

### 📧 문의 및 협업

**라이센스 관련 문의**
- 이메일: team@withbuddy.ai
- 제목: [라이센스 문의] 사용 목적 및 범위

**협업 및 채용 제안**
- WithBuddy 팀은 프로젝트 협업 및 채용 제안을 환영합니다
- 동일 이메일로 연락 주시기 바랍니다

---

### 📚 오픈소스 라이브러리 고지

본 프로젝트는 다음의 오픈소스 소프트웨어를 사용합니다:

**Backend**
- Spring Boot (Apache 2.0)
- Spring Security (Apache 2.0)
- MySQL Connector (GPL 2.0 with FOSS Exception)
- Lombok (MIT)

**Frontend**
- React (MIT)
- TypeScript (Apache 2.0)
- Tailwind CSS (MIT)
- Vite (MIT)

**AI/ML**
- FastAPI (MIT)
- LangChain (MIT)
- OpenAI Python SDK (MIT)
- Pinecone Client (Apache 2.0)

각 라이브러리의 라이선스는 해당 프로젝트의 원본 라이선스를 따릅니다.

---

### ⚖️ 면책 조항

본 소프트웨어는 "있는 그대로" 제공되며, 명시적이든 묵시적이든 어떠한 종류의 보증도 하지 않습니다. 저작권자는 소프트웨어의 사용으로 인해 발생하는 어떠한 청구, 손해 또는 기타 책임에 대해서도 책임을 지지 않습니다.

---

<div align="center">

**Made with ❤️ by WithBuddy Team**

[🏠 홈페이지](https://withbuddy.com) • [📖 문서](./docs) • [💬 논의](https://github.com/WithBuddyAi/withbuddy/discussions)

</div>
