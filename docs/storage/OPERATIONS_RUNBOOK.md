# Storage 운영 Runbook

> 대상: Storage API 운영/장애 대응 담당자

**버전**: 0.6.0
**기준일**: 2026-04-10

---

## 1. 운영 목표

- 업로드 API 정상 처리
- `document_files` 메타데이터 일관성 유지
- 백업 실패의 빠른 감지 및 재시도
- Storage API 관리자 접근 통제(`X-API-Key`)
- 삭제 작업의 안전한 수행(confirm 기반)

---

## 2. 일일 점검

1. 애플리케이션 헬스 확인
  - `/actuator/health`
2. 백업 실패 건 확인
  - `document_files.backup_status='FAILED'`
3. 재시도 누적 건 확인
  - `backup_attempt_count >= maxAttempts`
4. 최근 삭제 작업 확인
  - 단건/선택삭제 호출 로그

권장 SQL:

```sql
SELECT id, document_id, backup_status, backup_attempt_count, backup_last_error
FROM document_files
WHERE deleted_at IS NULL
  AND backup_status = 'FAILED'
ORDER BY updated_at DESC
LIMIT 50;
```

---

## 3. 장애 패턴별 대응

### 3.1 Storage API 401 Unauthorized

증상:
- Storage API 호출 시 `UNAUTHORIZED`
- 메시지: `X-API-Key 헤더가 필요합니다.` 또는 `유효하지 않은 API Key 입니다.`

조치:
1. 헤더 확인
  - `X-API-Key: {STORAGE_API_KEY_VALUE}`
2. 서버 환경변수 확인
  - `STORAGE_API_AUTH_ENABLED=true`
  - `STORAGE_API_KEY_VALUE` 설정
  - `STORAGE_API_GLOBAL_ACCESS=true` 권장(기본)
3. 이행 모드 여부 확인
  - Bearer로 테스트하려면 `STORAGE_API_AUTH_ENABLED=false` 필요

### 3.2 백업 401 NotAuthenticated

증상:
- 백업 로그에 `NotAuthenticated`
- `backupStatus=FAILED` 지속

조치:
1. `~/.oci/config` 프로파일 확인 (`STORAGE_OCI_BACKUP_PROFILE`)
2. 키 파일 경로/권한 확인
3. CLI 직접 점검
  - `oci os ns get --profile {backup-profile}`
4. 복구 후 수동 재시도
  - `POST /api/v1/documents/{id}/backup/retry`

### 3.3 삭제 API 400(confirm)

증상:
- `삭제를 진행하시겠습니까` (문서 삭제는 confirm=true가 필요하다.)

조치:
- 클라이언트 요청에 `?confirm=true` 포함

### 3.4 목록은 보이는데 상세 404

증상:
- 목록 응답에는 문서가 있고 상세는 `문서 파일 메타데이터를 찾을 수 없습니다.`

조치:
1. 시드 문서/legacy 데이터 여부 확인
2. 운영 정책 선택
  - 시드 문서 정리
  - 또는 메타데이터 보강

---

## 4. 삭제 운영 정책

- 단건 삭제:
  - `{id}/delete-check`로 사전 검증
  - `{id}/confirm=true`로 최종 실행
- 선택 삭제:
  - `bulk-delete/delete-check`로 사전 검증
  - `bulk-delete?confirm=true`로 최종 실행
  - 응답의 `notFoundDocumentIds`, `forbiddenDocumentIds`를 후속 조치 큐로 분리
- 전체 삭제:
  - `GET /api/v1/documents/delete-check`로 사전 검증
  - `DELETE /api/v1/documents?confirm=true`로 최종 실행

권장 절차:
1. 대상 ID 확정
2. 검증 API 호출
3. 삭제 실행
4. 결과 JSON 보관(감사 로그)

---

## 5. 배포 전/후 체크리스트

배포 전:
- [ ] 로컬 Swagger에서 신규 API 확인
- [ ] 업로드/삭제/선택삭제 회귀 완료
- [ ] `API_SPEC.md` 반영

배포 후:
- [ ] 배포 환경 `/v3/api-docs` 확인
- [ ] 스모크 테스트
  - 로그인
  - 업로드
  - delete-check
  - 단건 삭제(confirm)
  - 선택삭제(delete-check + confirm)
  - 전체삭제(delete-check + confirm)

---

## 6. 테스트 절차 (통합)

인증 모드:
- 운영 모드: `STORAGE_API_AUTH_ENABLED=true` -> `X-API-Key` 필수
- 이행/로컬 모드: `STORAGE_API_AUTH_ENABLED=false` -> Bearer 사용 가능

권장 회귀 순서:
1. 업로드
2. 목록/상세
3. 단건 `delete-check` + 단건 삭제(`confirm=true`)
4. 선택 `delete-check` + 선택 삭제(`confirm=true`)
5. 전체 `delete-check` + 전체 삭제(`confirm=true`)
6. 백업 재시도

---

## 7. 백업 전략 요약

역할 분리:
- Primary(TenantA): 실서비스 업로드/다운로드 기준
- Backup(TenantB): 복구용 복제본

처리 흐름:
1. Primary 업로드
2. `document_files` 메타데이터 저장(`backup_status=PENDING`)
3. 백업 복제 시도 후 상태 갱신(`COMPLETED`/`FAILED`)

운영 목표:
- RPO(Recovery Point Objective): 15분 이내
- RTO(Recovery Time Objective): 60분 이내

다운로드 우선순위:
1. Primary 확인
2. 미존재 시 Backup fallback
3. 둘 다 없으면 `404`

---

## 8. 운영 알림 조건(권장)

- `backup_status='FAILED'` 10건 이상 연속 발생
- `NotAuthenticated` 3회 이상 연속
- 삭제 API `400(confirm)` 과다 발생(클라이언트 구현 누락 신호)

---

## 9. 변경 관리

- Storage API/정책 변경 시 필수 동기화:
  - `docs/storage/API_SPEC.md`
  - `docs/storage/OPERATIONS_RUNBOOK.md`
  - `docs/storage/ERD.md`
  - `docs/storage/DB_DDL.sql`
