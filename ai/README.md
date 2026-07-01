# 🤖 With Buddy — AI 파트

> 신입사원 온보딩을 돕는 RAG 기반 AI 비서 서비스

**AI 파트 개발**: 생성 AI 9회차 김준수

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🔍 **RAG 질의응답** | 사내 문서·사규를 벡터+BM25 하이브리드 검색하여 정확한 답변 제공 |
| 💬 **멀티턴 대화** | 대화 히스토리 기반 문맥 유지 |
| 🏢 **멀티테넌시** | `company_code` 기준 회사별 문서 격리 (WB0001·WB0002·WB0003 독립 운영) |
| 🚀 **프리보딩** | 입사 전 팀 소개·일정·환영 안내 제공 |
| 🙋 **담당자 추천** | 질문 내용에 맞는 담당자 자동 연결 |
| ❓ **미답변 감지** | 답변 불가 질문 자동 감지 → Slack 실시간 알림 |
| 🧠 **미답변 군집화** | 의미 유사 질문을 자동 클러스터링 → 문서 보강 우선순위 제공 |
| 📊 **주간 요약 리포트** | 미답변 질문 LLM 분석 → 회사별 Slack 자동 발송 |
| 🗂️ **지식 관리** | 리더가 미답변 질문에 직접 답변 → ChromaDB 즉시 반영 |
| 📁 **파일 관리** | 사내 문서 목록 조회·다운로드·자동 인덱싱 |

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| **AI** | Python 3.12, FastAPI, LangChain, ChromaDB, Claude Haiku 4.5 (Anthropic) |
| **임베딩** | Gemini Embedding 2 (`models/gemini-embedding-2`, 3072차원) |
| **검색** | 벡터 유사도 + BM25 하이브리드 (RRF 병합, kiwipiepy 한국어 형태소 분석) |
| **오케스트레이터** | LangGraph — Intent 분류 → RAG / chitchat / sensitive 등 라우팅 |
| **평가** | RAGAS, LangSmith |
| **알림** | slack_sdk, slack_bolt (Socket Mode) — company_code 기반 채널 라우팅 |
| **스케줄러** | APScheduler — 주간 요약 리포트 자동 발송 |

---

## 📁 폴더 구조

```
ai/
├── main.py                  # FastAPI 진입점 + Prometheus 설정
├── requirements.txt
│
├── core/                    # 핵심 모듈 (LLM, 임베딩, ChromaDB, Slack 채널 라우팅)
├── chains/                  # LangChain RAG 체인 (복합 질문 분리, 병렬 검색)
├── agents/                  # 오케스트레이터·프리보딩·커뮤니케이션 에이전트
├── memory/                  # 대화 히스토리·프로필·미답변 저장소
├── routers/                 # API 라우터
├── tasks/                   # 스케줄러 / Slack 알림
├── utils/                   # 프롬프트 템플릿·의미 군집화
├── scripts/                 # 문서 임베딩·평가·E2E 테스트 스크립트
├── docs/                    # 사내 문서 (RAG 소스: 사규·법률·온보딩)
└── data/                    # 런타임 데이터 (클러스터 캐시 등)
```

---

## 🚀 시작하기

```bash
cd ai
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```

`.env` 파일 생성:

```env
ANTHROPIC_API_KEY=your_key
GOOGLE_API_KEY=your_key
SLACK_BOT_TOKEN=your_slack_bot_token
SLACK_APP_TOKEN=your_slack_app_token
SLACK_CHANNEL_INTERNAL=your_slack_channel_id
```

문서 임베딩 후 서버 실행:

```bash
python scripts/ingest.py
uvicorn main:app --reload --host 0.0.0.0 --port 8000
# Swagger UI → http://localhost:8000/docs
```

> ⚠️ `chroma_db/` 폴더는 OneDrive 안에 두지 마세요. 동기화로 인해 벡터 인덱스가 손상될 수 있습니다.

---

## 📡 API 주요 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/internal/ai/answer` | RAG 기반 질의응답 (BE 연동) |
| POST | `/internal/ai/answer/stream` | RAG 질의응답 (SSE 스트리밍) |
| POST | `/chat` | 직접 테스트용 채팅 |
| GET | `/knowledge/unanswered` | 미답변 질문 목록 |
| GET | `/knowledge/no-result/clusters` | 미답변 질문 의미 군집화 결과 |
| POST | `/knowledge/no-result/summary` | 미답변 질문 LLM 요약 + Slack 발송 |
| POST | `/admin/ingest` | 문서 자동 인덱싱 (업로드 후 BE 호출) |
| POST | `/slack/trigger-summary` | 미답변 요약 리포트 수동 발송 |
| GET | `/metrics` | Prometheus 메트릭 |
| GET | `/health` | 헬스체크 |
| GET | `/docs` | Swagger UI |

---

## 📝 수정 이력

| 날짜 | 내용 |
|------|------|
| 2026.06 | Prometheus `/metrics` 엔드포인트 추가 |
| 2026.06 | 미답변 질문 의미 군집화 API 추가 (SCRUM-551) |
| 2026.06 | 문서 버전 관리 기반 자동 인덱싱 최적화 |
| 2026.06 | Slack 미답변 알림 고도화 — 테스트 단계에서 실 운영으로 전환, 미답변 실시간 알림 + 주간 요약 안정화 |
| 2026.05 | BM25 하이브리드 검색 도입 (Before 82.5% → After 87.5%) |
| 2026.05 | Prompt Caching 적용 — 비용 절감 |
| 2026.05 | LLM 호출 2회 → 1회 통합 (Intent + Clarifying 병합) |
| 2026.04 | RAGAS + LangSmith 평가 시스템 도입 |
| 2026.04 | 멀티테넌시 구조 완성 (WB0001·WB0002·WB0003) |
| 2026.03 | RAG 기반 질의응답 초기 구현 |
