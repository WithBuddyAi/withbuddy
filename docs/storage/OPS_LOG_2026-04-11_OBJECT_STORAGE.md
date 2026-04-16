# Object Storage 점검 로그 (2026-04-11)

## 요약
- 원본 버킷(`bucket-20260409-jw`)은 OCI CLI로 조회/목록 확인 성공
- 백업 버킷(`bucket-20260409-sm`)은 조회 실패
- 백엔드 서버(`withbuddy-vcn`)의 Object Storage 443 이그레스는 Security List 수정으로 복구
- 런타임 `STORAGE_PROVIDER=oci-cli` 전환 후 업로드 실검증 완료(원본 성공, 백업 실패)

## 실행 결과

### 1) 로컬(운영자 PC) 기준
- 성공
  - `oci os ns get --profile DEFAULT` -> `axmnkfv4prr3`
  - `oci os bucket get --profile DEFAULT --namespace-name axmnkfv4prr3 --name bucket-20260409-jw`
  - `oci os object list --profile DEFAULT --namespace-name axmnkfv4prr3 --bucket-name bucket-20260409-jw --all`
    - objects: `6`
    - total bytes: `1218994`
- 실패
  - `oci os bucket get --profile DEFAULT --namespace-name axomfbacqmd4 --name bucket-20260409-sm`
    - `404 BucketNotFound`
  - `oci os bucket get --profile TENANCY_A --namespace-name axomfbacqmd4 --name bucket-20260409-sm`
    - `401 NotAuthenticated`

### 2) 백엔드 서버(withbuddy-vcn) 기준
- 환경값
  - `STORAGE_PROVIDER=oci-cli`
  - `STORAGE_OCI_CLI_EXECUTABLE=/home/ubuntu/.local/bin/oci`
  - `STORAGE_OCI_PRIMARY_PROFILE=DEFAULT`
  - `STORAGE_OCI_BACKUP_PROFILE=DEFAULT`
  - `STORAGE_PRIMARY_NAMESPACE=axmnkfv4prr3`
  - `STORAGE_PRIMARY_BUCKET=bucket-20260409-jw`
  - `STORAGE_BACKUP_NAMESPACE=axomfbacqmd4`
  - `STORAGE_BACKUP_BUCKET=bucket-20260409-sm`
- 조치
  - Security List(`ocid1.securitylist...oyykcq`) egress에 `0.0.0.0/0:443` 규칙 추가
    - description: `HTTPS egress for OCI APIs`
- 성공(조치 후 재검증)
  - `nc -vz -w 5 134.70.112.3 443` -> succeeded
  - `curl -I https://objectstorage.ap-osaka-1.oraclecloud.com` -> HTTP 응답 수신(404는 정상)
  - `oci os ns get --profile DEFAULT` -> `axmnkfv4prr3`
  - `oci os bucket get --profile DEFAULT --namespace-name axmnkfv4prr3 --name bucket-20260409-jw` -> 성공
- 실패(잔여)
  - `oci os bucket get --profile DEFAULT --namespace-name axomfbacqmd4 --name bucket-20260409-sm` -> `404 BucketNotFound`

### 3) 애플리케이션 동작 검증
- 전환 이슈
  - `STORAGE_PROVIDER=oci`로 설정 시 부팅 실패
  - 원인: 코드 조건값이 `oci-cli`여야 `ObjectStorageClient` 빈 생성됨
- 조치
  - `/etc/withbuddy-backend.env`를 `STORAGE_PROVIDER=oci-cli`로 수정 후 재시작
- API 검증
  - `GET /api/v1/documents?page=0&size=1` + `X-API-Key` -> `200`
  - `POST /api/v1/documents/upload`(pdf) -> `201`
    - 응답: `source=PRIMARY`, `backupStatus=FAILED`
- 원본 저장 검증
  - `oci os object list --profile DEFAULT --namespace-name axmnkfv4prr3 --bucket-name bucket-20260409-jw --prefix companies/WB0001/documents/2026-04-11/`
  - 업로드 객체 생성 확인(455 bytes)
- 백업 실패 원인 로그
  - `UPLOAD_SYNC` 백업 처리 실패
  - `BucketNotFound`

## 원인 정리
- (해결) Security List 이그레스 제한으로 OCI API(443) 접속 불가
- 백업 테넌시 프로파일(`TENANCY_A`) 인증 불가(키/권한/프로파일 불일치 가능)
- 백업 버킷의 namespace/bucket 값 불일치 가능
- 현재 앱은 `local` 모드여서 Object Storage 실제 경로가 아님

## 체크리스트 상태
- [x] 원본 버킷 실제 조회 확인
- [x] 원본 오브젝트 목록/용량 확인
- [x] 백업 버킷 조회 실패 코드 수집(401/404)
- [x] 백엔드 서버 기준 연결성 교차 검증
- [x] 백엔드 이그레스(443) 정상화
- [x] `STORAGE_PROVIDER=oci-cli` 전환 및 앱 기동 확인
- [x] 업로드 실검증(원본 저장 성공) 
- [ ] 백업 프로파일(TENANCY_A) 인증 복구
- [ ] 백업 namespace/bucket 실값 확정
- [ ] 백업 업로드 성공(원본/백업 동시 완료) 검증

## 즉시 액션
1. `TENANCY_A` 프로파일 키 재발급 또는 올바른 키로 교체 후 `oci os ns get --profile TENANCY_A` 확인
2. 백업 버킷 실식별자(namespace/bucket) 확인 후 env 재설정
3. `STORAGE_OCI_BACKUP_PROFILE`, `STORAGE_BACKUP_NAMESPACE`, `STORAGE_BACKUP_BUCKET` 최종값 반영 후 재배포
4. 업로드 1건 후 `backupStatus=COMPLETED` 및 백업 버킷 객체 존재 검증
