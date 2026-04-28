# Data 문서

DB 스키마, 마이그레이션, ERD를 관리하는 문서 영역입니다.

- [ERD.md](./ERD.md): 서비스 핵심 ERD 설명
- [ERD.png](./ERD.png): ERD 이미지
- [MIGRATION.md](./MIGRATION.md): Flyway 마이그레이션 가이드
- [storage/DB_DDL.sql](./storage/DB_DDL.sql): 스토리지 확장 DDL
- [storage/STORAGE_ERD.md](./storage/STORAGE_ERD.md): 스토리지 전용 ERD

## 운영 규칙

1. SQL 변경은 마이그레이션 파일 + 본 문서 동시 반영이 원칙입니다.
2. `ERD.md`는 개념/관계를, DDL은 실제 스키마를 다룹니다.
