# AI OVERLOADED_529 Grafana/Loki 운영 가이드

Anthropic 등 업스트림 AI API 과부하(`529`, `overloaded_error`)를 `AI_ERROR_METRIC` 로그로 감지하기 위한 운영 쿼리/알림 규칙입니다.

## KPI/OKR & 평가셋

| 항목 | 내용 |
| --- | --- |
| 목표 KPI | `OVERLOADED_529` 발생 후 2분 이내 탐지 알림 도달 |
| OKR 연결 | AI 답변 가용성 안정화(간헐 실패 조기 감지/대응) |
| 평가셋 | 운영 트래픽 48시간, Loki 수집 정상, `withbuddy-backend` 1개 이상 인스턴스 |
| Before/After | Before: `OTHER` 집계로 과부하 원인 분리 어려움 / After: `OVERLOADED_529` 원인 분리 가능 |
| 합격 기준 | 529 과부하 발생 시 경고/치명 알림 정상 발송, 오탐률 5% 이하 |

## 전제 로그 포맷

백엔드 로그 예시:

```text
[AI_ERROR_METRIC] type=OVERLOADED_529, count=12, questionId=751, errorClass=IllegalStateException, message=...
```

## Loki LogQL 쿼리

1. `OVERLOADED_529` 실시간 로그 확인

```logql
{app="withbuddy-backend", env="prod"} |= "[AI_ERROR_METRIC]" |= "type=OVERLOADED_529"
```

2. 1분 단위 `OVERLOADED_529` 건수

```logql
sum(
  count_over_time(
    {app="withbuddy-backend", env="prod"} |= "[AI_ERROR_METRIC]" |= "type=OVERLOADED_529" [1m]
  )
)
```

3. 5분 누적 `OVERLOADED_529` 건수

```logql
sum(
  count_over_time(
    {app="withbuddy-backend", env="prod"} |= "[AI_ERROR_METRIC]" |= "type=OVERLOADED_529" [5m]
  )
)
```

4. 15분 누적 `OVERLOADED_529` 건수(장애 구간 추세)

```logql
sum(
  count_over_time(
    {app="withbuddy-backend", env="prod"} |= "[AI_ERROR_METRIC]" |= "type=OVERLOADED_529" [15m]
  )
)
```

## Grafana 알림 권장안

Alert A (Warning): 5분 내 3건 이상

```logql
sum(count_over_time({app="withbuddy-backend", env="prod"} |= "[AI_ERROR_METRIC]" |= "type=OVERLOADED_529" [5m])) >= 3
```

- `for`: 1m
- 심각도: `warning`
- 목적: 과부하 초입 감지

Alert B (Critical): 5분 내 10건 이상

```logql
sum(count_over_time({app="withbuddy-backend", env="prod"} |= "[AI_ERROR_METRIC]" |= "type=OVERLOADED_529" [5m])) >= 10
```

- `for`: 1m
- 심각도: `critical`
- 목적: 사용자 체감 장애 가능성이 높은 구간 즉시 감지

Alert C (Sustained): 15분 내 20건 이상

```logql
sum(count_over_time({app="withbuddy-backend", env="prod"} |= "[AI_ERROR_METRIC]" |= "type=OVERLOADED_529" [15m])) >= 20
```

- `for`: 2m
- 심각도: `critical`
- 목적: 장시간 과부하 지속 감지

## 운영 체크리스트

1. 로그 라벨(`app`, `env`)이 실제 Loki 라벨과 동일한지 확인
2. Alert A/B/C 임계치가 트래픽 규모 대비 과도하지 않은지 주간 검토
3. `OVERLOADED_529` 알림 발생 시, 동일 시각 `AI_TIMEOUT`, `NETWORK`, `HTTP_500` 동시 발생 여부 비교
4. 장애 종료 후 원인 구분을 위해 `questionId`와 업스트림 `request_id`를 함께 보존

