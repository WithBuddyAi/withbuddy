# BE ↔ AI 서버 대화 이력 전달 API 명세서

> **버전**: v1.2  
> **작성일**: 2026-04-24  
> **수정일**: 2026-04-27 — 비동기 AI PENDING 처리, turn 순서 보존, timeout 정책 반영  
> **대상 엔드포인트**: `POST /internal/ai/answer`  
> **작성 배경**: AI가 이전 대화를 기억하지 못하는 문제 해결을 위해 BE → AI 서버 요청에 대화 이력을 추가

---

## 1. 변경 요약

| 항목 | 변경 전 | 변경 후 |
|------|---------|---------|
| 요청 필드 | questionId, user, content | questionId, user, content, **conversationHistory** 추가 |
| 대화 이력 출처 | 없음 | Redis (세션 내) → DB fallback (만료 후) |
| AI 응답 품질 | 단발성 (맥락 없음) | 이전 대화 참조 가능 |
| AI 응답 지연 처리 | 요청 스레드가 AI 응답까지 대기 | 10초 초과 시 `PENDING` 반환 후 비동기 처리 |
| Redis turn 저장 | AI 응답 완료 후 Q&A 페어 저장 | 질문 저장 시 `user` turn 선저장, 답변 완료 시 `assistant` turn 저장 |

---

## 2. 요청 스펙 (Request)

### 엔드포인트

```
POST /internal/ai/answer
Content-Type: application/json
```

### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `questionId` | Long | Y | 질문 메시지 ID |
| `user` | AiUserContext | Y | 사용자 정보 |
| `user.userId` | Long | Y | 사용자 ID |
| `user.name` | String | Y | 사용자 이름 |
| `user.companyCode` | String | Y | 회사 코드 |
| `content` | String | Y | 현재 질문 내용 |
| `conversationHistory` | ConversationTurn[] | Y | 이전 대화 이력 (없으면 빈 배열 `[]`) |
| `conversationHistory[].role` | String | Y | `"user"` 또는 `"assistant"` |
| `conversationHistory[].content` | String | Y | 해당 턴의 메시지 내용 |

### 요청 예시 — 첫 질문 (이력 없음)

```json
{
  "questionId": 201,
  "user": {
    "userId": 1,
    "name": "김지원",
    "companyCode": "WB0001"
  },
  "content": "연차는 어떻게 쓰나요?",
  "conversationHistory": []
}
```

### 요청 예시 — 이어지는 질문 (이력 있음)

```json
{
  "questionId": 202,
  "user": {
    "userId": 1,
    "name": "김지원",
    "companyCode": "WB0001"
  },
  "content": "그럼 연차 신청은 어디서 해요?",
  "conversationHistory": [
    {
      "role": "user",
      "content": "연차는 어떻게 쓰나요?"
    },
    {
      "role": "assistant",
      "content": "연차는 입사 1년 후 15일이 부여되며, 사내 HR 시스템에서 신청할 수 있습니다."
    }
  ]
}
```

---

## 3. 응답 스펙 (Response)

변경 없음. 현재 스펙 유지.

```json
{
  "questionId": 202,
  "messageType": "rag_answer",
  "content": "연차 신청은 HR 시스템 > 근태관리 메뉴에서 하실 수 있습니다.",
  "documents": [
    { "documentId": 5 }
  ]
}
```

---

## 4. 대화 이력 조회 전략 (BE 내부)

AI 호출 전 `conversationHistory`를 구성하는 우선순위:

```
1순위: Redis  conversation:{userId}
   → 세션 내 최근 대화 (TTL 30분)
   → HIT 시 바로 사용

2순위: DB  chat_messages 테이블
   → Redis MISS 또는 TTL 만료 시 fallback
   → userId 기준 최근 10개 메시지 조회
   → user_question / rag_answer 타입만 필터링
   → 조회 결과를 Redis에 다시 적재 (TTL 초기화)

이력 없음: conversationHistory = []
   → 첫 질문이거나 DB에도 기록 없을 때
```

