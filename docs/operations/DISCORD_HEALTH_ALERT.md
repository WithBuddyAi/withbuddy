# Discord Server Health Alert (SCRUM-266)

2026-06-16 기준 운영 표준은 `Grafana Alerting + Prometheus + blackbox_exporter + Discord contact point` 입니다. 이 문서의 로컬 헬스체크 스크립트는 수동 fallback 용도로만 유지하며, 운영 배포 표준으로는 사용하지 않습니다.

## 1) KPI / OKR / 평가셋

| 항목 | 정의 |
|---|---|
| 목표 KPI | 장애 발생 후 1분 이내(최대 2분) Discord 알림 도착 |
| OKR 연결 | 데모/운영 중 서버 장애 인지 지연 최소화 |
| 평가셋 | Grafana alert rule firing/resolved + Discord contact point 전달 + blackbox/애플리케이션 헬스체크 시나리오 |
| 합격 기준 | Grafana alert firing/resolved 이벤트가 Discord 채널에 정상 전송되고, 동일 장애에 대해 별도 cron 알림이 중복 발송되지 않을 것 |

## 2) 현재 운영 정책

- 운영 표준: `Grafana Alerting -> Discord`
- 기본 source of truth: Grafana alert history, Prometheus scrape 상태, blackbox probe 결과
- 비표준: 백엔드 서버 로컬 `cron` 기반 Discord 알림
- 이유: 동일 장애에 대해 `Grafana`와 로컬 스크립트가 동시에 `DOWN/RECOVERY`를 보내면 운영 기록과 알림 해석이 분산된다.

## 3) 수동 fallback 스크립트 위치

- 파일: `scripts/discord-health-alert.sh`
- 용도: Grafana Alerting 자체가 불가하거나, 단일 서버에서 임시 검증이 꼭 필요할 때만 수동 실행

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

## 4) 배포/설정 기준

- `backend-deploy.yml`은 더 이상 `/etc/withbuddy-monitor.env` 또는 cron을 설치하지 않는다.
- 운영 서버에 `/etc/withbuddy-monitor.env`, `/etc/cron.d/withbuddy-discord-health-alert`를 기본값으로 추가하지 않는다.
- 로컬 스크립트 알림을 다시 활성화하려면, Grafana 표준만으로 요구사항을 충족할 수 없는 근거가 먼저 있어야 한다.

## 5) 수동 fallback 설정 예시 (필요 시)

```bash
cd /home/ubuntu/withbuddy
chmod +x scripts/discord-health-alert.sh
```

임시 env 예시:

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

## 6) 수동 검증

정상:

```bash
set -a
source /tmp/withbuddy-monitor.env
set +a
/home/ubuntu/withbuddy/scripts/discord-health-alert.sh
```

비정상 강제 테스트(잘못된 URL):

```bash
set -a
source /tmp/withbuddy-monitor.env
set +a
HEALTHCHECK_URL=http://127.0.0.1:65535/health \
  /home/ubuntu/withbuddy/scripts/discord-health-alert.sh
```

## 7) 운영 메모

- 표준 운영 기록은 Grafana alert history와 Discord 수신 메시지를 기준으로 남긴다.
- 로컬 스크립트는 상시 cron 등록 대신, Grafana 경로가 불능일 때만 임시 사용한다.
- `DOWN`, `RECOVERY`, `OOM_DETECTED` 이벤트 의미 자체는 스크립트 fallback에서도 동일하다.
