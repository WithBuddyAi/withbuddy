# With Buddy AI 서버 가이드

> 2026-03-30 기준 정리 (실서버 운영 기준)

**최종 업데이트**: 2026-04-11
**버전**: 0.6.0
**작성일**: 2026-03-24

---

## 1. 서버 구조

```
Oracle Compute (AI 서버: <AI_SERVER_PUBLIC_IP>)
├── FastAPI (uvicorn, systemd)
├── LangChain/LangGraph
└── ChromaDB (로컬 디스크 저장)
```

- AI 서버는 FastAPI + ChromaDB를 단일 인스턴스로 운영한다.
- ChromaDB는 파일 기반이며 `CHROMA_PERSIST_DIR` 경로를 사용한다.
- Backend는 AI 내부 API(`POST /internal/ai/answer`)만 호출한다.
- AI API 서비스 외부 도메인은 `ai.itsdev.kr`를 사용한다.
- 백엔드 연동 기본값은 `AI_API_URL=https://ai.itsdev.kr`이다.

**최소 사양**: RAM 4GB, CPU 2코어 (GPU 불필요)

---

## 2. 엔드포인트 정리

| 엔드포인트 | 용도 | 누가 호출 |
|---|---|---|
| `POST /internal/ai/answer` | 백엔드 ↔ AI 연동 | 백엔드만 |
| `GET /health` | 헬스 체크 | 운영/CI |
| `POST /chat`, `POST /chat/stream` | 테스트용 | 내부 테스트 |

기본 호출 URL 예시:
- `POST ${AI_API_URL}/internal/ai/answer`

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
- 앱은 `uvicorn main:app`로 실행한다.
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

### 4.4 Nginx 민감 파일 차단 규칙

- 적용 파일: `/etc/nginx/sites-available/ai.itsdev.kr`
- 목적: `.env`, `.git`, 백업/설정 파일 등 민감 경로의 직접 노출 차단

```nginx
location ~ /\.(?!well-known).* {
    deny all;
    return 404;
}

location ~* /(\.git|\.svn|\.hg|CVS)(/|$) {
    deny all;
    return 404;
}

location ~* \.(env|ini|log|conf|sql|bak|old|orig|save|swp|swo|tmp|yml|yaml)$ {
    deny all;
    return 404;
}
```

배포 후 점검 명령:

```bash
curl -i https://ai.itsdev.kr/.env
curl -i https://ai.itsdev.kr/.git/config
curl -i https://ai.itsdev.kr/health
```

기대 결과:
- `/.env`, `/.git/config` → `404`
- `/health` → `200`

Nginx 버전 노출 제한:

```nginx
http {
    server_tokens off;
}
```

검증:

```bash
curl -I https://ai.itsdev.kr/
```

기대 결과:
- `Server: nginx` (버전 미표기)

### 4.5 데이터 경계 운영 기준

- 현재 운영 기준은 `Frontend → Backend → AI` 단방향 연동이다.
- DB(MySQL)는 Backend만 접근하며, AI 서버는 DB에 직접 접근하지 않는다.
- 사용자 원본 데이터 저장/수정 책임은 Backend에만 둔다.

## 5. CI/CD 배포 전 필수 점검

`/.github/workflows/ai-deploy.yml`가 실패 없이 동작하려면 아래가 선행되어야 한다.

1. `${{ secrets.AI_APP_DIR }}`가 git repository 경로여야 한다.
2. `${{ secrets.AI_APP_DIR }}` 하위에 `venv`가 존재해야 한다.
3. `${{ secrets.AI_SERVICE_NAME }}`에 해당하는 `systemd` 서비스가 존재해야 한다.
4. 배포 사용자(`ubuntu`)가 `sudo systemctl restart/status <${{ secrets.AI_SERVICE_NAME }}>`를 비밀번호 없이 수행할 수 있어야 한다.
5. 서비스 재기동 후 `http://127.0.0.1:8000/health`가 `200`을 반환해야 한다.

---

## 6. 2026-03-29 실서버 점검 결과 (<AI_SERVER_PUBLIC_IP>)

### 확인된 상태

- SSH 접속 정상
- 리스닝 포트는 `22`만 확인됨 (`80/443/8000` 미기동)
- `uvicorn`, `nginx`, `withbuddy-ai.service` 미구성
- `ai/.env` 미존재
- `/home/ubuntu/withbuddy/ai`에 실행 엔트리포인트 코드 부재

### 현재 결론

- 현재 상태는 "배포 준비 완료"가 아니며, CI/CD 실행 시 `systemctl restart` 단계에서 실패할 수 있다.

---

## 7. 2026-04-06 실서비스 점검 결과 (`ai.itsdev.kr`)

### 확인된 상태

- DNS 확인: `ai.itsdev.kr` 도메인 해석 정상
- `GET https://ai.itsdev.kr/health` → `200`
- `GET https://ai.itsdev.kr/internal/ai/answer` → `405` (메서드 제한 정상)
- `POST https://ai.itsdev.kr/internal/ai/answer` (빈 JSON) → `422` (요청 검증 동작 정상)
- 외부 노출 기준으로 AI API 서비스가 동작 중임을 확인

### 현재 결론

- 외부 엔드포인트 기준으로 현재 상태는 "배포 준비 완료"로 판단한다.
- AI API 엔드포인트와 헬스체크는 정상 응답한다.
- 현재 결과는 외부 엔드포인트 기준 점검이며, SSH/systemd/프로세스 내부 상태 점검은 별도 서버 접근으로 확인한다.

---

## 8. 변경 이력

- 2026-04-07: `ai.itsdev.kr` Nginx 민감 경로 차단 규칙(`.env`, `.git`, 백업 확장자)과 검증 명령을 추가.
- 2026-04-07: Nginx `server_tokens off` 적용 기준을 추가해 버전 노출 제한을 명시.
- 2026-04-06: AI API 서비스 도메인(`ai.itsdev.kr`)과 `AI_API_URL` 운영 기본값을 명시.
- 2026-04-06: 실서비스 점검 결과를 외부 엔드포인트 기준(`health`, `internal/ai/answer`)으로 업데이트.
- 2026-04-06: 현재 운영 기준(`DB는 Backend만 접근`)에 맞춰 AI 서버 문서에서 AI 직접 DB/캐시/메시징 접근 항목을 제거.
- 2026-04-02: 변경 이력 중복 항목을 통합 정리.
- 2026-04-01: Redis(캐시)·RabbitMQ(메시징) 역할 분리 운영 기준을 정리하고, DB 서버 공용 구축 시 보안/신뢰성 기준 및 운영 점검 명령을 추가.
- 2026-03-30: GitHub Actions 시크릿 표기를 `${{ secrets.* }}` 형식으로 통일.
- 2026-03-29: 운영 기준을 실서버/CI 기반으로 개편하고 CI/CD 선행조건을 추가.
- 2026-04-01: Redis(캐시)·RabbitMQ(메시징) 역할 분리 운영 기준을 정리하고, DB 서버 공용 구축 시 보안/신뢰성 기준 및 운영 점검 명령을 추가.

