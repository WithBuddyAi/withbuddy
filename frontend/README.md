# 🤝 WithBuddy

신입사원이 회사에 빠르게 적응할 수 있도록 도와주는 온보딩 AI 서비스

<br>

## 📌 목차

- [서비스 소개](#-서비스-소개)
- [주요 기능](#-주요-기능)
- [화면 구성](#-화면-구성)
- [기술 스택](#-기술-스택)
- [프로젝트 구조](#-프로젝트-구조)
- [개발 가이드](#-개발-가이드)
- [팀원 소개](#-팀원-소개)

<br>

## 💡 서비스 소개

| 항목      | 내용                                          |
| --------- | --------------------------------------------- |
| 서비스명  | WithBuddy                                     |
| 대상      | 스타트업 · 중소기업의 신입사원(확장 가능성 ↑) |
| 수습 기간 | 입사 후 3개월                                 |
| MVP 범위  | 인사 · 행정 (+ 사내 문화)                     |
| 개발 상태 | 🚧 MVP 기능 구현 단계                         |

WithBuddy는?
신입사원이 **사내 문서 기반 Q&A**, **단계별 가이드(Nudge)**를 통해  
혼자서도 빠르게 적응할 수 있도록 도와주는 서비스

<br>

## ✨ 주요 기능

### 🔐 로그인

- 회사코드 · 사번 · 이름 입력으로 간편 로그인
- Cloudflare Turnstile 봇 방지 인증

### 🤝 My Buddy

| 기능            | 설명                                                        |
| --------------- | ----------------------------------------------------------- |
| **Q&A 채팅**    | 사내 문서 기반 RAG 질의응답. 궁금한 걸 언제든 물어보세요    |
| **Buddy Nudge** | 입사 단계에 맞춰 필요한 정보와 할 일을 먼저 알려주는 가이드 |

### 🛠️ 관리자 페이지

| 기능               | 설명                                                   |
| ------------------ | ------------------------------------------------------ |
| **대시보드**       | 활용률 · 문서 보강 필요율 · 미시작 신입 수 통계 + AI 분석 |
| **계정 관리**      | 신입 계정 생성 · 조회 · 부서/팀 필터                    |
| **문서 관리**      | 문서 업로드 · 조회 · 삭제                               |

<br>

## 🖥️ 화면 구성

| 화면            | 설명                                          |
| --------------- | --------------------------------------------- |
| 로그인 페이지   | 회사코드 · 사번 · 이름 입력 폼 + Turnstile 인증 |
| 메인 (My Buddy) | Nudge 카드 + Q&A 채팅 영역                     |
| 관리자 페이지   | 대시보드 · 계정 관리 · 문서 관리                |
| 이용 종료 안내  | 수습 기간 종료 후 안내 화면                     |

<br>

## 🛠️ 기술 스택

### Frontend

![React](https://img.shields.io/badge/React-61DAFB?style=flat-square&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square&logo=javascript&logoColor=black)
![TailwindCSS](https://img.shields.io/badge/TailwindCSS-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)
![React Router](https://img.shields.io/badge/React_Router-CA4245?style=flat-square&logo=reactrouter&logoColor=white)
![Axios](https://img.shields.io/badge/Axios-5A29E4?style=flat-square&logo=axios&logoColor=white)
![Vercel](https://img.shields.io/badge/Vercel-000000?style=flat-square&logo=vercel&logoColor=white)

<br>

## 📁 프로젝트 구조

```
src/
├── App.jsx                                    # 라우팅 설정, 전체 앱 진입점
├── api/
│   ├── axiosInstance.js                       # axios 공통 설정 (쿠키 기반 인증, 401 자동 로그아웃)
│   └── handlers.js                            # API 에러 핸들러
├── contexts/
│   └── UserContext.jsx                        # 사용자 정보 전역 상태 관리 (hireDate, dayOffset, role, accountStatus)
├── hooks/
│   ├── useDesktop.js                          # 반응형 브레이크포인트 감지 공용 훅
│   ├── useLoginForm.js                        # 로그인 폼 상태 + 유효성 검사 + API 호출 훅
│   ├── useTurnstile.js                        # Cloudflare Turnstile CAPTCHA 위젯 관리 훅
│   ├── useTypingEffect.js                     # 타이핑 애니메이션 + 서브 카피 순환 훅
│   ├── useChat.js                             # SSE 스트리밍 + 메시지 상태 + 전송/재시도/다운로드 훅
│   ├── useSession.js                          # 세션 모달 + 로그아웃 관리 훅
│   └── useSidebar.js                          # 사이드바 + 달력 날짜 조회 훅
├── utils/
│   └── validators.js                          # 공용 유효성 검사 함수 (정규식 + 에러 메시지)
├── components/
│   ├── Tooltip.jsx                            # 툴팁 공통 컴포넌트
│   ├── Sidebar.jsx                            # 사이드바 (사용자 정보, 메뉴, 대화기록 달력)
│   ├── LogoutModal.jsx                        # 로그아웃 확인 모달
│   ├── ErrorToast.jsx                         # 에러 토스트 메시지
│   ├── MessageList.jsx                        # 채팅 메시지 목록
│   ├── QuickQuestions.jsx                     # 빠른 질문 버튼 목록
│   ├── ChatInput.jsx                          # 채팅 입력창
│   ├── SessionModal.jsx                       # 세션 만료 모달
│   ├── login/
│   │   ├── LoginForm.jsx                      # 로그인 폼 (데스크탑/모바일 통합)
│   │   ├── HeroSection.jsx                    # 로그인 좌측 소개 영역 (타이핑 말풍선)
│   │   └── LoginBackground.jsx                # 로그인 데스크탑 배경 (그라디언트 + 점 패턴)
│   └── admin/
│       ├── AdminSidebar.jsx                   # 관리자 사이드바 (탭 네비게이션 포함)
│       ├── AdminCreateView.jsx                # 신입 계정 생성 화면 (폼 + 유효성 검사 + API 호출)
│       ├── AdminForm.jsx                      # 계정 생성 폼
│       ├── AdminMainView/
│       │   ├── AdminMainView.jsx              # 계정 관리 메인 화면 (상태 관리, API 호출 담당)
│       │   ├── AdminHeader.jsx                # 계정 관리 상단 헤더
│       │   ├── UserMobileList.jsx             # 모바일 계정 목록
│       │   ├── UserMobileCard.jsx             # 모바일 계정 카드
│       │   ├── UserTable.jsx                  # PC / 태블릿 계정 테이블
│       │   ├── UserRow.jsx                    # 계정 테이블 행
│       │   ├── Pagination.jsx                 # 페이지네이션
│       │   ├── LoadingState.jsx               # 로딩 상태 UI
│       │   └── EmptyState.jsx                 # 빈 상태 UI
│       ├── AdminDocumentView/
│       │   ├── AdminDocumentView.jsx          # 문서 관리 메인 화면 (상태 관리, API 호출 담당)
│       │   ├── AdminDocHeader.jsx             # 문서 관리 상단 헤더
│       │   ├── DocTable.jsx                   # PC / 태블릿 문서 테이블
│       │   ├── DocRow.jsx                     # 문서 테이블 행
│       │   ├── DocMobileList.jsx              # 모바일 문서 목록
│       │   ├── DocMobileCard.jsx              # 모바일 문서 카드
│       │   ├── DocUploadZone.jsx              # 파일 드래그앤드롭 업로드 영역
│       │   ├── DocUploadForm.jsx              # 파일 업로드 입력 폼
│       │   ├── DocDeleteModal.jsx             # 문서 삭제 확인 모달
│       │   ├── DocDuplicateModal.jsx          # 중복 문서 안내 모달
│       │   ├── DocToast.jsx                   # 문서 삭제·업로드 결과 토스트
│       │   └── validateFile.js               # 파일 형식·크기 검사 유틸
│       └── AdminDashboardView/
│           ├── AdminDashboardView.jsx         # 대시보드 메인 화면 (API 호출, 데이터 분배)
│           ├── AdminDashboardHeader.jsx       # 대시보드 헤더 (제목 + 설명)
│           ├── AdminDashboardCards.jsx        # 통계 카드 3종 (활용률, 보강 필요율, 미시작 수)
│           └── AdminDashboardQuestions.jsx    # 미답변 질문 TOP5 + AI 분석
└── pages/
    ├── Login.jsx                              # 로그인 페이지 (훅 + 컴포넌트 조합)
    ├── MyBuddy.jsx                            # 메인 페이지 (훅 + 컴포넌트 조합)
    ├── Inactive.jsx                           # 이용 기간 종료 안내 페이지
    └── Admin.jsx                              # 관리자 페이지 (뷰 전환 · 레이아웃 담당)
```

> 💡 **hooks/ 폴더란?**  
> 여러 컴포넌트에서 재사용 가능한 로직을 Custom Hook으로 분리한 폴더.  
> 로그인: `useLoginForm`(폼 상태·검증·API), `useTurnstile`(CAPTCHA), `useTypingEffect`(타이핑 애니메이션)  
> 마이버디: `useChat`(SSE 스트리밍·메시지·전송·재시도), `useSession`(세션 모달·로그아웃), `useSidebar`(사이드바·달력)  
> 공용: `useDesktop`(반응형 브레이크포인트 감지)

> 💡 **utils/ 폴더란?**  
> React에 의존하지 않는 순수 유틸리티 함수 모음.  
> `validators.js`는 회사코드·사원번호·이름의 정규식 검증과 에러 메시지를 한 곳에서 관리하여  
> Login과 AdminCreateView 등 여러 화면에서 중복 없이 공유함

> 💡 **components/login/ 폴더란?**  
> 로그인 페이지 관련 UI 컴포넌트를 모아둔 폴더.  
> `LoginForm`은 데스크탑/모바일 통합 폼, `HeroSection`은 좌측 소개 영역,  
> `LoginBackground`는 데스크탑 배경 장식을 담당함

> 💡 **axiosInstance란?**  
> 모든 API 요청에 쿠키를 자동으로 첨부하고,  
> 토큰 만료 등 401 에러 발생 시 자동으로 로그아웃 처리하는 axios 공통 설정 파일

> 💡 **UserContext란?**  
> 로그인한 사용자의 hireDate, dayOffset, role, accountStatus 등을 전역으로 관리하여  
> 여러 컴포넌트에서 공통으로 사용할 수 있도록 하는 Context API 파일

> 💡 **admin/ 폴더란?**  
> 관리자 페이지 관련 컴포넌트를 모아둔 폴더.  
> `AdminCreateView`는 계정 생성에 필요한 state, 유효성 검사, API 호출을 직접 관리함  
> `AdminMainView/`는 계정 관리 화면을 담당하며, 하위 컴포넌트(UserTable, UserRow, UserMobileCard)로 역할을 분리함  
> `AdminDashboardView/`는 대시보드 화면을 담당하며, 집계 API 연동 및 통계 카드·질문 랭킹·AI 분석을 렌더링함

<br>

## 🚀 개발 가이드

### 시작하기

```bash
# 1. 레포지토리 클론
git clone https://github.com/WithBuddyAi/withbuddy.git
cd withbuddy/frontend

# 2. 패키지 설치
npm install

# 3. 개발 서버 실행
npm run dev
# → http://localhost:5173 에서 확인
```

### 환경변수 설정

로컬 개발 시 프로젝트 루트에 `.env.local` 파일을 생성하고 아래 값을 설정합니다.

```bash
# Cloudflare Turnstile (로컬 테스트용 dummy key)
VITE_TURNSTILE_SITE_KEY=1x00000000000000000000AA
```

> 💡 운영 환경(Vercel)에는 실제 Turnstile site key가 별도로 등록되어 있습니다.  
> 로컬에서는 Cloudflare가 제공하는 dummy key를 사용하면 항상 인증 성공 처리됩니다.

### 브랜치 전략

```
main                          # 최종 배포용 (건드리지 말기)
└── develop                   # 개발 통합 브랜치
     ├── feature/SCRUM-68-login      # 로그인 기능 개발
     └── feature/SCRUM-72-my-buddy   # My Buddy 화면 개발
```

- 새 기능을 만들 땐 항상 `develop` 브랜치에서 `feature/SCRUM-##-기능명` 브랜치를 만들기
- 기능 완성 후 `develop`으로 Pull Request(PR)를 올리기
- `main`은 최종 배포할 때만 `develop`에서 합치기

### 커밋 컨벤션

커밋 메시지는 **어떤 작업을 했는지** 한눈에 알 수 있게 작성

| 타입       | 설명                     | 예시                                      |
| ---------- | ------------------------ | ----------------------------------------- |
| `feat`     | 새로운 기능 추가         | `feat: 로그인 폼 유효성 검사 추가`        |
| `fix`      | 버그 수정                | `fix: 로그인 버튼 중복 클릭 오류 수정`    |
| `style`    | UI · 스타일 변경         | `style: 버튼 색상 및 여백 조정`           |
| `refactor` | 기능 변화 없이 코드 정리 | `refactor: AdminCreateView 컴포넌트 분리` |
| `docs`     | 문서 수정                | `docs: README 프로젝트 구조 업데이트`     |
| `chore`    | 설정 · 패키지 변경       | `chore: Tailwind CSS 설정 추가`           |

```bash
# ✅ 좋은 예
git commit -m "feat: 사번 입력 유효성 검사 추가"
git commit -m "fix: 채팅 스크롤 자동 이동 오류 수정"

# ❌ 나쁜 예
git commit -m "수정"
git commit -m "ㅇㅇ"
```

### 코드 스타일

- 컴포넌트 파일명: **PascalCase** (`MyBuddy.jsx`, `LoginForm.jsx`)
- 훅 파일명: **camelCase + use 접두사** (`useDesktop.js`, `useLoginForm.js`)
- 유틸 파일명: **camelCase** (`validators.js`, `validateFile.js`)
- 변수 · 함수명: **camelCase** (`userName`, `handleSubmit`)
- 스타일링: **Tailwind CSS** 클래스 사용 (별도 CSS 파일 최소화)
- 하나의 파일이 너무 길어지면 컴포넌트 또는 커스텀 훅으로 분리
- 관련 컴포넌트는 폴더로 묶어서 관리 (예: `components/admin/`, `components/login/`)

<br>

## 변경 이력

- 2026-04-02: 브랜치 전략 예시와 네이밍 규칙의 Jira 키 표기를 `SCRUM-##` 대문자로 통일.
- 2026-04-22: 프로젝트 구조에 `api/axiosInstance.js` 추가.
- 2026-04-27: `contexts/UserContext.jsx` 추가 (hireDate·dayOffset 전역 상태 관리), dayOffset 계산 로직 개선, SSoT 인증 상태 통합.
- 2026-04-28: `ProtectedRoute.jsx`, `Layout.jsx` 제거, `components/` 폴더에 Sidebar, LogoutModal, ErrorToast, MessageList, QuickQuestions, ChatInput 컴포넌트 분리 추가.
- 2026-05-11: 서비스명 WithBuddy로 수정, 관리자 페이지(신입 계정 생성) 추가, `components/admin/` 폴더 구조 반영 (AdminSidebar, AdminMainView, AdminCreateView 분리).
- 2026-05-28: `AdminMainView`를 폴더 구조로 리팩토링하고 관리자 메인 화면 컴포넌트를 역할별로 분리.
  (`AdminHeader`, `UserTable`, `UserRow`, `UserMobileList`, `UserMobileCard`, `Pagination`, `LoadingState`, `EmptyState`)
- 2026-06-08: `AdminDocumentView` 폴더 추가 (문서 목록 조회, 삭제 확인 모달, 토스트 포함), 관리자 사이드바 탭 구조 추가 (대시보드·문서 관리·미답변 질문), `Inactive.jsx` 페이지 추가.
- 2026-06-10: `AdminDocumentView` 문서 업로드 기능 추가 (드래그앤드롭 업로드존, 입력 폼, 파일 검사 유틸), 모바일 문서 목록 추가 (`DocMobileList`, `DocMobileCard`), 업로드·삭제 토스트 통합.
- 2026-06-17: `DocDuplicateModal.jsx` 추가 (POLICY/GUIDE 중복 업로드 차단 모달).
- 2026-06-19: 로그인 페이지에 Cloudflare Turnstile 봇 방지 통합. site key 환경변수 분리 (`VITE_TURNSTILE_SITE_KEY`), 429 레이트리밋 NaN 방어 코드 추가 (`Number.isFinite`), Turnstile 토큰을 `isFormValid`에 반영, 위젯 테마·사이즈 설정 (`theme: "light"`, `size: "flexible"`). 로그인 폼 별표 위치 `<sup>` 적용, 챗 목업 시간 동적 표시 (`date-fns` format).
- 2026-06-22: `AdminDashboardView` 폴더 추가 (대시보드 통계 카드 3종, 미답변 질문 TOP5 막대그래프, AI 분석 섹션). 집계 API 연동 (`GET /api/v1/admin/metrics/dashboard`), 카드별 조건부 dot 색상 처리, 로딩·에러·빈 상태 처리, 다시 시도 버튼 추가.
- 2026-06-24: Login.jsx 컴포넌트 모듈 분리. `hooks/` 폴더 추가 (useDesktop, useLoginForm, useTurnstile, useTypingEffect), `utils/validators.js` 공용 유효성 검사 유틸 추출, `components/login/` 폴더 추가 (LoginForm, HeroSection, LoginBackground). Login.jsx 823줄 → 111줄로 축소.
- 2026-06-25: MyBuddy.jsx 컴포넌트 모듈 분리. `hooks/` 폴더에 useChat, useSession, useSidebar 추가. SSE 스트리밍·메시지·전송/재시도 로직을 useChat으로, 세션 모달·로그아웃을 useSession으로, 사이드바·달력 조회를 useSidebar로 분리. botClass·navItems 컴포넌트 외부 상수로 이동. handleDownload blob → window.open 변경 (CORS 해결). 달력 날짜 선택 시 맨 위 스크롤 기능 추가. 로그인 에러 메시지 3단계 분리 (500/서버 연결 불가/인터넷 끊김). MyBuddy.jsx 663줄 → 227줄로 축소.
