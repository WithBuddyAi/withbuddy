# WithBuddy OCI 이중 Object Storage 전략

> 두 개의 OCI tenancy Object Storage를 WithBuddy 문서 스토리지로 병렬 사용하는 설계 문서

**최종 업데이트**: 2026-05-11
**버전**: 0.63.1

---

## 문서 목적

이 문서는 WithBuddy에서 서로 다른 두 OCI tenancy의 Object Storage를 함께 사용할 때의 운영 기준을 정의한다.

- 업로드 경로를 어떻게 설계할지
- 조회와 다운로드를 어떻게 처리할지
- 백업과 복구를 어떤 기준으로 운영할지
- 현재 `backend/sql/schema.sql` 기준에서 어떤 DB 확장이 필요한지

이 문서는 `docs/PLANNED_API.md`의 문서 업로드/다운로드 초안을 실제 구현 가능한 형태로 구체화한 설계서다.

---

## 1. 전제

### 1.1 현재 WithBuddy 상태

현재 `documents` 테이블은 파일 본문형 문서만 저장한다.

```sql
CREATE TABLE `documents` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20) NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    department VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
)
```

즉 현재는 다음이 없다.

- 실제 파일 저장 위치
- Object Storage provider 구분값
- bucket / namespace / object key
- 파일 크기 / MIME type / checksum
- 백업 상태

따라서 OCI Object Storage 연동을 붙이려면 DB 메타데이터 확장이 먼저 필요하다.

### 1.2 목표

WithBuddy는 두 개의 OCI tenancy를 다음처럼 사용한다.

- `storage-primary`: 주 저장소
- `storage-backup`: 백업 저장소

핵심 원칙은 다음과 같다.

- 업로드는 항상 애플리케이션 서버를 통해 제어한다.
- 파일 메타데이터는 DB가 진실의 원천이다.
- 실제 조회 URL은 DB에 고정 저장하지 않고 요청 시 생성한다.
- 두 tenancy는 하나의 논리 스토리지처럼 합치지 않고, 앱이 두 스토리지를 명시적으로 제어한다.

---

## 2. 권장 저장소 역할 분리

가장 현실적인 운영안은 다음이다.

### 권장안

- Tenancy A: `primary` 버킷
- Tenancy B: `backup` 버킷

### 이유

- 업로드 경로가 단순하다.
- 읽기 기준이 명확하다.
- 장애 시 복구 판단이 쉽다.
- 20GB + 20GB를 하나처럼 쓰는 착시를 줄이고 운영 실수를 막는다.

### 비권장안

- 용량이 찰 때마다 A/B를 번갈아 업로드
- 파일 종류별로 즉흥 분산
- 메타데이터 없이 URL만 저장

이런 방식은 조회 경로, 권한, 정합성, 복구 자동화가 모두 복잡해진다.

---

## 3. 저장 대상 분류

WithBuddy 기준 저장 대상은 최소 4종류로 나눠야 한다.

1. 회사 문서 업로드 파일
- 정책 PDF, 가이드 문서, 온보딩 자료 첨부

2. 회사 설정 이미지
- 로고, 배너, 기타 브랜딩 자산

3. 사용자 프로필 이미지
- 향후 `profileImage` 실제 업로드 지원 시 대상

4. 생성 산출물
- AI 생성 리포트 PDF, 압축본, 내보내기 파일

각 유형은 동일 버킷을 써도 되지만 object key prefix는 분리해야 한다.

예시:

```text
companies/{companyCode}/documents/{documentId}/{uuid}-{filename}
companies/{companyCode}/logos/{uuid}-{filename}
companies/{companyCode}/users/{userId}/profile/{uuid}-{filename}
companies/{companyCode}/reports/{reportId}/{uuid}.pdf
```

---

## 4. DB 확장안

현재 `documents`는 파일 메타를 담기 어렵다. 최소 확장안은 다음이다.

### 4.1 documents 테이블 확장

```sql
ALTER TABLE documents
    ADD COLUMN storage_provider VARCHAR(20) NULL,
    ADD COLUMN storage_tier VARCHAR(20) NULL,
    ADD COLUMN storage_namespace VARCHAR(120) NULL,
    ADD COLUMN storage_bucket VARCHAR(120) NULL,
    ADD COLUMN object_key VARCHAR(500) NULL,
    ADD COLUMN original_file_name VARCHAR(255) NULL,
    ADD COLUMN content_type VARCHAR(120) NULL,
    ADD COLUMN file_size BIGINT NULL,
    ADD COLUMN checksum_sha256 CHAR(64) NULL,
    ADD COLUMN download_path VARCHAR(255) NULL,
    ADD COLUMN backup_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN backup_bucket VARCHAR(120) NULL,
    ADD COLUMN backup_object_key VARCHAR(500) NULL,
    ADD COLUMN backup_completed_at DATETIME NULL;
```

