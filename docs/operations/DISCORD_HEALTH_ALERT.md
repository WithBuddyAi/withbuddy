# Discord Server Health Alert (SCRUM-266)

서버 다운 및 OOM 상황을 빠르게 감지하기 위해, 헬스체크 기반 디스코드 알림 스크립트를 운영 서버 cron으로 실행한다.

## 1) KPI / OKR / 평가셋

| 항목 | 정의 |
|---|---|
| 목표 KPI | 장애 발생 후 1분 이내(최대 2분) Discord 알림 도착 |
| OKR 연결 | 데모/운영 중 서버 장애 인지 지연 최소화 |
| 평가셋 | `cron */1 * * * *` 실행 + 정상/비정상/복구 + OOM 로그 감지 시나리오 |
| 합격 기준 | DOWN/RECOVERY/OOM 이벤트가 webhook 채널에 정상 전송되고, 멘션 남용 없이(`allowed_mentions.parse=[]`) 전달 |

## 2) 스크립트 위치

- 파일: `scripts/discord-health-alert.sh`

필수 환경변수:

- `HEALTHCHECK_URL` (예: `http://127.0.0.1:8080/actuator/health`)
- `DISCORD_WEBHOOK_URL`

선택 환경변수:

- `CHECK_NAME` (기본 `withbuddy-server`)
- `CURL_CONNECT_TIMEOUT` (기본 `5`)
- `CURL_MAX_TIME` (기본 `10`)
- `ALERT_COOLDOWN_SECONDS` (기본 `600`)
- `OOM_CHECK_ENABLED` (`1` 또는 `0`, 기본 `1`)
- `OOM_LOOKBACK` (기본 `5 minutes ago`)

## 3) 서버 배포 예시 (OCI Ubuntu)

```bash
cd /home/ubuntu/withbuddy
chmod +x scripts/discord-health-alert.sh
```

`.env.monitor` 예시:

```bash
HEALTHCHECK_URL=http://127.0.0.1:8080/actuator/health
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/REDACTED/REDACTED
CHECK_NAME=withbuddy-backend-prod
CURL_CONNECT_TIMEOUT=5
CURL_MAX_TIME=10
ALERT_COOLDOWN_SECONDS=600
OOM_CHECK_ENABLED=1
OOM_LOOKBACK=5 minutes ago
```

## 4) 수동 검증

정상:

```bash
set -a
source /home/ubuntu/.env.monitor
set +a
/home/ubuntu/withbuddy/scripts/discord-health-alert.sh
```

비정상 강제 테스트(잘못된 URL):

```bash
set -a
source /home/ubuntu/.env.monitor
set +a
HEALTHCHECK_URL=http://127.0.0.1:65535/health \
  /home/ubuntu/withbuddy/scripts/discord-health-alert.sh
```

## 5) cron 등록

매 1분 실행:

```bash
crontab -e
```

```cron
* * * * * /bin/bash -lc 'set -a; source /home/ubuntu/.env.monitor; set +a; /home/ubuntu/withbuddy/scripts/discord-health-alert.sh >> /home/ubuntu/withbuddy/logs/discord-health-alert.log 2>&1'
```

## 6) 운영 메모

- `DOWN` 알림은 최초 장애 시 즉시 전송된다.
- 장애 지속 중에는 `ALERT_COOLDOWN_SECONDS` 기준으로 재알림된다.
- 복구 시 `RECOVERY` 알림이 전송된다.
- OOM 로그(`Out of memory`, `Killed process`)가 커널 로그에서 감지되면 `OOM_DETECTED` 알림이 전송된다.
