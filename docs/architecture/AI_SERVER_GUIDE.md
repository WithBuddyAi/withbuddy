# With Buddy AI 서버 가이드

> 2026-03-30 기준 정리 (실서버 운영 기준)

**최종 업데이트**: 2026-04-01  
**버전**: 1.1.0  
**작성일**: 2026-03-24

---

## 1. 서버 구조

```
Oracle Compute (AI 서버: 217.142.242.239)
├── FastAPI (uvicorn, systemd)
├── LangChain/LangGraph
└── ChromaDB (로컬 디스크 저장)
```

- AI 서버는 FastAPI + ChromaDB를 단일 인스턴스로 운영한다.
- ChromaDB는 파일 기반이며 `CHROMA_PERSIST_DIR` 경로를 사용한다.
- Backend는 AI 내부 API(`POST /internal/ai/answer`)만 호출한다.

**최소 사양**: RAM 4GB, CPU 2코어 (GPU 불필요)

---

## 2. 엔드포인트 정리

| 엔드포인트 | 용도 | 누가 호출 |
|---|---|---|
| `POST /internal/ai/answer` | 백엔드 ↔ AI 연동 | 백엔드만 |
| `GET /health` | 헬스 체크 | 운영/CI |
| `POST /chat`, `POST /chat/stream` | 테스트용 | 내부 테스트 |

---

## 3. 백엔드 연동 흐름

```
사용자 질문
  ↓
프론트 → 백엔드
  ↓
백엔드 → POST /internal/ai/answer (AI 서버)
  ↓
AI 서버 → answer + sourceDocumentId 반환
  ↓
백엔드 → DB 저장 후 프론트 응답
```

---

## 4. 운영 표준

### 4.1 서비스 실행

- 프로세스는 `systemd` 서비스(`withbuddy-ai.service`)로 관리한다.
- 앱은 `uvicorn app.main:app`로 실행한다.
- `--host 127.0.0.1 --port 8000`을 기본값으로 사용하고 외부 노출은 리버스 프록시에서 처리한다.

### 4.2 필수 파일/경로

- 앱 경로: `/home/ubuntu/withbuddy/ai`
- 가상환경: `/home/ubuntu/withbuddy/ai/venv`
- 환경변수 파일: `/etc/withbuddy/ai.env` (권장)
- Chroma 저장 경로: `/home/ubuntu/withbuddy/ai/chroma_db`

### 4.3 보안 기본값

- 공인 포트는 `22`, `80`, `443`만 허용한다.
- `8000`은 외부 공개하지 않는다.
- CORS는 운영 도메인으로 제한한다 (`*` 금지).

### 4.4 캐시/메시징 운영 기준

- Redis는 캐시 계층으로만 사용한다 (AI 응답 캐시, 토큰 블랙리스트, 레이트리밋).
- RabbitMQ는 비동기 작업 큐로 사용한다 (리포트 생성, 문서 재인덱싱, 알림 발송).
- 사용자 대화 원본 데이터 저장은 Backend + MySQL이 담당한다.
- AI 서버는 RabbitMQ 소비자(worker)로 동작할 수 있으나, 원본 데이터의 최종 쓰기 책임은 Backend에 둔다.

### 4.5 DB 서버(공용) Redis/RabbitMQ 설정 기준

DB 서버에 Redis/RabbitMQ를 함께 운영할 때는 아래를 기본값으로 둔다.

```yaml
Redis:
  bind: 127.0.0.1 + DB private IP
  auth: requirepass 또는 ACL 필수
  persistence: AOF enabled
  maxmemory-policy: allkeys-lru
  role: 채팅/간단 액션 캐시

RabbitMQ:
  ports:
    amqp: 5672 (내부망만 허용)
    management: 15672 (운영자 고정 IP만 허용)
  users:
    app: withbuddy_app
    admin: withbuddy_admin
  role: 주간 회고/리포트/장시간 비동기 작업
  reliability: retry + DLQ
```

운영 점검 명령:
```bash
sudo systemctl status redis-server --no-pager
sudo systemctl status rabbitmq-server --no-pager
redis-cli -a <REDIS_PASSWORD> ping
sudo rabbitmqctl list_queues name messages consumers
```

## 5. CI/CD 배포 전 필수 점검

`/.github/workflows/ai-deploy.yml`가 실패 없이 동작하려면 아래가 선행되어야 한다.

1. `${{ secrets.AI_APP_DIR }}`가 git repository 경로여야 한다.
2. `${{ secrets.AI_APP_DIR }}` 하위에 `venv`가 존재해야 한다.
3. `${{ secrets.AI_SERVICE_NAME }}`에 해당하는 `systemd` 서비스가 존재해야 한다.
4. 배포 사용자(`ubuntu`)가 `sudo systemctl restart/status <${{ secrets.AI_SERVICE_NAME }}>`를 비밀번호 없이 수행할 수 있어야 한다.
5. 서비스 재기동 후 `http://127.0.0.1:8000/health`가 `200`을 반환해야 한다.

---

## 6. 2026-03-29 실서버 점검 결과 (217.142.242.239)

### 확인된 상태

- SSH 접속 정상
- 리스닝 포트는 `22`만 확인됨 (`80/443/8000` 미기동)
- `uvicorn`, `nginx`, `withbuddy-ai.service` 미구성
- `ai/.env` 미존재
- `/home/ubuntu/withbuddy/ai`에 실행 엔트리포인트 코드 부재

### 현재 결론

현재 상태는 "배포 준비 완료"가 아니며, CI/CD 실행 시 `systemctl restart` 단계에서 실패할 수 있다.

---

## 7. 변경 이력

- 2026-03-30: GitHub Actions 시크릿 표기를 `${{ secrets.* }}` 형식으로 통일.
- 2026-03-29: 운영 기준을 실서버/CI 기반으로 개편하고 CI/CD 선행조건을 추가.
- 2026-04-01: Redis(캐시)와 RabbitMQ(메시징)의 역할 분리 운영 기준을 추가.
- 2026-04-01: DB 서버 공용 Redis/RabbitMQ 구축 시 보안/신뢰성 기준과 운영 점검 명령을 추가.
