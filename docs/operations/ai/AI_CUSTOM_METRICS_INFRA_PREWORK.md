# AI Prompt Cache Grafana Prework

## 목적

`SCRUM-563`의 AI 커스텀 메트릭 선행안과 `SCRUM-564`의 Prompt Caching Grafana 패널 요구사항을 하나의 운영 초안으로 정리한다.

이 문서는 다음을 포함한다.

- Prompt Cache 메트릭 계약 초안
- Grafana 패널 초안
- PromQL 초안
- KPI / OKR / 평가셋 / 합격 기준
- 기존 Redis 캐시 레이어 메트릭 연계 기준

## 범위

- 포함:
  - Prompt Cache hit rate 시각화 기준
  - LLM 호출 수 대비 절감 호출 수 시각화 기준
  - Redis fallback / circuit 상태 보조 패널 초안
  - Prometheus 수집 전제 및 검증 절차
- 제외:
  - AI 애플리케이션 코드 계측 구현
  - `company_code` 단위 집계
  - Grafana 서버 실제 import / 배포 수행

## 현재 코드 기준 확인 사항

### 1. Prompt Cache 메트릭 노출 경로

- `backend`의 `InternalCacheApiService`에서 Prompt Cache lookup metric을 직접 노출한다.
- Prometheus metric 이름:
  - `withbuddy_prompt_cache_requests_total{namespace,result,source}`
- `result`:
  - `hit`
  - `miss`
- `source`:
  - `l1`
  - `l2`
  - `l1_negative`
  - `origin`

### 2. Backend 캐시 레이어 보조 지표

- Redis resilience metric은 이미 존재한다.
- 코드 기준 확인된 metric:
  - `cache.redis.requests{op,result}`
  - `cache.redis.fallback{op,result}`
  - `cache.redis.circuit.open`
  - `cache.redis.fallback.tokens`
- Prometheus 노출 시 Micrometer 규칙상 아래 이름으로 조회하는 것을 전제로 한다.
  - `cache_redis_requests_total`
  - `cache_redis_fallback_total`
  - `cache_redis_circuit_open`
  - `cache_redis_fallback_tokens`

### 3. L1 Caffeine 연계 판단

- `internalApiLocalCache`의 hit/miss는 Spring cache binder에 맡기지 않고 애플리케이션 계측으로 직접 집계한다.
- 따라서 `SCRUM-564` 대시보드는:
  - 1차: `withbuddy_prompt_cache_requests_total`을 주 지표로 사용
  - 2차: Redis resilience metric을 보조 지표로 사용

## 제안 메트릭 계약

### 1. Prompt Cache 요청 카운터

```text
withbuddy_prompt_cache_requests_total{namespace="<NAMESPACE>",result="hit|miss",source="l1|l2|l1_negative|origin"}
```

용도:

- Prompt Cache hit rate 추적
- 절감 호출 수 계산

### 2. 보조 Redis 캐시 지표

```text
cache_redis_requests_total{op="get|set|delete|set_multi",result="success|failure|circuit_open"}
cache_redis_fallback_total{op="get|set|delete|set_multi",result="redis_error|circuit_open|rate_limited"}
cache_redis_circuit_open
cache_redis_fallback_tokens
```

용도:

- L2 Redis 레이어 fallback 증가 감지
- circuit open 상태 관찰
- 운영 중 캐시 회복력 저하 조기 탐지

## KPI / OKR / 평가셋

- 목표 KPI:
  - Prompt Cache hit rate 5분 기준 `>= 70%`
  - L1 hit 비율 5분 기준 `>= 40%`
  - Redis fallback rate 5분 기준 `< 3%`
  - Redis circuit open 상태 평균 `0` 유지
- OKR 연결:
  - AI 호출 비용 절감
  - 응답 안정성 유지
  - 캐시 이상 조기 탐지로 운영 장애 대응 시간 단축
