# withbuddy | BE Internal API v1.1 — AI↔Backend 통합 규약

> 🔌 **기준일**: 2026-05-01
> 

> **목적**: AI 서비스는 Backend API만 호출. Redis / RabbitMQ / DB는 Backend가 전담.
> 

> **Base URL**: `http://<BACKEND_PRIVATE_IP>:8080`
> 

> 🔒 실제 IP / Secret은 별도 **[접속 정보 시트]** 참고.
> 

---

## 1. 아키텍처 원칙

- AI → **Backend API만 사용**
- Backend → Redis / RabbitMQ / DB 처리
- AI의 `REDIS_URL`, `RABBITMQ_URL` 직접 사용 **금지**

---

## 2. 네트워크 / 접근 정책

| 항목 | 값 |
| --- | --- |
| Base URL | `http://<BACKEND_PRIVATE_IP>:8080` |
| 내부 허용 대역 | `<AI_VCN_CIDR>` (AI 서버 대역) |
| 인증 | `X-Internal-Token` 필수 |
| 권장 | ACL + 토큰 동시 적용 |

---

## 3. 공통 규약

### 요청 헤더

```jsx
Content-Type: application/json
X-API-Key: <internal-shared-secret>
X-Request-Id: <uuid>
```

> 🔐 **인증 설계 원칙**
> 

> - `X-API-Key`는 기존 Backend 내부 API 인증 체계 재활용 — AI → Backend 서버 간 통신 전용 키
> 

> - 사용자 인증용 JWT(`Authorization: Bearer`)와 **혼용 금지**
> 

> - 일반 사용자 요청 (`Frontend → Backend`): `Authorization: Bearer <access-token>`
> 

> - 내부 서버 요청 (`AI → Backend Internal API`): `X-API-Key: <internal-shared-secret>`
> 

