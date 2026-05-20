# 문서 다운로드 접근 차단 및 감사 로그 정책

SCRUM-366 정책 확정 문서입니다. 삭제·비활성화 문서 접근 차단 정책과 다운로드 감사 로그 정책을 정의합니다.

## 1. 삭제·비활성화 문서 접근 차단 정책

### 정책 요약

| 항목 | 내용 |
| --- | --- |
| 적용 대상 | 다운로드 URL 발급, Redirect 다운로드, Direct 파일 다운로드 진입 경로 |
| 차단 조건 | `documents.is_active = false` |
| 차단 결과 | `404 Not Found` / `NOT_FOUND` |
| 구현 위치 | `DocumentStorageService`의 다운로드 관련 조회 경로 |

### 구현 원칙

- 문서 조회는 `findByIdAndIsActiveTrue`를 사용한다.
- `is_active = false` 문서는 조회 단계에서 제외되어 실제 파일 접근 전 차단된다.
- 클라이언트에는 삭제 여부를 노출하지 않고 동일하게 `NOT_FOUND`를 반환한다.

예시:

```java
documentRepository.findByIdAndIsActiveTrue(documentId)
    .orElseThrow(() -> new StorageException(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."));
```

### 적용 엔드포인트

| 경로 | 역할 | 차단 적용 |
| --- | --- | --- |
| `GET /api/v1/documents/{id}/download` | 다운로드 URL 발급 | ✅ |
| `GET /api/v1/documents/{id}/file?source=...` | 302 Redirect 다운로드 | ✅ |
| `GET /api/v1/documents/{id}/file?source=...` | Direct 바이트 다운로드 | ✅ |

## 2. 감사 로그 정책

### 정책 요약

| 항목 | 내용 |
| --- | --- |
| 로그 키워드 | `AUDIT_DOWNLOAD` |
| 저장 방식 | 애플리케이션 구조화 로그 |
| 수집 경로 | Logback → Loki (Grafana 조회) |
| 기록 시점 | URL 발급 성공, 실제 파일 반환 성공 |
| 보안 원칙 | presigned URL 원문 로그 금지 |

### 저장 필드

| 필드 | 값 예시 | 설명 |
| --- | --- | --- |
| `event` | `DOWNLOAD_URL_ISSUED`, `DOWNLOAD_CONTENT_ACCESSED` | 이벤트 유형 |
| `route` | `REDIRECT`, `DIRECT` | 다운로드 경로 |
| `userId` | `101`, `SYSTEM` | JWT 사용자 ID, API Key 호출은 `SYSTEM` |
| `requesterCompanyCode` | `COMPANY_A`, `GLOBAL` | 요청자 회사 코드 |
| `documentCompanyCode` | `COMPANY_A`, `COMMON` | 문서 회사 코드 |
| `documentId` | `55` | 문서 ID |
| `source` | `PRIMARY`, `BACKUP` | 저장소 소스 |
| `ttlSeconds` | `30`, `-` | Redirect URL 유효시간 |
| `contentLength` | `204800`, `-` | Direct 응답 바이트 길이 |
| `globalAccess` | `true`, `false` | API Key globalAccess 여부 |
| `traceId` | `abc-123`, `-` | MDC traceId |
| `timestamp` | Logback 자동 부여 | Loki 시간 인덱싱 기준 |

로그 예시:

```text
AUDIT_DOWNLOAD event=DOWNLOAD_URL_ISSUED route=REDIRECT userId=101 requesterCompanyCode=COMPANY_A documentCompanyCode=COMPANY_A documentId=55 source=PRIMARY ttlSeconds=30 contentLength=- globalAccess=false traceId=abc-def-123
```

## 3. 의도적 제외 범위

| 항목 | 이유 |
| --- | --- |
| 다운로드 실패 이벤트 별도 감사 레코드 | 예외 경로는 기존 ERROR/WARN 로그로 추적 |
| presigned URL 원문 로그 | 임시 권한 토큰 노출 방지 |
| 별도 감사 DB 테이블 | 현재 범위에서는 Loki 기반 감사로 운영 |

## 4. 운영 KPI 및 검증 기준

| 항목 | 기준 |
| --- | --- |
| 목표 KPI | `AUDIT_DOWNLOAD` 파싱 실패 0건, 유효 이벤트 수집률 100% |
| 평가셋 | 운영 트래픽 기준 48시간, Loki 수집 정상 |
| 합격 기준 | 대시보드/알림 정상 동작, 파싱 실패 알림 0건 |

Grafana/Loki 쿼리와 알림 규칙은 [AUDIT_DOWNLOAD_GRAFANA_LOKI.md](./AUDIT_DOWNLOAD_GRAFANA_LOKI.md)를 따른다.

_정책 확정일: 2026-05-19 / SCRUM-366_