- 평가셋:
  - 실제 AI 요청 100건 이상
  - 반복 질의와 비반복 질의 혼합
  - cache hit / miss 사례 포함
  - Redis fallback 유도 또는 운영 로그 기반 샘플 포함
- Before / After:
- Before: Prompt Cache 전용 시계열 대시보드 부재
- After: hit rate / hit source / Redis fallback 패널 확인 가능
- 합격 기준:
  - Grafana draft dashboard import 성공
  - PromQL query 모두 실행 가능
  - hit rate / saved calls / Redis fallback 패널 렌더링 확인

## Grafana 패널 초안

대상 대시보드:

- `WithBuddy AI Prompt Cache Draft`
- `WithBuddy AI Overview`

핵심 패널:

1. `Prompt Cache Hit Rate (5m)`
2. `Prompt Cache Hit Rate by Source`
3. `Prompt Cache Requests Rate`
4. `Redis Cache Fallback Rate (5m)`
5. `Prompt Cache Hit Rate Over Time`
6. `Prompt Cache Requests by Result`
7. `Prompt Cache Requests by Source`
8. `Redis Cache Fallbacks by Op`
9. `Redis Circuit Open`
10. `Redis Fallback Tokens`

## PromQL 초안

### Prompt Cache Hit Rate (5m)

```promql
100 * sum(rate(withbuddy_prompt_cache_requests_total{result="hit"}[5m]))
/ clamp_min(sum(rate(withbuddy_prompt_cache_requests_total[5m])), 0.0001)
```

### Prompt Cache Requests Rate

```promql
sum(rate(withbuddy_prompt_cache_requests_total[5m]))
```

### Prompt Cache Hit Rate by Source

```promql
100 * sum(rate(withbuddy_prompt_cache_requests_total{result="hit"}[5m])) by (source)
/ clamp_min(sum(rate(withbuddy_prompt_cache_requests_total[5m])), 0.0001)
```

### Prompt Cache Requests by Result

```promql
sum(rate(withbuddy_prompt_cache_requests_total[5m])) by (result, source)
```

### Redis Cache Fallback Rate (5m)

```promql
100 * sum(rate(cache_redis_fallback_total[5m]))
/ clamp_min(sum(rate(cache_redis_requests_total[5m])), 0.0001)
```

### Redis Cache Fallbacks by Op

```promql
sum(rate(cache_redis_fallback_total[5m])) by (op, result)
```

### Redis Circuit Open

```promql
max(cache_redis_circuit_open)
```

### Redis Fallback Tokens

```promql
max(cache_redis_fallback_tokens)
```

## 검증 절차

1. Grafana draft JSON lint 확인
2. Prometheus에서 메트릭 존재 여부 확인

```powershell
curl "http://127.0.0.1:9090/api/v1/label/__name__/values"
```

3. Prompt Cache hit rate query 확인

```powershell
curl "http://127.0.0.1:9090/api/v1/query?query=100%20*%20sum(rate(withbuddy_prompt_cache_requests_total%7Bresult%3D%22hit%22%7D%5B5m%5D))%20%2F%20clamp_min(sum(rate(withbuddy_prompt_cache_requests_total%5B5m%5D))%2C%200.0001)"
```

4. Redis fallback rate query 확인

```powershell
curl "http://127.0.0.1:9090/api/v1/query?query=100%20*%20sum(rate(cache_redis_fallback_total%5B5m%5D))%20%2F%20clamp_min(sum(rate(cache_redis_requests_total%5B5m%5D))%2C%200.0001)"
```

5. Grafana에서 아래 패널 렌더링 확인
  - Prompt Cache Hit Rate
  - Prompt Cache Hit Rate by Source
  - Redis Cache Fallback Rate

## 비고

- `company_code`는 Prometheus label에 넣지 않는다.
- Prompt Cache metric 명칭이 구현 단계에서 달라지면 Grafana JSON과 본 문서를 함께 갱신한다.
- Redis fallback metric은 이미 백엔드에 존재하므로 운영 보조 패널로 즉시 활용 가능하다.
