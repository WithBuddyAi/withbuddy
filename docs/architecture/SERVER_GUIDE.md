# With Buddy AI 서버 가이드

> 2026-03-24 기준 정리

---

## 1. 서버 구조

```
노트북 (AI 서버)
├── FastAPI          → API 처리
├── LangGraph        → 오케스트레이터 (질문 의도 분류)
└── ChromaDB         → 벡터 DB (파일로 저장, 별도 서버 불필요)
```

- AI 서버 하나에 전부 올라가 있음
- ChromaDB는 독립 서버가 아니라 파일 저장 방식 (`~/withbuddy_chroma_db`)
- 스프링부트 → MySQL (일반 데이터), FastAPI → ChromaDB (AI 벡터 검색용) → 서로 영향 없음

**최소 사양**: RAM 4GB, CPU 2코어 (GPU 불필요)

---

## 2. 엔드포인트 정리

| 엔드포인트 | 용도 | 누가 호출 |
|---|---|---|
| `POST /internal/ai/answer` | 백엔드 ↔ AI 연동 | 백엔드만 |
| `POST /chat/stream` | SSE 스트리밍 채팅 | Streamlit 테스트용 |
| `POST /chat` | 일반 채팅 | Streamlit 테스트용 |

> `/chat`, `/chat/stream`은 Streamlit 내부 테스트용. 실제 서비스에서 프론트/백엔드가 직접 호출하는 AI 엔드포인트는 `/internal/ai/answer` 하나.

---

## 3. 백엔드 연동 흐름

```
사용자 질문
  ↓
프론트 → POST /api/v1/chat/messages (백엔드)
  ↓
백엔드 → POST /internal/ai/answer (AI 서버)
  ↓
AI 서버 → answer + sourceDocumentId 반환
  ↓
백엔드 → DB 저장 후 프론트에 응답
```

### `/internal/ai/answer` 요청 형식

```json
{
  "user": {
    "id": 1,
    "companyId": 1,
    "name": "김지원",
    "hireDate": "2026-03-01"
  },
  "question": "복지카드는 어떻게 신청하나요?",
  "messageHistory": [
    {
      "senderType": "USER",
      "messageType": "QUESTION",
      "content": "출근 기록은 어디서 확인하나요?"
    },
    {
      "senderType": "BOT",
      "messageType": "ANSWER",
      "content": "근태 시스템에서 확인할 수 있습니다."
    }
  ],
  "documents": [
    {
      "id": 10,
      "title": "복지카드 신청 안내",
      "content": "복지카드는 관련 안내 문서를 기준으로 신청한다.",
      "documentType": "HR",
      "department": "인사팀"
    }
  ]
}
```

### 응답 형식

```json
{
  "answer": "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.",
  "sourceDocumentId": 10
}
```

---

## 4. CORS

`main.py`에 `allow_origins=["*"]`로 전체 허용되어 있음.
프론트 `localhost:5173` 별도 추가 없이 바로 호출 가능.

---

## 5. 배포 방법 (현재)

오라클 프리티어 가입 불가 → **노트북 + Cloudflare Tunnel**로 운영 중.

### 실행 순서

1. `start.bat` 실행 → FastAPI 서버 시작 (`localhost:8000`)
2. Cloudflare Tunnel 실행:
```bash
cloudflared tunnel --url http://localhost:8000
```
3. 터미널에 출력된 `https://xxxx.trycloudflare.com` URL을 팀에 공유

### Streamlit 실행 (테스트용)

```bash
start_streamlit.bat
```
→ `localhost:8501` + Cloudflare Tunnel 자동 연결

---

## 6. 주의사항

- Streamlit은 프론트 완성 후 제거 예정
