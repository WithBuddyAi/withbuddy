# Flyway 마이그레이션 안정화 체크리스트

기준일: 2026-05-28  
대상: `.github/workflows/backend-deploy.yml` 배포 파이프라인

## 1) 자동화 범위

- 배포 전 `V19__dedupe_and_add_unique_for_suggestion_messages.sql`의 Flyway checksum을 CI에서 계산
- 배포 전 DB `flyway_schema_history`의 V19 checksum과 비교
- 불일치 시 checksum 자동 보정(`UPDATE flyway_schema_history SET checksum=... WHERE version='19' AND success=1`)
- 기존 자동복구(V7/V8 failed migration 정리) 유지

## 2) 배포 전 체크리스트

- [ ] `backend-deploy.yml`에 `Calculate Flyway checksum for V19 migration` step 존재
- [ ] `Repair Flyway failed migrations` step에서 `FLYWAY_V19_CHECKSUM` env 전달
- [ ] `Repair Flyway failed migrations` step 로그에 아래 중 1개 확인
  - `V19 checksum already aligned.`
  - `V19 checksum auto-repair completed.`
- [ ] `flyway_schema_history` 테이블 존재 확인

## 3) 배포 후 체크리스트

- [ ] 서비스 기동 로그에 `FlywayValidateException` 미발생
- [ ] 헬스체크 `/actuator/health/liveness` 성공
- [ ] Flyway 이력 점검

```sql
SELECT installed_rank, version, description, success, checksum
FROM flyway_schema_history
WHERE version IN ('7','8','19','20')
ORDER BY installed_rank;
```

## 4) 실패 시 수동 복구 기준

- V19 checksum mismatch만 단독 발생:
  - 배포 로그에 출력된 `expected V19 checksum` 값으로 `flyway_schema_history.checksum`을 정합
- V7/V8 실패 레코드 동반:
  - 기존 자동복구 로직(V7/V8 table drop + failed row delete) 우선
- 자동복구 후에도 실패:
  - 배포 중단 후 DB 백업본 확보, 원인 분석 후 재배포