### 최대 전달 턴 수

| 항목 | 값 |
|------|-----|
| 최대 턴 수 | **5턴 (Q&A 10개 메시지)** |
| 초과 시 처리 | RPUSH + LTRIM으로 Redis 저장 시점에 BE가 직접 제어 |
| 이유 | AI 서버 토큰 한도 초과 방지, AI 서버에 슬라이딩 로직 의존 금지 |

### Thundering Herd 방지 (DB Fallback 동시 요청)

Redis TTL 만료 직후 다수의 요청이 동시에 DB fallback을 시도하면 `chat_messages` 테이블에 과부하가 발생할 수 있다. 이를 방지하기 위해 DB 조회 전 분산 락을 사용한다.

```
Redis MISS 발생 시:
  1. lock:conv:{userId} 분산 락 획득 시도 (TTL 5초)
  2. 락 획득 성공 → DB 조회 → Redis 적재 → 락 해제
  3. 락 획득 실패 (다른 요청이 선점) → 짧게 대기 후 Redis 재조회
     → 이미 적재되어 있으면 HIT 처리
     → 여전히 MISS면 conversationHistory = [] 로 진행 (graceful degradation)
```

> 분산 락 구현은 `RedisCacheService.putIfAbsent()`와 Lua 기반 `releaseLock()`을 사용한다.

---

## 5. Redis 저장 포맷 및 turn 순서 정책

### 키

```
conversation:{userId}       -- 대화 이력 List 자료구조
lock:conv:{userId}          -- DB fallback 분산 락 (TTL 5초)
```

### 자료구조 변경

**변경 전** — String (마지막 질문 1개)
```
SET conversation:{userId} "연차는 어떻게 쓰나요?"
```

**변경 후** — Redis List (RPUSH + LTRIM으로 최신 5턴 유지)

```
-- 질문 메시지 저장 직후
RPUSH conversation:{userId} '{"role":"user","content":"연차는 어떻게 쓰나요?"}'

-- AI 답변 저장 직후 (동기 완료 또는 비동기 완료)
RPUSH conversation:{userId} '{"role":"assistant","content":"연차는 입사 1년 후..."}'
LTRIM conversation:{userId} -10 -1   -- 최신 10개(5턴) 유지, 초과분 자동 제거
EXPIRE conversation:{userId} 1800    -- TTL 30분 초기화
```

> Sliding Window 로직은 BE 저장 시점에 `RPUSH + LTRIM`으로 처리한다.  
> AI 서버에 턴 수 제어를 맡기지 않는다.

### 비동기 완료 시 순서 보존

AI 응답이 10초를 초과하면 API는 즉시 `PENDING`을 반환하고 백그라운드에서 답변 생성을 계속한다. 이때 질문과 답변을 비동기 완료 시점에 함께 append하면 사용자가 대기 중 다른 질문을 보낸 경우 Redis 이력이 다음처럼 뒤섞일 수 있다.

```
Q2, A2, Q1, A1
```

이를 방지하기 위해 turn 저장 시점을 분리한다.

```
1. 질문 DB 저장 직후: user turn append
2. 동기 AI 완료: assistant turn append
3. 비동기 AI 완료: assistant turn append
4. 비동기 스케줄링 실패 또는 AI 실패: assistant turn append 없음, rag 상태만 TIMEOUT 처리
```

이 정책으로 다음 AI 호출이 읽는 `conversationHistory`의 시간 순서를 안정적으로 유지한다.

### TTL

```
30분 (기존 유지)
매 질문/응답 저장 시 EXPIRE로 TTL 초기화
```

---