> - Callback (`BE → AI`): `X-API-Key` 단독 사용보다 **HMAC Signature 유지** (하단 #7 참고)
> 

**`INTERNAL_API_AUTH_ENABLED` 동작**

| 값 | 동작 |
| --- | --- |
| `true` (기본) | `X-API-Key` 필수. `Authorization: Bearer` 단독으로 Internal API 접근 불가. |
| `false` | `Bearer` 흐름으로 동작 허용 (로칼 / 이행 단계 전용) |

### 응답 시간

- KST / UTC ISO8601 사용 권장 (예: `2026-05-01T11:20:00+09:00`)

### 에러 포맷 (RFC 9457)

> 📎 [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 준수
> 

```json
{
  "type": "https://api.withbuddy.internal/probs/invalid-request",
  "title": "Invalid Request",
  "status": 400,
  "detail": "key is required",
  "instance": "/internal/v1/cache/get",
  "requestId": "a6c8..."
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `type` | ✓ | 에러 유형 URI (식별자 — 접근 가능 URL 아님) |
| `title` | ✓ | 에러 유형 요약 |
| `status` | ✓ | HTTP 상태코드 정수 |
| `detail` | - | 이번 요청에 대한 구체적 설명 |
| `instance` | - | 요청 경로 |
| `requestId` | - | `X-Request-Id` 전파값 (WithBuddy 확장 필드) |

### X-Request-Id 전파 규약

- AI → BE 요청 시 `X-Request-Id` 포함 (UUID v4 권장)
- BE → Task 처리 내부에서 동일 ID 유지 (로그 연결)
- BE → AI Callback 시 동일 `X-Request-Id` 포함
- 에러 응답의 `requestId` 필드에 반드시 포함

---

## 4. Redis 추상화 API

### POST /internal/v1/cache/get

**요청**

```json
{"namespace":"ai","key":"ctx:user:123","defaultValue":null}
```

**응답**

```json
{"found":true,"value":{"step":"plan"},"ttlSeconds":120}
```

---

### POST /internal/v1/cache/get-multi

**요청**

```json
{"namespace":"ai","keys":["ctx:user:123","embed:doc:777"]}
```

**응답**

```json
{
  "items":[
    {"key":"ctx:user:123","found":true,"value":{"step":"plan"},"ttlSeconds":120},
    {"key":"embed:doc:777","found":false,"value":null,"ttlSeconds":null}
  ]
}
```

---

### POST /internal/v1/cache/set

**요청**

```json
{"namespace":"ai","key":"ctx:user:123","value":{"step":"plan"},"ttlSeconds":300}
```

**응답**

```json
{"ok":true}
```

---

### POST /internal/v1/cache/set-multi

> ⚠️ **원자성**: 비원자적(non-atomic). 개별 항목 실패 시 `errors` 배열에 포함, 나머지는 정상 저장됩니다.
> 

**요청**

```json
{
  "namespace":"ai",
  "items":[
    {"key":"k1","value":{"a":1},"ttlSeconds":300},
    {"key":"k2","value":{"b":2},"ttlSeconds":300}
  ]
}
```

**응답 (전체 성공)**

```json
{"ok":true,"written":2}
```

**응답 (부분 실패)**

```json
{
  "ok": false,
  "written": 1,
  "errors": [
    {
      "key": "k2",
      "type": "https://api.withbuddy.internal/probs/value-too-large",
      "detail": "value exceeds 64KB limit"
    }
  ]
}
```

---

### POST /internal/v1/cache/del

**요청**

```json
{"namespace":"ai","key":"ctx:user:123"}
```

**응답**

```json
{"ok":true,"deleted":1}
```

---

## 5. RabbitMQ 추상화 API (Task)

### POST /internal/v1/tasks

**요청**

```json
{
  "type":"ai.nudge.analysis",
  "payload":{"userId":"123","context":"..."},
  "priority":"normal",
  "idempotencyKey":"nudge-123-v1",
  "timeoutSeconds":600,
  "retryCount":3,
  "callbackUrl":"http://<AI_PRIVATE_IP>:8000/api/v1/callback"
}
```

**응답** `202 Accepted`

```json
{"taskId":"task_01JT...","status":"QUEUED","queuedAt":"2026-05-01T11:20:00+09:00"}
```

**idempotencyKey 규칙**

| 케이스 | 처리 |
| --- | --- |
| 동일 key + 동일 body | 기존 응답 재반환 (`202`) |
| 동일 key + 다른 body | `409 Conflict` |
| key 보관 TTL | 24시간 (완료 여부 무관) |

> TTL 만료 후 동일 key 재사용 시 새 Task로 처리됩니다.
> 

---

### DELETE /internal/v1/tasks/{taskId} (취소)

**응답** `200`

```json
{"taskId":"task_01JT...","status":"CANCELLED"}
```

> `SUCCESS` / `FAILED` / `TIMED_OUT` / `CANCELLED` 상태에서 취소 요청 시 `409`.
> 

---

### POST /internal/v1/tasks/{taskId}/retry (재시도)

**요청**

```json
{"reason":"manual-retry"}
```

**응답** `202`

```json
{"taskId":"task_01JT...","status":"QUEUED","queuedAt":"2026-05-01T11:20:00+09:00"}
```

> `RUNNING` 상태 재시도 시 `409`. `SUCCESS` 재시도 시 새 `taskId` 발급.
> 

---

### GET /internal/v1/tasks/{taskId}

**응답**

```json
{
  "taskId":"task_01JT...",
  "status":"RUNNING",
  "progress":65,
  "queuePosition":4,
  "etaSeconds":75,
  "updatedAt":"2026-05-01T11:21:10+09:00",
  "error":null
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `queuePosition` | - | **optional** — QUEUED 상태에서만 유효, 예측 불가 시 `null` |
| `etaSeconds` | - | **optional** — 예측 불가 시 `null` |

---

### GET /internal/v1/tasks/{taskId}/result

**응답**

```json
{"taskId":"task_01JT...","status":"SUCCESS","result":{"summary":"..."}}
```

> ⚠️ Task TTL 만료(완료 후 24시간) 후 조회 시 **`404`** 반환 (tombstone 없음). AI는 TTL 내 결과를 반드시 수신/처리해야 합니다.
> 

---

## 6. Task 상태 모델

| 상태 | 설명 |
| --- | --- |
| `QUEUED` | 큐에 등록됨 |
| `RUNNING` | 처리 중 |
| `SUCCESS` | 완료 |
| `FAILED` | 처리 실패 |
| `TIMED_OUT` | 타임아웃 초과 |
| `CANCELLED` | 수동 취소됨 |

---

## 7. Callback 계약 (BE → AI)

### 이벤트 페이로드

```json
{
  "event":"TASK_COMPLETED",
  "taskId":"task_01JT...",
  "status":"SUCCESS",
  "result":{"summary":"..."},
  "sentAt":"2026-05-01T11:25:00+09:00"
}
```

### 콜백 헤더

```
X-Callback-Signature: sha256=<hmac>
X-Callback-Timestamp: <unix-seconds>
X-Request-Id: <uuid>
```

### 서명 생성 (BE 발송)

Canonical String 형식:

```
{X-Callback-Timestamp}\n{X-Request-Id}\n{raw-request-body}
```

서명 생성:

```
HMAC-SHA256(Shared Secret, canonical_string) → hex 인코딩
X-Callback-Signature: sha256=<hex>
```

### 검증 규약 (AI 수신)

1. `X-Callback-Timestamp` — 현재 시각 ±300초 이내 확인
2. Canonical String 재조합 → HMAC-SHA256 비교
3. `X-Request-Id` nonce 중복 확인 — **nonce 보관 TTL: 600초**
    - 600초 내 동일 nonce 재수신 → `200` 반환 (멱등 응답, 재처리 없음)
4. 검증 실패 시 `401` 반환

---

## 8. 운영 정책

| 항목 | 규칙 |
| --- | --- |
| Redis 키 규칙 | `ai:{namespace}:{key}` |
| value 타입 | JSON only 권장 |
| get-multi / set-multi 최대 키 수 | 200 |
| Task 상태 / 결과 TTL | 완료 후 24시간 |
| RMQ 큐 이름 | `q.internal.tasks` (env: `RABBITMQ_QUEUE_INTERNAL_TASKS`) |
| RMQ 라우팅 키 | `internal.tasks.requested` |
| RMQ 라우팅 패턴 | `internal.tasks.#` |
| 재시도 기본값 | `retryCount` 기본 3, 최대 5 |
| DLQ | **권장** (미적용 시 메시지 유실 위험) |

---

## 9. 타임아웃 / 재시도 권장값

> 아래 값은 기본값입니다. 환경 / SLA에 따라 override 가능.
> 

| 엔드포인트 | Timeout | Retry | 비고 |
| --- | --- | --- | --- |
| `cache/get`, `cache/set` | 1~2초 | 1회 | - |
| `tasks` POST | 3초 | 1~2회 | `idempotencyKey` 필수 |
| `tasks/{id}` status / result | 2초 | 1회 | - |
| callback 수신 처리 | 5초 | 최대 5회 | 지수 백오프 적용 |

---

## 10. 상태코드 규약

| 코드 | 의미 |
| --- | --- |
| `200` | 성공 |
| `202` | 비동기 등록 성공 |
| `400` | 요청 검증 실패 |
| `401` | 인증 실패 |
| `404` | 리소스 없음 |
| `409` | 멱등성 충돌 |
| `429` | 과다 요청 |
| `500` | 내부 오류 |

---

## 11. Rate Limit 정책

> 아래 한도는 기본값이며 운영 환경에 따라 조정 가능합니다.
> 

| 엔드포인트 | 한도 | 백오프 |
| --- | --- | --- |
| `cache/*` | 1,000 req/min | 지수 백오프, 초기 1초 |
| `tasks` POST | 100 req/min | 지수 백오프, 초기 3초 |
| `tasks/*` GET | 500 req/min | 지수 백오프, 초기 1초 |

429 응답 예시 (RFC 9457):

```json
{
  "type": "https://api.withbuddy.internal/probs/too-many-requests",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded. Retry after 60 seconds.",
  "retryAfterSeconds": 60,
  "requestId": "a6c8..."
}
```

---

## 12. 필드 제약

| 필드 | 최대 크기 / 문자셋 |
| --- | --- |
| `namespace` | 영문소문자 + `_`, 최대 32자 |
| `key` | 영문/숫자/`:`/`_`/`-`, 최대 512자 |
| `value` (단일 항목) | 최대 64KB |
| `payload` (Task) | 최대 256KB |
| `callbackUrl` | 내부 IP 또는 HTTPS, 최대 512자 |
| `idempotencyKey` | ASCII 인쇄 가능 문자, 최대 128자 |

---

## 변경 이력

### v1.1 — 2026-05-01

**1) 엔드포인트 / 식별자 네이밍**

- `/internal/v1/jobs` → `/internal/v1/tasks`
- `{jobId}` → `{taskId}`, 응답 필드 `jobId` → `taskId`, 예시 ID `job_...` → `task_...`

**2) 클래스 / 모델 네이밍**

- `InternalJobApiService` → `InternalTaskApiService`
- `JobCreateRequest/Response` → `TaskCreateRequest/Response`
- `JobStatusResponse` → `TaskStatusResponse`
- `JobState` → `TaskState`, `InternalJobMessage` → `InternalTaskMessage`

**3) MQ 네이밍**

- 라우팅 키: `internal.jobs.requested` → `internal.tasks.requested`
- 바인딩 패턴: `internal.jobs.#` → `internal.tasks.#`
- 설정 키: `queue-internal-jobs` → `queue-internal-tasks`
- 환경변수: `RABBITMQ_QUEUE_INTERNAL_JOBS` → `RABBITMQ_QUEUE_INTERNAL_TASKS`
- 기본 큐명: `q.internal.jobs` → `q.internal.tasks`

**4) 인증 규정 공통화 (Storage API와 동일 원칙)**

- Internal API 기본 헤더: `X-API-Key`
- `INTERNAL_API_AUTH_ENABLED=true`: `X-API-Key` 필수, `Bearer` 단독 접근 불가
- `INTERNAL_API_AUTH_ENABLED=false`: `Bearer` 흐름 허용 (로칼 / 이행 단계)

**5) 검증**

- `./gradlew.bat compileJava` 성공