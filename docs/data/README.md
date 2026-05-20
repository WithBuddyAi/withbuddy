# Data 문서

`docs/data` 폴더에 포함된 데이터 모델 문서 목록입니다.

## 문서 목록

- [ERD.md](./ERD.md): MVP 기준 핵심 ERD 설명
- [ERD.png](ERD.png): ERD 이미지
- [MIGRATION.md](./MIGRATION.md): Flyway 마이그레이션 가이드
- [storage/README.md](./storage/README.md): 스토리지 전용 데이터 문서 인덱스
- [storage/DB_DDL.sql](./storage/DB_DDL.sql): 스토리지 관련 DDL
- [storage/STORAGE_ERD.md](./storage/STORAGE_ERD.md): 스토리지 전용 ERD 설명

## 관리 원칙

1. 스키마 변경 시 SQL, ERD, 마이그레이션 문서를 함께 갱신합니다.
2. 공통 데이터 모델은 `ERD.md`, 스토리지 전용 모델은 `storage/` 문서에서 관리합니다.
