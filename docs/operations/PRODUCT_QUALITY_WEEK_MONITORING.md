# Product Quality Week 서버 모니터링 및 장애 대응 가이드 (SCRUM-493)

Product Quality Week 기간 동안 위드버디 서버 상태를 모니터링하고, 장애 발생 시 동일한 절차로 감지·복구·기록하기 위한 운영 기준서입니다.

## KPI/OKR & 평가셋

| 항목 | 내용 |
| --- | --- |
| 목표 KPI | DOWN/OOM 발생 후 2분 이내 알림 확인, 서버 DOWN 시 5분 이내 1차 복구 시도 |
| OKR 연결 | QA/데모 중단 시간 최소화, 발표 자료용 "서버 안정성" 근거 데이터 확보 |
| 평가셋 | Product Quality Week 운영 기간, `/actuator/health` 정상 노출, Grafana/Prometheus/blackbox 기반 Discord 알림 채널 활성화, `withbuddy-backend.service` 단일 기동 |
| Before/After | Before: 장애 감지/조치 기준이 이슈 본문 수준에 머묾 / After: 점검 항목, 복구 명령, 기록 양식이 표준화됨 |
| 합격 기준 | 장애/지연 발생 시 감지 시각, 원인, 조치, 복구 시각이 모두 기록되고 핵심 서비스가 정상 복구될 것 |

## 운영 기준

- 백엔드 헬스 엔드포인트: `/actuator/health`
- prod 노출 엔드포인트: `health`, `info`, `prometheus`
- 서비스 기동 표준: `withbuddy-backend.service`
- 환경변수 파일: `/etc/withbuddy-backend.env`
- 모니터링 표준: `Grafana Alerting + Prometheus + blackbox_exporter + Discord`
- 주기 점검 자동화: `.github/workflows/product-quality-monitor.yml`
- 자동 운영 로그: `/home/ubuntu/withbuddy/logs/product-quality-monitor.log`

## 자동 실행 경로

- 장애 감지: `Grafana Alerting -> Discord`
- 주기 상태 점검: GitHub Actions `Product Quality Monitor`가 30분마다 실행
- 자동 점검 항목:
  - `withbuddy-backend.service` 상태
  - `grafana-server`, `prometheus` 상태
  - `BACKEND_HEALTH_URL` 응답
  - `Prometheus /-/healthy`, `/-/ready`
  - 메모리 사용량, 루트 디스크 사용량
- 자동 기록 위치:
  - 서버 로그: `/home/ubuntu/withbuddy/logs/product-quality-monitor.log`
  - GitHub Actions 실행 요약: workflow step summary
- 수동 대응이 필요한 경우:
  - Grafana 알림이 실제로 firing/resolved 되었을 때
  - `Product Quality Monitor` 워크플로가 실패했을 때
  - 장애 원인/조치 내용을 Jira에 남길 때

## 사전 점검 체크리스트

- [ ] `/actuator/health` 응답이 `UP` 인지 확인
- [ ] `sudo systemctl is-active withbuddy-backend.service` 결과가 `active` 인지 확인
- [ ] `grafana-server`, `prometheus` 서비스가 `active` 인지 확인
- [ ] Grafana Discord 알림 채널과 alert history 접근 가능 여부 확인
- [ ] GitHub Actions `Product Quality Monitor` 최근 실행이 성공인지 확인
- [ ] `/home/ubuntu/withbuddy/logs/product-quality-monitor.log`에 최신 점검 로그가 누적되는지 확인
- [ ] 메모리 사용량, 디스크 사용량이 임계치 근처가 아닌지 확인
- [ ] `journalctl -u withbuddy-backend.service` 최근 오류 급증 여부 확인

## 점검 명령

