# 🤝 With Buddy

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
| 서비스명  | With Buddy                                    |
| 대상      | 스타트업 · 중소기업의 신입사원(확장 가능성 ↑) |
| 수습 기간 | 입사 후 3개월                                 |
| MVP 범위  | 인사 · 행정 (+ 사내 문화)                     |
| 개발 상태 | 🚧 기획 & 설계 단계                           |

With Buddy는?
신입사원이 **사내 문서 기반 Q&A**, **단계별 가이드(Nudge)**를 통해  
혼자서도 빠르게 적응할 수 있도록 도와주는 서비스

<br>

## ✨ 주요 기능

### 🔐 로그인

- 회사명 · 이름 · 사번 입력으로 간편 로그인
- 추후 고도화: 비밀번호, IP 인증, 회사 코드 방식으로 개선 예정

### 🤝 My Buddy

| 기능            | 설명                                                        |
| --------------- | ----------------------------------------------------------- |
| **Q&A 채팅**    | 사내 문서 기반 RAG 질의응답. 궁금한 걸 언제든 물어보세요    |
| **Buddy Nudge** | 입사 단계에 맞춰 필요한 정보와 할 일을 먼저 알려주는 가이드 |

<br>

## 🖥️ 화면 구성

| 화면            | 설명                         |
| --------------- | ---------------------------- |
| 로그인 페이지   | 회사명 · 이름 · 사번 입력 폼 |
| 메인 (My Buddy) | Nudge 카드 + Q&A 채팅 영역   |

> 📐 화면 설계는 현재 기획 · 디자인 단계로 UI 확정 후 스크린샷을 추가할 예정

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
├── App.jsx                  # 라우팅 설정, 전체 앱 진입점
├── ProtectedRoute.jsx       # 로그인 여부 확인 후 페이지 접근 제어
├── api/
│   └── axiosInstance.js     # axios 공통 설정 (토큰 자동 첨부, 401 자동 로그아웃)
├── contexts/
│   └── Context.jsx          # 전역 상태 관리 (Context API)
├── components/
│   └── Layout.jsx           # 공통 레이아웃 (헤더 등 반복 UI)
└── pages/
    ├── Login.jsx            # 로그인 페이지
    └── MyBuddy.jsx          # 메인 페이지 (Q&A 채팅 + Buddy Nudge)
```

> 💡 **ProtectedRoute란?**  
> 로그인하지 않은 사용자가 메인 페이지 URL을 직접 입력해서 들어오려고 하면,  
> 자동으로 로그인 페이지로 돌려보내는 역할

> 💡 **axiosInstance란?**  
> 모든 API 요청에 토큰을 자동으로 첨부하고,  
> 토큰 만료 등 401 에러 발생 시 자동으로 로그아웃 처리하는 axios 공통 설정 파일

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

| 타입       | 설명                     | 예시                                   |
| ---------- | ------------------------ | -------------------------------------- |
| `feat`     | 새로운 기능 추가         | `feat: 로그인 폼 유효성 검사 추가`     |
| `fix`      | 버그 수정                | `fix: 로그인 버튼 중복 클릭 오류 수정` |
| `style`    | UI · 스타일 변경         | `style: 버튼 색상 및 여백 조정`        |
| `refactor` | 기능 변화 없이 코드 정리 | `refactor: MyBuddy 컴포넌트 분리`      |
| `docs`     | 문서 수정                | `docs: README 화면 구성 업데이트`      |
| `chore`    | 설정 · 패키지 변경       | `chore: Tailwind CSS 설정 추가`        |

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
- 변수 · 함수명: **camelCase** (`userName`, `handleSubmit`)
- 스타일링: **Tailwind CSS** 클래스 사용 (별도 CSS 파일 최소화)
- 하나의 파일이 너무 길어지면 컴포넌트로 분리

## 변경 이력

- 2026-04-02: 브랜치 전략 예시와 네이밍 규칙의 Jira 키 표기를 `SCRUM-##` 대문자로 통일.
- 2026-04-22: 프로젝트 구조에 `api/axiosInstance.js` 추가.