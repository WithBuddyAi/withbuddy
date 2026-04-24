# BE ↔ AI 서버 대화 이력 전달 API 명세서

> **버전**: v1.1  
> **작성일**: 2026-04-24  
> **수정일**: 2026-04-24 —  4. Thundering Herd 방지, 5. RPUSH+LTRIM Sliding Window, 6. 트랜잭션 분리 반영  
> **대상 엔드포인트**: `POST /internal/ai/answer`  
> **작성 배경**: AI가 이전 대화를 기억하지 못하는 문제 해결을 위해 BE → AI 서버 요청에 대화 이력을 추가

---

## 1. 변경 요약

| 항목 | 변경 전 | 변경 후 |
|------|---------|---------|
| 요청 필드 | questionId, user, content | questionId, user, content, **conversationHistory** 추가 |
| 대화 이력 출처 | 없음 | Redis (세션 내) → DB fallback (만료 후) |
| AI 응답 품질 | 단발성 (맥락 없음) | 이전 대화 참조 가능 |

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

> 분산 락 구현은 기존 `RedisLockManager.withLock()`을 재사용한다.

---

## 5. Redis 저장 포맷

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
-- 새 Q&A 페어 저장 시
RPUSH conversation:{userId} '{"role":"user","content":"연차는 어떻게 쓰나요?"}'
RPUSH conversation:{userId} '{"role":"assistant","content":"연차는 입사 1년 후..."}'
LTRIM conversation:{userId} -10 -1   -- 최신 10개(5턴) 유지, 초과분 자동 제거
EXPIRE conversation:{userId} 1800    -- TTL 30분 초기화
```

> Sliding Window 로직은 BE 저장 시점에 `RPUSH + LTRIM`으로 처리한다.  
> AI 서버에 턴 수 제어를 맡기지 않는다.

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

### 트랜잭션 분리 주의사항

현재 `saveUserMessage()`는 `@Transactional` 안에서 Redis 호출과 AI 서버 HTTP 호출을 함께 수행하고 있다. AI 서버 응답(평균 4.69초) 동안 DB 커넥션이 점유되는 문제가 있으므로, 이력 관련 Redis 로직은 트랜잭션 밖에서 처리한다.

```
[트랜잭션 밖]  Redis에서 대화 이력 조회 (Redis → DB fallback)
     ↓
[트랜잭션 시작]  질문 메시지 DB 저장
[트랜잭션 종료]
     ↓
[트랜잭션 밖]  AI 서버 호출 (최대 ~20초)
     ↓
[트랜잭션 시작]  AI 답변 메시지 DB 저장
[트랜잭션 종료]
     ↓
[트랜잭션 밖]  Redis에 Q&A 페어 저장 (RPUSH + LTRIM + EXPIRE)
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
| AI 응답 실패 | timeout 등 | Redis 이력 업데이트 안 함 (기존 이력 보존) |
