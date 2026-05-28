# Operations 문서

`docs/operations` 폴더에 포함된 배포 및 운영 문서 목록입니다.

## 문서 목록

- [DEPLOYMENT-ORACLE.md](./DEPLOYMENT-ORACLE.md): Oracle Cloud 배포 가이드
- [FLYWAY_MIGRATION_STABILIZATION_CHECKLIST.md](./FLYWAY_MIGRATION_STABILIZATION_CHECKLIST.md): Flyway 마이그레이션 안정화(자동 repair + 배포 전/후 체크리스트)
- [ai/OVERLOADED_529_GRAFANA_LOKI.md](./ai/OVERLOADED_529_GRAFANA_LOKI.md): AI 529 과부하 Grafana/Loki 쿼리 및 알림 가이드
- [storage/README.md](./storage/README.md): 스토리지 운영 문서 인덱스
- [storage/RUNBOOK.md](./storage/RUNBOOK.md): Storage API 운영 런북
- [storage/OPS_LOG_2026-04-11_OBJECT_STORAGE.md](./storage/OPS_LOG_2026-04-11_OBJECT_STORAGE.md): Object Storage 작업 로그
- [storage/AUDIT_DOWNLOAD_GRAFANA_LOKI.md](./storage/AUDIT_DOWNLOAD_GRAFANA_LOKI.md): 다운로드 감사 로그 Grafana/Loki 쿼리 및 알림 가이드
- [storage/DOWNLOAD_ACCESS_POLICY.md](./storage/DOWNLOAD_ACCESS_POLICY.md): 다운로드 접근 차단 및 감사 로그 정책(SCRUM-366)

## 관리 원칙

1. 운영 절차와 실제 실행 로그는 `operations/` 아래에서만 관리합니다.
2. 날짜 기반 운영 로그가 추가되면 상위 README와 하위 README 목록을 함께 갱신합니다.