### 4.2 컬럼 의미

- `storage_provider`: `OCI`
- `storage_tier`: `PRIMARY`, `BACKUP`
- `storage_namespace`: 업로드된 tenancy의 namespace
- `storage_bucket`: 원본 저장 버킷
- `object_key`: 원본 object key
- `original_file_name`: 사용자 업로드 파일명
- `content_type`: MIME type
- `file_size`: 바이트 단위 크기
- `checksum_sha256`: 업로드 무결성 확인용
- `download_path`: API 내부 논리 경로. 외부 URL 고정 저장 대신 사용
- `backup_status`: `PENDING`, `COMPLETED`, `FAILED`
- `backup_bucket`: 백업 버킷
- `backup_object_key`: 백업 object key

### 4.3 더 나은 구조

장기적으로는 `documents`와 실제 파일 메타를 분리하는 것이 좋다.

- `documents`: 문서 도메인 정보
- `document_files`: 파일 저장 메타데이터

하지만 현재는 빠르게 붙여야 하므로 `documents` 확장안이 현실적이다.

---

## 5. 업로드 전략

### 5.1 기본 전략

업로드는 항상 `primary` tenancy로 먼저 저장한다.

흐름:

1. 클라이언트가 `multipart/form-data`로 업로드 요청
2. 백엔드가 권한, 회사코드, 파일 형식, 크기 제한 검증
3. 백엔드가 OCI Primary bucket에 업로드
4. 업로드 성공 시 DB에 원본 메타 저장
5. 백업 작업을 비동기 큐 또는 후행 작업으로 등록
6. 백업 성공 시 `backup_status=COMPLETED`

### 5.2 업로드 API 권장 흐름

`docs/PLANNED_API.md`의 `POST /api/v1/documents/upload`를 다음처럼 구체화한다.

