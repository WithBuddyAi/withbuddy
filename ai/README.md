# With Buddy — AI 파트

신입사원 온보딩을 돕는 AI 비서 서비스

> **AI 파트 개발**: 생성 AI 9회차 김준수

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| RAG 질의응답 | 사내 문서·사규를 벡터 검색하여 정확한 답변 제공 |
| 멀티턴 대화 | 대화 히스토리 기반 문맥 유지 |
| 담당자 추천 | 질문 내용에 맞는 담당자 자동 연결 |
| 주간 리포트 | 수습사원 대화 분석 후 리더에게 Slack 자동 발송 |
| 지식 관리 | 리더가 미답변 질문에 직접 답변 → ChromaDB 즉시 반영 |
| 프리보딩 | 입사 전 사전 온보딩 정보 제공 |
| 파일 관리 | 사내 문서 목록 조회 및 다운로드 |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| **AI** | Python 3.11, FastAPI, LangChain, ChromaDB, Claude (Anthropic), Streamlit |
| **임베딩** | `jhgan/ko-sroberta-multitask` (한국어 특화), kiwipiepy |
| **Slack** | slack_sdk, slack_bolt (Socket Mode) |
| **스케줄러** | APScheduler |

---

## 폴더 구조

```
ai/
├── main.py                  # FastAPI 진입점
├── streamlit_app.py         # Streamlit 관리자 대시보드
├── start.bat                # FastAPI 서버 실행 배치 파일
├── start_streamlit.bat      # Streamlit 서버 실행 배치 파일
├── requirements.txt
│
├── core/                    # 핵심 모듈 (LLM, 임베딩, ChromaDB, Slack)
├── routers/                 # API 라우터
├── chains/                  # LangChain 체인
├── memory/                  # 로컬 데이터 저장소 (JSON)
├── tasks/                   # 스케줄러 / Slack 자동 알림
├── utils/                   # 프롬프트 템플릿
├── scripts/                 # 문서 임베딩 스크립트
├── docs/                    # 사내 문서 (RAG 소스, 사규 PDF 49개)
├── data/                    # 런타임 데이터 (대화 히스토리, 프로필)

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
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
```

문서 임베딩:

```bash
python scripts/ingest.py
```

서버 실행:

```bash
# 배치 파일 실행 (권장)
start.bat           # FastAPI 채팅 서버
start_streamlit.bat # Streamlit 관리자 대시보드

# 또는 직접 실행
uvicorn main:app --reload --host 0.0.0.0 --port 8000
# http://localhost:8000/docs
```

> **주의**: `chroma_db/` 폴더는 OneDrive 안에 두지 마세요. OneDrive 동기화로 인해 벡터 인덱스가 손상될 수 있습니다.

---

## API 주요 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/chat` | RAG 기반 질의응답 |
| POST | `/report` | 주간 리포트 생성 |
| POST | `/recommend` | 담당자 추천 |
| GET | `/knowledge/unanswered` | 미답변 질문 목록 |
| POST | `/knowledge/answer` | 리더 답변 저장 + 즉시 반영 |
| GET | `/files/list` | 문서 목록 |
| GET | `/docs` | Swagger UI |
