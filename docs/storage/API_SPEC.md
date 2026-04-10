# Storage API 명세 (현재 동작 기준)

> 목적: 실제 백엔드 구현과 동일한 Storage API 계약을 제공한다.

**버전**: 0.6.0
**기준일**: 2026-04-10

---

## 1. 공통 규칙

- Base URL:
  - Local: `http://localhost:{port}` (예: `http://localhost:8082`)
  - Dev/Prod: 배포 도메인 (예: `https://api-wb.itsdev.kr`)
  - Postman 권장 변수: `{{baseUrl}}`
- Base path: `/api/v1/documents`
- 인증:
  - 운영 권장: `X-API-Key: {storageAdminApiKey}`
  - 이행/개발 호환: `Authorization: Bearer {accessToken}` (`STORAGE_API_AUTH_ENABLED=false`일 때만)
- 문서 파일 구분:
  - API Key 모드: 관리자 전역 권한
    - 업로드: `companyCode`로 대상 회사 지정, 미지정 시 공통 문서
    - 조회/삭제: 전체 문서 범위
  - Bearer 모드: JWT `companyCode` 기준
- 관리자 키 확장: `global-access=true`(기본) 권장
- 에러 포맷: `docs/API.md`의 `ErrorResponse`와 동일

인증 모드 매트릭스:
- `STORAGE_API_AUTH_ENABLED=true`:
  - `X-API-Key` 필수
  - Bearer만으로는 Storage API 접근 불가
- `STORAGE_API_AUTH_ENABLED=false`:
  - Storage API가 기존 Bearer 흐름으로 동작(로컬/이행 단계)

파일 제한:
- 확장자: `pdf`, `docx`, `pptx`, `png`, `jpg`, `jpeg`
- 크기: 문서 20MB, 이미지 5MB (환경변수로 조정)

---

## 2. 엔드포인트 요약

### 2.1 업로드

`POST /api/v1/documents/upload` (`multipart/form-data`)

요청 파트:
- `file` (required)
- `title` (required)
- `documentType` (required)
- `department` (required)
- `companyCode` (optional)

`companyCode` 규칙:
- 관리자 키(`global-access=true`): 지정 시 해당 회사 문서, 미지정 시 공통 문서
- `global-access=false`로 내리면 공통 문서 중심으로 제한 동작(운영에서는 비권장)
- 특정 회사 업로드 시 `companyCode`는 DB `companies.company_code`에 존재해야 함

요청 예시(Postman, 공통 문서 업로드):
- Method: `POST`
- URL: `{{baseUrl}}/api/v1/documents/upload`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`
- Body (`form-data`):
  - `file`: `/path/to/common-guide.pdf` (File)
  - `title`: `공통 안내 문서`
  - `documentType`: `LEGAL`
  - `department`: `HR`

요청 예시(Postman, 특정 회사 문서 업로드):
- Method: `POST`
- URL: `{{baseUrl}}/api/v1/documents/upload`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`
- Body (`form-data`):
  - `file`: `/path/to/company-guide.pdf` (File)
  - `title`: `회사 전용 안내 문서`
  - `documentType`: `LEGAL`
  - `department`: `HR`
  - `companyCode`: `WB0001`

응답: `201 Created`

```json
{
  "documentId": 101,
  "title": "복지카드 신청 가이드",
  "fileName": "welfare-guide.pdf",
  "contentType": "application/pdf",
  "fileSize": 2048000,
  "source": "PRIMARY",
  "backupStatus": "FAILED",
  "uploadedAt": "2026-04-10T11:30:00"
}
```

메모:
- 현재 구현은 업로드 요청 안에서 백업까지 시도한다.
- 백업 실패여도 원본 업로드가 성공하면 API는 `201`을 반환하고 `backupStatus`로 상태를 노출한다.

### 2.2 목록

`GET /api/v1/documents?page=0&size=20&documentType=LEGAL&search=근로`

요청 예시(Postman):
- Method: `GET`
- URL: `{{baseUrl}}/api/v1/documents?page=0&size=20`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답: `200 OK` (`DocumentListResponse`)

### 2.3 상세

`GET /api/v1/documents/{documentId}`

요청 예시(Postman):
- Method: `GET`
- URL: `{{baseUrl}}/api/v1/documents/101`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답: `200 OK` (`DocumentDetailResponse`)

주의:
- 일부 시드 문서처럼 `document_files` 메타데이터가 없는 경우 `NOT_FOUND`가 날 수 있다.

### 2.4 다운로드 URL

`GET /api/v1/documents/{documentId}/download`

요청 예시(Postman):
- Method: `GET`
- URL: `{{baseUrl}}/api/v1/documents/101/download`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답: `200 OK`

```json
{
  "downloadUrl": "/api/v1/documents/101/file?source=PRIMARY",
  "expiresIn": 300,
  "source": "PRIMARY"
}
```

### 2.5 파일 직접 다운로드(개발용)

`GET /api/v1/documents/{documentId}/file?source=PRIMARY|BACKUP`