```text
POST /api/v1/documents/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

처리 규칙:

- 권한: `HR`, `ADMIN`
- 회사 경계: JWT의 `companyCode`와 요청 데이터가 일치해야 함
- 허용 형식: PDF, DOCX, PPTX, PNG, JPG
- 최대 크기: 문서 20MB, 이미지 5MB
- 파일명 정규화: 원본 파일명은 저장하되 object key에는 UUID 기반 이름 사용

### 5.3 업로드 성공 응답

응답에는 외부 스토리지 URL 대신 파일 식별 정보만 내려주는 것이 안전하다.

```json
{
  "id": "doc-001",
  "title": "신규 문서",
  "fileName": "onboarding-guide.pdf",
  "fileSize": 2048000,
  "contentType": "application/pdf",
  "storageTier": "PRIMARY",
  "backupStatus": "PENDING",
  "uploadedAt": "2026-04-08T12:00:00Z"
}
```

### 5.4 업로드 실패 기준

- Primary 업로드 실패: DB 저장하지 않고 요청 실패
- Primary 성공, DB 실패: 즉시 object 삭제 보상 처리
- Primary 성공, Backup 실패: 요청은 성공 처리 가능, 단 `backup_status=FAILED`로 남기고 재시도 대상에 넣음

### 5.5 왜 동시 이중 업로드를 기본값으로 두지 않는가

업로드 요청 안에서 Primary와 Backup을 모두 동기 처리하면 다음 문제가 생긴다.

- 응답 지연 증가
- 한쪽 일시 장애가 전체 업로드 실패로 번짐
- 재시도 로직 복잡화

따라서 기본 전략은 다음이 맞다.

- 쓰기 기준점은 Primary
- Backup은 비동기 복제

---

## 6. 조회 전략

### 6.1 목록/상세 조회

문서 목록과 상세 조회는 스토리지 URL 자체를 기본 응답으로 내리지 않는다.

대신 다음 메타만 응답한다.

- 문서 id
- 제목
- 파일명
- 파일 크기
- content type
- 업로드 시각
- 다운로드 가능 여부

이유:

- presigned URL은 만료 시간이 짧다.
- URL을 캐시/저장하면 보안과 운영이 꼬인다.
- tenancy 전환 시 기존 URL 호환성이 깨진다.

### 6.2 다운로드 전략

다운로드는 별도 API에서 매번 presigned URL을 생성한다.

권장 엔드포인트:

```text
GET /api/v1/documents/{documentId}/download
```

응답 예시:

```json
{
  "downloadUrl": "https://objectstorage.../p/....",
  "expiresIn": 300,
  "source": "PRIMARY"
}
```

### 6.3 다운로드 우선순위

조회 시도 순서는 다음이 적절하다.

1. Primary object 존재 확인
2. 존재하면 Primary presigned URL 반환
3. Primary 없고 backup 존재 시 Backup presigned URL 반환
4. 둘 다 없으면 404 또는 복구 가능 상태 코드 반환

### 6.4 왜 파일 공개 URL을 직접 저장하면 안 되는가

- tenancy 변경 시 URL 재작성 필요
- bucket 정책 변경에 취약
- private bucket 운영이 어려워짐
- 만료 URL이 DB에 남는 문제 발생

DB에는 `bucket + object_key + namespace`만 저장하는 것이 맞다.

---

## 7. 백업 전략

### 7.1 기본 백업 정책

원본은 Tenancy A Primary bucket에 저장하고, 백업본은 Tenancy B Backup bucket에 복제한다.

정책:

- 신규 업로드 후 비동기 복제
- 주기적 검증 작업으로 누락 백업 재처리
- 백업본은 외부 공개 금지
- 백업본은 평시 조회에 사용하지 않음

### 7.2 백업 실행 방식

권장 순서:

1. 업로드 완료 이벤트 발행
2. 백업 워커가 원본 object metadata 조회
3. 스트림 복사 또는 서버측 복사 수행
4. checksum 검증
5. DB `backup_status` 갱신

가능하면 애플리케이션 레벨 복제를 먼저 구현하고, 안정화 후 OCI 간 직접 복사 자동화를 검토한다.

### 7.3 주기 점검 잡

백업 점검 스케줄 예시:

- 매 10분: `backup_status=PENDING/FAILED` 재시도
- 매일 01:00: 최근 24시간 업로드 파일 checksum 검증
- 매주 1회: Primary/Backup object count 샘플 대조

### 7.4 삭제 정책

문서 삭제는 즉시 hard delete보다 soft delete 우선이 맞다.

권장 순서:

1. DB에서 `is_active=false`
2. 사용자 조회 경로에서 제외
3. 유예 기간 7일 또는 30일 후 실제 object 삭제
4. Primary 삭제 후 Backup 삭제
5. 감사 로그 기록

즉시 완전 삭제가 필요한 경우에도 다음 순서를 유지한다.

- DB 삭제 전에 object 삭제 상태 확인
- 하나만 삭제된 경우 재시도 큐 적재

---

## 8. 복구 전략

### 8.1 Primary 장애 시

Primary에서 object를 읽지 못하면 Backup으로 failover 조회한다.

처리 규칙:

- 사용자 요청 시 Backup presigned URL 발급 허용
- 동시에 운영 알림 발생
- 백그라운드에서 Primary 복원 작업 등록

### 8.2 복원 절차

1. DB에서 손실된 문서 메타 조회
2. Backup bucket object 존재 확인
3. Backup object를 Primary bucket으로 재업로드
4. checksum 비교
5. 복원 완료 로그 기록

### 8.3 복구 범위

복구 대상:

- 개별 문서 손상
- 버킷 오브젝트 일부 유실
- tenancy A 측 권한/정책 실수

복구 불가 가능성:

- DB 메타와 Backup object가 모두 유실된 경우
- 잘못된 object key 저장 후 장기간 방치한 경우

따라서 DB 백업도 별도로 필요하다.

---

## 9. 애플리케이션 구성 권장안

### 9.0 Redis / RabbitMQ와의 관계

Redis와 RabbitMQ는 Object Storage의 대체 저장소가 아니다.

- Object Storage: 파일 원본 저장
- MySQL: 파일 메타데이터와 권한 기준 저장
- Redis: presigned URL 캐시, 업로드 상태 캐시, 짧은 TTL 보조 캐시
- RabbitMQ: 백업 복제, 후처리, 재시도, 장애 복구 작업 전달

즉 이 프로젝트에서 Redis는 캐시 계층이고, RabbitMQ는 비동기 작업 계층이다.  
파일 자체의 영속 저장소 역할은 OCI Object Storage가 맡는다.

### 9.1 구성 요소

백엔드에는 최소 다음 컴포넌트가 필요하다.

- `PrimaryObjectStorageClient`
- `BackupObjectStorageClient`
- `ObjectStorageService`
- `DocumentUploadService`
- `DocumentDownloadService`
- `DocumentBackupWorker`

### 9.2 역할 분리

- `ObjectStorageService`
  - put object
  - head object
  - create presigned url
  - delete object

- `DocumentUploadService`
  - 권한 검증
  - 파일 검증
  - primary 업로드
  - DB 저장
  - 백업 작업 발행

- `DocumentDownloadService`
  - 문서 소유 회사 검증
  - primary 우선 presign
  - 필요 시 backup fallback

- `DocumentBackupWorker`
  - 비동기 복제
  - 실패 재시도
  - checksum 검증

### 9.3 설정 예시

```yaml
app:
  storage:
    oci:
      primary:
        enabled: true
        region: ap-osaka-1
        namespace: ${OCI_PRIMARY_NAMESPACE}
        bucket: ${OCI_PRIMARY_BUCKET}
        config-profile: ${OCI_PRIMARY_PROFILE}
      backup:
        enabled: true
        region: ap-osaka-1
        namespace: ${OCI_BACKUP_NAMESPACE}
        bucket: ${OCI_BACKUP_BUCKET}
        config-profile: ${OCI_BACKUP_PROFILE}
      document:
        max-size-mb: 20
        image-max-size-mb: 5
        presign-expire-seconds: 300