## 6. BE 구현 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `infrastructure/ai/dto/ConversationTurn.java` | 신규 record 생성 |
| `infrastructure/ai/dto/AiAnswerServerRequest.java` | `conversationHistory` 필드 추가 |
| `chat/service/ChatMessageService.java` | 이력 조회/저장 로직 추가, **트랜잭션 범위 분리** |
| `chat/dto/ChatMessageCreateResponse.java` | `status` 필드 추가 (`COMPLETED`, `PENDING`, `TIMEOUT`) |
| `chat/dto/ChatMessageStatusResponse.java` | 비동기 처리 상태 조회 응답 DTO 추가 |
| `chat/service/AsyncAiCallService.java` | `PENDING` 이후 백그라운드 AI 재호출 |
| `chat/service/ChatAnswerSaveService.java` | 비동기 답변 저장 및 `assistant` turn append |
| `global/config/AsyncConfig.java` | AI 비동기 실행용 bounded executor 설정 |
| `infrastructure/ai/config/AsyncAiRestClientConfig.java` | 비동기 AI RestClient timeout 설정 |

### 채팅 API 응답 상태

`POST /api/v1/chat/messages`는 AI 응답 시간에 따라 다음처럼 응답한다.

```
10초 내 응답:
  { question, answer, status: "COMPLETED" }

10초 초과:
  { question, answer: null, status: "PENDING" }

10초 초과 후 비동기 스케줄링 실패:
  { question, answer: null, status: "TIMEOUT" }
```

비동기 처리 상태는 다음 API로 조회한다.

```
GET /api/v1/chat/messages/{questionId}/status
```

응답 예시:

```json
{
  "status": "COMPLETED",
  "answer": {
    "id": 202,
    "senderType": "BOT",
    "messageType": "rag_answer",
    "content": "연차 신청은 HR 시스템 > 근태관리 메뉴에서 하실 수 있습니다."
  }
}
```

### Timeout 정책

| 항목 | 환경변수 | 기본값 | 목적 |
|------|----------|--------|------|
| AI connection timeout | `AI_SERVER_CONNECT_TIMEOUT_MS` | `5000` | AI 서버 TCP 연결 대기 |
| AI 동기 read timeout | `AI_SERVER_READ_TIMEOUT_MS` | `10000` | 사용자 요청을 붙잡는 최대 응답 대기 |
| AI 비동기 read timeout | `AI_SERVER_ASYNC_READ_TIMEOUT_MS` | `10000` | `PENDING` 이후 백그라운드 AI 응답 대기 |

### 트랜잭션 분리 주의사항

현재 `saveUserMessage()`는 `@Transactional` 안에서 Redis 호출과 AI 서버 HTTP 호출을 함께 수행하고 있다. AI 서버 응답(평균 4.69초) 동안 DB 커넥션이 점유되는 문제가 있으므로, 이력 관련 Redis 로직은 트랜잭션 밖에서 처리한다.

```
[트랜잭션 밖]  Redis에서 대화 이력 조회 (Redis → DB fallback)
     ↓
[트랜잭션 시작]  질문 메시지 DB 저장
[트랜잭션 종료]
     ↓
[트랜잭션 밖]  Redis에 user turn 저장 (RPUSH + LTRIM + EXPIRE)
     ↓
[트랜잭션 밖]  AI 서버 호출 (동기 read timeout 기본 10초)
     ↓
[트랜잭션 시작]  AI 답변 메시지 DB 저장
[트랜잭션 종료]
     ↓
[트랜잭션 밖]  Redis에 assistant turn 저장 (RPUSH + LTRIM + EXPIRE)
```

> `@CacheEvict`와 동일한 원칙: Redis 작업은 트랜잭션 완료 후 수행.

---

## 7. AI 서버 구현 변경 범위

> AI 서버 팀 담당

### 요청 스키마 추가

```python
# schemas/request.py
class ConversationTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str

class AiAnswerServerRequest(BaseModel):
    question_id: int
    user: AiUserContext
    content: str
    conversation_history: list[ConversationTurn] = []  # 추가
```

### LangGraph 체인 주입