헬스체크:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS https://<API_DOMAIN>/actuator/health
```

서비스 상태:

```bash
sudo systemctl status withbuddy-backend.service --no-pager
sudo systemctl is-active withbuddy-backend.service
sudo systemctl is-active grafana-server
sudo systemctl is-active prometheus
```

로그 확인:

```bash
sudo journalctl -u withbuddy-backend.service -n 200 --no-pager
sudo journalctl -u withbuddy-backend.service --since "10 minutes ago" --no-pager
```

리소스 확인:

```bash
free -m
df -h
top -b -n 1 | head -30
```

모니터링 스택 점검:

```bash
curl -fsS http://127.0.0.1:9090/-/healthy
curl -fsS http://127.0.0.1:9090/-/ready
tail -n 20 /home/ubuntu/withbuddy/logs/product-quality-monitor.log
```

## 장애 유형별 1차 대응

### 1. 서버 DOWN

1. `/actuator/health` 실패 여부 확인
2. `systemctl status`와 최근 로그 확인
3. 아래 명령으로 1차 재기동

```bash
sudo systemctl restart withbuddy-backend.service
sudo systemctl is-active withbuddy-backend.service
curl -fsS http://127.0.0.1:8080/actuator/health
```

4. 복구 후 Jira 이슈와 팀 채널에 장애 시각/복구 시각/원인 후보 공유

### 2. OOM 감지

1. Grafana OOM 관련 알림 또는 `journalctl -k` 커널 로그 확인
2. 메모리 점유 상위 프로세스 확인
3. 백엔드 재기동 후 헬스체크 재검증

```bash
sudo journalctl -k --since "30 minutes ago" --no-pager
free -m
ps aux --sort=-%mem | head
sudo systemctl restart withbuddy-backend.service
```

4. 재발 시점까지의 트래픽/배치/AI 요청 패턴을 별도 메모

### 3. 응답 지연

1. 헬스체크는 `UP` 인데 사용자 응답이 느린지 분리 확인
2. 최근 애플리케이션 로그에서 timeout, DB, Redis, RabbitMQ 오류 탐색
3. CPU/메모리/디스크 wait 여부 확인
4. AI 연동 지연인지, DB 조회 지연인지 원인 범주를 먼저 분류

예시 확인:

```bash
sudo journalctl -u withbuddy-backend.service --since "15 minutes ago" --no-pager
curl -w "@-" -o /dev/null -sS https://<API_DOMAIN>/actuator/health <<'EOF'
time_namelookup:  %{time_namelookup}\n
time_connect:  %{time_connect}\n
time_starttransfer:  %{time_starttransfer}\n
time_total:  %{time_total}\n
EOF
```

## 기록 기준

- 정기 상태 기록 기본값:
  - 서버 로그 `/home/ubuntu/withbuddy/logs/product-quality-monitor.log`
  - GitHub Actions `Product Quality Monitor` 실행 이력
- 수동 Jira 기록이 필요한 경우:
  - 장애가 실제로 발생했을 때
  - 응답 지연 등 원인 분석/조치를 남겨야 할 때
  - 발표/회고용으로 특정 시점의 운영 판단 근거를 남길 때

장애 발생 시 Jira `SCRUM-493` 코멘트에 아래 형식으로 기록합니다.

```text
[Product Quality Week 운영 기록]
- 감지 시각:
- 증상:
- 영향 범위:
- 1차 확인:
- 원인 가설:
- 조치:
- 복구 시각:
- 후속 액션:
```

## 자동 로그 예시

```text
2026-06-16T01:00:00Z | result=ok | backend=active | grafana=active | prometheus=active | backend_health={"status":"UP"} | prom_health=Prometheus Server is Healthy. | prom_ready=Prometheus Server is Ready. | memory=1624MB / 11909MB used | disk=71% used on /dev/sda1
2026-06-16T01:30:00Z | result=failed | backend=active | grafana=active | prometheus=active | backend_health=curl: (28) Operation timed out after 10001 milliseconds with 0 bytes received | prom_health=Prometheus Server is Healthy. | prom_ready=Prometheus Server is Ready. | memory=1688MB / 11909MB used | disk=71% used on /dev/sda1
```

## 완료 판정 기준

- 운영 기간 종료 시 `/home/ubuntu/withbuddy/logs/product-quality-monitor.log` 또는 GitHub Actions 실행 이력에 자동 점검 기록이 누적되어 있을 것
- 장애가 발생했다면 감지부터 복구까지의 시각 정보가 누락 없이 남아 있을 것
- 장애가 없었다면 자동 점검 로그만으로도 "무장애" 근거를 설명할 수 있을 것