```

### 9.4 파일 키 생성 규칙

object key는 사람이 입력한 이름에 의존하지 않는다.

예시:

```text
companies/WB-0001/documents/123/20260408T101530Z-1f3a9f6d.pdf
```

규칙:

- 회사 코드 prefix 필수
- 도메인 종류 prefix 필수
- 문서 id 또는 사용자 id 포함
- 랜덤 UUID 또는 난수 suffix 포함

---

## 10. 보안 기준

### 10.1 버킷 공개 정책

- Primary bucket: private
- Backup bucket: private
- 공개 읽기 금지

### 10.2 다운로드 방식

- presigned URL 만 허용
- 만료 시간 5분 권장
- 민감 문서는 1분 또는 단건 토큰 다운로드 고려

### 10.3 검증 규칙

- content type whitelist
- 파일 확장자 whitelist
- 업로드 직후 checksum 계산
- 실행 파일 형식 차단

### 10.4 멀티 테넌시 보안

`company_code`가 다른 문서에 대한 presigned URL 발급은 절대 금지한다.

반드시 다음 순서를 따른다.

1. JWT에서 사용자 회사 확인
2. 문서의 `company_code` 확인
3. 일치할 때만 다운로드 URL 발급

---

## 11. API 반영안

`docs/PLANNED_API.md` 기준으로는 다음 보정이 필요하다.

### 문서 업로드 응답 보정

기존:

- `fileUrl` 직접 반환

변경 권장:

- `fileUrl` 제거
- `downloadAvailable` 또는 `downloadEndpoint` 제공

예시:

```json
{
  "id": "doc-new",
  "title": "신규 문서",
  "fileName": "company-policy.pdf",
  "fileSize": 2048000,
  "contentType": "application/pdf",
  "downloadEndpoint": "/api/v1/documents/doc-new/download",
  "backupStatus": "PENDING",
  "uploadedAt": "2026-04-08T11:00:00Z"
}
```

### 문서 상세 조회 보정

기존 `fileUrl`은 고정 URL보다 다음 형태가 안전하다.

```json
{
  "id": "doc-001",
  "title": "복지카드 신청 가이드",
  "category": "HR",
  "content": "1. 복지카드란? ...",
  "file": {
    "name": "guide.pdf",
    "size": 1024000,
    "contentType": "application/pdf",
    "downloadEndpoint": "/api/v1/documents/doc-001/download"
  }
}
```

---

## 12. 구현 순서

1. DB 메타데이터 컬럼 추가
2. OCI Primary/Backup 설정 분리
3. 문서 업로드 API 구현
4. presigned 다운로드 API 구현
5. 비동기 백업 워커 구현
6. 백업 재시도 스케줄러 구현
7. 삭제/복구 운영 절차 추가
8. 모니터링 및 경보 연결

---

## 13. 최종 권장안 요약

WithBuddy에서는 두 OCI Object Storage를 다음처럼 쓰는 것이 가장 안정적이다.

- Tenancy A Object Storage = 원본 저장소
- Tenancy B Object Storage = 백업 저장소
- 업로드는 항상 A로 먼저 저장
- 조회는 A 우선, 필요 시 B fallback
- 백업은 요청 흐름과 분리된 비동기 복제
- DB에는 URL이 아니라 object 메타데이터 저장
- presigned URL은 다운로드 시점에만 생성

즉, 두 스토리지를 "합쳐서 40GB 하나처럼" 쓰는 방식이 아니라, "원본 + 백업" 또는 "주 저장소 + 장애 대체 저장소"로 쓰는 것이 이 프로젝트에 맞다.

---

## 변경 이력

### v1.0.1 (2026-04-09)
- Redis와 RabbitMQ는 Object Storage의 대체 저장소가 아니라 보조 계층임을 명시

### v1.0.0 (2026-04-08)
- WithBuddy용 OCI 이중 Object Storage 업로드/조회/백업 전략 초안 작성
- 현재 `documents` 스키마 기준 최소 확장안 정의
- `PLANNED_API.md`의 문서 업로드/다운로드 초안을 구현 지향 설계로 구체화