```python
from langchain_core.messages import HumanMessage, AIMessage

def to_message_objects(history: list[ConversationTurn]):
    result = []
    for turn in history:
        if turn.role == "user":
            result.append(HumanMessage(content=turn.content))
        else:
            result.append(AIMessage(content=turn.content))
    return result

chain.invoke({
    "input": request.content,
    "chat_history": to_message_objects(request.conversation_history)
})
```

### 하위 호환

- `conversation_history`가 `[]`이면 기존 단발성 RAG와 동일하게 동작
- 필드 자체가 없을 경우에도 기본값 `[]`로 처리

---

## 8. 작업 순서

```
Step 1. 이 명세서 기준으로 BE / AI 서버팀 스펙 합의
Step 2. [AI 서버] conversation_history 수신 + LangGraph 주입 구현
Step 3. [BE] ConversationTurn DTO + ChatMessageService 이력 로직 구현
         - Redis 조회 → MISS 시 DB fallback → 이력 구성
         - AI 응답 후 Redis 업데이트
Step 4. [통합 테스트] 대화 연속성 end-to-end 확인
```

> Step 2, 3은 병렬 진행 가능. 각자 mock으로 독립 테스트 후 Step 4에서 통합.

---

## 9. 별도 이슈 (이번 작업 범위 외)

이번 구현에서 다루지 않지만 후속 작업으로 등록이 필요한 항목:

| 항목 | 내용 |
|------|------|
| SSE 세션 cleanup | 서버 재기동 시 해당 `instanceId`의 `sse:session:*` 키 일괄 삭제 |
| Presigned URL 실패 대응 | URL 생성 실패 시 캐싱하지 않고 즉시 에러 반환 |
| KEYS → SCAN 전환 | `deleteByPrefix()` 내 `redisTemplate.keys()` 사용 여부 점검 및 SCAN으로 교체 |
| 직렬화 최적화 | Jackson JSON → MessagePack/Protobuf (메모리 20~30% 절감, 여유 시 검토) |
| Caffeine L1 캐시 | `quicktap:{day}` 등 불변 데이터 애플리케이션 메모리 캐싱 (Redis 트래픽 감소) |

---

## 10. 테스트 시나리오

| 케이스 | 조건 | 기대 동작 |
|--------|------|-----------|
| 첫 질문 | Redis MISS, DB 기록 없음 | `conversationHistory: []` 전송 |
| 세션 내 연속 질문 | Redis HIT | Redis List에서 이력 조회 후 전송 |
| 세션 만료 후 재질문 | Redis MISS, DB 기록 있음 | 분산 락 획득 → DB 최근 10개 조회 → Redis 재적재 → 전송 |
| 오래된 대화 참조 | "저번에 복지카드 얘기했는데" | DB fallback으로 이력 복원하여 AI가 맥락 파악 |
| 동시 DB fallback | Redis MISS 상태에서 요청 다수 | 락 선점 1개만 DB 조회, 나머지는 Redis 재조회 또는 `[]`로 graceful 처리 |
| 6턴 이상 누적 | RPUSH 후 List 길이 > 10 | LTRIM -10 -1 으로 자동 제거, 5턴만 유지 |
| AI 동기 응답 성공 | 10초 내 AI 응답 | `status=COMPLETED`, user turn 저장 후 assistant turn 저장 |
| AI 동기 timeout | 10초 초과 | `status=PENDING`, user turn은 이미 저장, 비동기 처리 시작 |
| 비동기 AI 완료 | `PENDING` 이후 AI 응답 성공 | BOT 답변 저장, assistant turn만 append, `rag:answer:{questionId}` 저장, `status=COMPLETED` |
| 비동기 스케줄링 실패 | executor 포화 등 `TaskRejectedException` | `status=TIMEOUT`, assistant turn 저장 안 함 |
| AI 응답 실패 | timeout 등 | assistant turn 저장 안 함, 기존 이력 보존 |