### 2.6 백업 재시도

`POST /api/v1/documents/{documentId}/backup/retry`

요청 예시(Postman):
- Method: `POST`
- URL: `{{baseUrl}}/api/v1/documents/101/backup/retry`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답: `200 OK`

```json
{
  "documentId": 101,
  "backupStatus": "FAILED",
  "backupAttemptCount": 3,
  "backupLastError": "...",
  "backupCompletedAt": null
}
```

### 2.7 삭제 전 검증

`GET /api/v1/documents/{documentId}/delete-check`

요청 예시(Postman):
- Method: `GET`
- URL: `{{baseUrl}}/api/v1/documents/101/delete-check`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답: `200 OK`

```json
{
  "documentId": 101,
  "title": "복지카드 신청 가이드",
  "companyCode": "WB0001",
  "active": true,
  "fileName": "welfare-guide.pdf",
  "fileSize": 2048000,
  "backupStatus": "FAILED",
  "createdAt": "2026-04-10T11:30:00",
  "confirmRequired": true
}
```

### 2.8 단건 삭제 (confirm 필수)

`DELETE /api/v1/documents/{documentId}?confirm=true`

요청 예시(Postman):
- Method: `DELETE`
- URL: `{{baseUrl}}/api/v1/documents/101?confirm=true`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답: `200 OK`

```json
{
  "documentId": 101,
  "message": "문서가 성공적으로 삭제되었습니다."
}
```

### 2.9 선택 삭제 전 검증

`POST /api/v1/documents/bulk-delete/delete-check`

요청 예시(JSON body):

```json
{
  "documentIds": [101, 102, 999999]
}
```

요청 예시(Postman):
- Method: `POST`
- URL: `{{baseUrl}}/api/v1/documents/bulk-delete/delete-check`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`
  - `Content-Type: application/json`
- Body (`raw`, JSON):
```json
{
  "documentIds": [101, 102, 999999]
}
```

응답:

```json
{
  "confirmRequired": true,
  "message": "삭제 가능 2/3건, 삭제 불가 1/3건, 사유: 존재하지 않는 문서: 999999",
  "requestedCount": 3,
  "deletableCount": 2,
  "deletableDocumentIds": [101, 102],
  "notFoundDocumentIds": [999999],
  "forbiddenDocumentIds": []
}
```

### 2.10 선택 삭제 (confirm 필수)

`POST /api/v1/documents/bulk-delete?confirm=true`

요청 예시(JSON body):

```json
{
  "documentIds": [101, 102, 999999]
}
```

요청 예시(Postman):
- Method: `POST`
- URL: `{{baseUrl}}/api/v1/documents/bulk-delete?confirm=true`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`
  - `Content-Type: application/json`
- Body (`raw`, JSON):
```json
{
  "documentIds": [101, 102, 999999]
}
```

응답:

```json
{
  "confirmAccepted": true,
  "message": "완료 2/3건, 실패 1/3건, 실패원인: 존재하지 않는 문서: 999999",
  "requestedCount": 3,
  "deletedCount": 2,
  "deletedDocumentIds": [101, 102],
  "notFoundDocumentIds": [999999],
  "forbiddenDocumentIds": []
}
```

### 2.11 전체 삭제 전 검증

`GET /api/v1/documents/delete-check`

요청 예시(Postman):
- Method: `GET`
- URL: `{{baseUrl}}/api/v1/documents/delete-check`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답:

```json
{
  "confirmRequired": true,
  "message": "삭제 가능 5/5건, 삭제 불가 0/5건, 전체 문서 삭제를 진행하시겠습니까?",
  "requestedCount": 5,
  "deletableCount": 5,
  "deletableDocumentIds": [101, 102, 103, 104, 105],
  "notFoundDocumentIds": [],
  "forbiddenDocumentIds": []
}
```

### 2.12 전체 삭제 (confirm 필수)

`DELETE /api/v1/documents?confirm=true`

요청 예시(Postman):
- Method: `DELETE`
- URL: `{{baseUrl}}/api/v1/documents?confirm=true`
- Headers:
  - `X-API-Key: {{storageAdminApiKey}}`

응답:

```json
{
  "confirmAccepted": true,
  "message": "완료 5/5건, 실패 0/5건",
  "requestedCount": 5,
  "deletedCount": 5,
  "deletedDocumentIds": [101, 102, 103, 104, 105],
  "notFoundDocumentIds": [],
  "forbiddenDocumentIds": []
}
```

---

## 3. 에러 코드

주요 코드:
- `FILE_001`: 파일 크기 제한 초과/빈 파일
- `FILE_002`: 지원하지 않는 파일 형식
- `FILE_003`: 스토리지 처리 실패
- `RESOURCE_004`: 타 회사 문서 접근
- `NOT_FOUND`: 문서/파일 메타데이터 미존재
- `BAD_REQUEST`: confirm 누락 등 입력 검증 실패

---

## 4. 참고

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- 운영 기준/장애 대응: `OPERATIONS_RUNBOOK.md`
