# Storage 문서 세트

WithBuddy의 OCI Object Storage(원본/백업) 설계와 구현 기준을 모아둔 폴더입니다.

- [API_SPEC.md](./API_SPEC.md): 현재 API 규약(`/api/v1`, JWT, 에러 포맷)과 호환되는 스토리지 API 명세
- [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md): 운영 점검/장애 대응/배포 체크리스트 + 테스트 절차 + 백업 전략
- [DB_DDL.sql](./DB_DDL.sql): 스토리지 메타데이터/백업 작업 추적용 MySQL DDL
- [ERD.md](./ERD.md): 스토리지 메타데이터 중심 ERD
- [OPS_LOG_2026-04-11_OBJECT_STORAGE.md](./OPS_LOG_2026-04-11_OBJECT_STORAGE.md): 원본/백업 연결 실검증 결과 및 차단 이슈 기록

## 범위

- Primary: TenantA Object Storage (`source=PRIMARY`)
- Backup: TenantB Object Storage (`source=BACKUP`)
- 대상 도메인: `documents` 업로드/다운로드/백업/복구

## 호환 기준

- API Prefix: `/api/v1`
- 인증:
  - 운영 권장: `X-API-Key: {storageAdminApiKey}`
  - 호환: `Authorization: Bearer {accessToken}` (`STORAGE_API_AUTH_ENABLED=false`일 때)
- 에러 형식: `docs/API.md`의 `ErrorResponse`(`timestamp`, `status`, `error`, `code`, `errors`, `path`)
- 문서 파일 구분:
  - API Key 모드: 업로드 요청의 `companyCode`로 구분(미지정 시 공통)
  - Bearer 모드: JWT의 `companyCode` 기준

## 빠른 시작

1. 인증 모드 결정
  - 운영: `STORAGE_API_AUTH_ENABLED=true` + `X-API-Key`
  - 로컬 이행: `STORAGE_API_AUTH_ENABLED=false` + Bearer
2. Postman 변수 설정
  - `baseUrl`
  - `storageAdminApiKey`
3. 권장 테스트 순서
  - 업로드 -> 목록/상세 -> delete-check -> 삭제(confirm) -> 다운로드

## GitHub Secrets (배포)

`backend-deploy.yml` 기준 필수:
- `STORAGE_API_AUTH_ENABLED`
- `STORAGE_API_KEY_VALUE`
- `STORAGE_API_GLOBAL_ACCESS`
- `STORAGE_API_KEY_ACTIVE`
- `STORAGE_API_KEY_ID` (선택, 미설정 시 `storage-admin`)
- `SPRING_SQL_INIT_MODE` (선택, 미설정 시 `always`; 필요 시 `never`)
