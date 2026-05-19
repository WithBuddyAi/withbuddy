# With Buddy — AI 파트

신입사원 온보딩을 돕는 AI 비서 서비스

> **AI 파트 개발**: 생성 AI 9회차 김준수

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| RAG 질의응답 | 사내 문서·사규를 벡터+BM25 하이브리드 검색하여 정확한 답변 제공 |
| 멀티턴 대화 | 대화 히스토리 기반 문맥 유지 |
| 멀티테넌시 | company_code 기준 회사별 문서 격리 (WB0001·WB0002 독립 운영) |
| 담당자 추천 | 질문 내용에 맞는 담당자 자동 연결 |
| 주간 리포트 | 수습사원 대화 분석 후 리더에게 Slack 자동 발송 |
| 지식 관리 | 리더가 미답변 질문에 직접 답변 → ChromaDB 즉시 반영 |
| 파일 관리 | 사내 문서 목록 조회 및 다운로드 |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| **AI** | Python 3.11, FastAPI, LangChain, ChromaDB, Claude Haiku 4.5 (Anthropic) |
| **임베딩** | Gemini Embedding 2 (`models/gemini-embedding-2`) |
| **검색** | 벡터 유사도 + BM25 하이브리드 (RRF 병합, kiwipiepy 한국어 형태소 분석) |
| **평가** | RAGAS, LangSmith |
| **Slack** | slack_sdk, slack_bolt (Socket Mode) |
| **스케줄러** | APScheduler |

---

## 프로젝트 표준

| 항목 | 값 |
|------|-----|
| 디렉토리 | `ai/` |
| 프로젝트명 | `withbuddy-ai` |
| 엔트리포인트 | `main:app` |
| 기본 포트 | 8000 |

---

## 폴더 구조

```
ai/
├── main.py                  # FastAPI 진입점
├── requirements.txt
│
├── core/                    # 핵심 모듈 (LLM, 임베딩, ChromaDB, Slack)
├── chains/                  # LangChain RAG 체인 (복합 질문 분리, 병렬 검색)
├── agents/                  # 오케스트레이터·프리보딩·커뮤니케이션 에이전트
├── memory/                  # 대화 히스토리·프로필·미답변 저장소 (JSON)
├── routers/                 # API 라우터
├── tasks/                   # 스케줄러 / Slack 자동 알림
├── utils/                   # 프롬프트 템플릿
├── scripts/                 # 문서 임베딩·평가 스크립트 (ingest, evaluate, test)
├── docs/                    # 사내 문서 (RAG 소스: 사규·법률·온보딩)
├── data/                    # 런타임 데이터
```

---

## 시작하기

### AI 서버 실행

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
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
```

문서 임베딩:

```bash
python scripts/ingest.py
```

서버 실행:

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8000
# http://localhost:8000/docs
```

> **주의**: `chroma_db/` 폴더는 OneDrive 안에 두지 마세요. 동기화로 인해 벡터 인덱스가 손상될 수 있습니다.

---

## API 주요 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/internal/ai/answer` | RAG 기반 질의응답 (BE 연동) |
| POST | `/internal/ai/answer/stream` | RAG 질의응답 (SSE 스트리밍) |
| POST | `/chat` | 직접 테스트용 채팅 |
| POST | `/report` | 주간 리포트 생성 |
| GET | `/knowledge/unanswered` | 미답변 질문 목록 |
| POST | `/knowledge/answer` | 리더 답변 저장 + 즉시 반영 |
| GET | `/files/list` | 문서 목록 |
| GET | `/docs` | Swagger UI |
