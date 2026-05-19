# AUDIT_DOWNLOAD Grafana/Loki 운영 가이드

`AUDIT_DOWNLOAD` 구조화 로그를 기준으로 문서 다운로드 경로를 모니터링하는 표준 쿼리/알림 규칙입니다.

## KPI/OKR 정의

| 항목 | 내용 |
| --- | --- |
| 목표 KPI | 다운로드 감사 로그 누락률 0%, URL 발급 성공 이벤트(`DOWNLOAD_URL_ISSUED`) 수집율 100% |
| OKR 연결 | 문서 다운로드 추적 가능성 확보로 보안/감사 대응 리드타임 단축 |
| 평가셋 | 운영 트래픽 기준 48시간, Loki 수집 정상, Backend 인스턴스 1개 이상 |
| Before/After | Before: presigned URL 원문 로그 중심, 감사 필드 집계 어려움 / After: `AUDIT_DOWNLOAD` 키-값 집계 가능 |
| 합격 기준 | 48시간 동안 `AUDIT_DOWNLOAD` 파싱 실패 0건, 대시보드/알림 정상 동작 |

## 로그 포맷

Backend 로그 예시:

```text
AUDIT_DOWNLOAD event=DOWNLOAD_URL_ISSUED route=REDIRECT userId=101 requesterCompanyCode=COMPANY_A documentCompanyCode=COMPANY_A documentId=55 source=PRIMARY ttlSeconds=30 contentLength=- globalAccess=false traceId=...
```

필드:
- `event`: `DOWNLOAD_URL_ISSUED` 또는 `DOWNLOAD_CONTENT_ACCESSED`
- `route`: `REDIRECT` 또는 `DIRECT`
- `userId`: JWT 사용자 ID, API Key 기반 호출은 `SYSTEM`
- `requesterCompanyCode`, `documentCompanyCode`
- `documentId`, `source`, `ttlSeconds`, `contentLength`, `globalAccess`, `traceId`

## Loki LogQL 쿼리

아래에서 라벨 셀렉터는 환경에 맞게 바꿉니다.
- 예시: `{app="withbuddy-backend", env="prod"}`

### 1) 최근 15분 감사 로그 원문

```logql
{app="withbuddy-backend", env="prod"} |= "AUDIT_DOWNLOAD"
```

### 2) URL 발급 이벤트 건수 (1분 버킷)

```logql
sum by (source) (
  count_over_time({app="withbuddy-backend", env="prod"} |= "AUDIT_DOWNLOAD" | logfmt | event="DOWNLOAD_URL_ISSUED" [1m])
)
```

### 3) 실제 다운로드 이벤트 건수 (1분 버킷)

```logql
sum by (source) (
  count_over_time({app="withbuddy-backend", env="prod"} |= "AUDIT_DOWNLOAD" | logfmt | event="DOWNLOAD_CONTENT_ACCESSED" [1m])
)
```

### 4) 회사별 다운로드 TOP N (최근 1시간)

```logql
topk(10,
  sum by (documentCompanyCode) (
    count_over_time({app="withbuddy-backend", env="prod"} |= "AUDIT_DOWNLOAD" | logfmt | event="DOWNLOAD_CONTENT_ACCESSED" [1h])
  )
)
```

### 5) API Key 경로(SYSTEM) 사용량 (최근 15분)

```logql
sum(
  count_over_time({app="withbuddy-backend", env="prod"} |= "AUDIT_DOWNLOAD" | logfmt | userId="SYSTEM" [15m])
)
```

### 6) 파싱 실패 탐지 (포맷 이상)

```logql
sum(
  count_over_time({app="withbuddy-backend", env="prod"} |= "AUDIT_DOWNLOAD" | logfmt | __error__!="" [5m])
)
```

## Grafana 알림 권장안

### Alert A: 감사 로그 파싱 실패
- 조건: 쿼리 6 결과 `> 0` for 5m
- 심각도: warning
- 의미: 로그 포맷 훼손 또는 수집 파이프라인 파싱 오류

### Alert B: 감사 로그 유입 중단
- 조건:
```logql
sum(count_over_time({app="withbuddy-backend", env="prod"} |= "AUDIT_DOWNLOAD" [10m])) == 0
```
- for: 10m
- 심각도: critical
- 의미: 다운로드 트래픽 급감 또는 로그 수집/애플리케이션 장애

### Alert C: API Key 경로 급증
- 조건: 쿼리 5 결과가 평시 기준치 초과(예: `> 50` / 15m)
- 심각도: warning
- 의미: 비정상 자동 호출 또는 내부 연계 이상 가능성

## 대시보드 패널 구성 권장

1. `Downloads Issued per Minute` (쿼리 2, 시계열)
2. `Downloads Accessed per Minute` (쿼리 3, 시계열)
3. `Top Companies (1h)` (쿼리 4, 바 차트)
4. `SYSTEM Access Count (15m)` (쿼리 5, Stat)
5. `Parse Errors (5m)` (쿼리 6, Stat + Alert)

## 운영 체크리스트 (배포 후 48시간)

1. 쿼리 1에서 `AUDIT_DOWNLOAD` 로그가 실시간 유입되는지 확인
2. 쿼리 6 값이 지속적으로 `0`인지 확인
3. 쿼리 2/3 추이가 비정상적으로 벌어지지 않는지 확인
4. Alert A/B/C 테스트 알림 1회 송신 확인

