# WithBuddy Docs

## 문서 구조

- `guides/`: 온보딩, 로컬 개발, 협업 절차
- `api/`: 현재 API, 계획 API, 스토리지 API, AI 연동 API
- `architecture/`: 시스템/인프라/보안/멀티테넌시/스토리지 전략
- `data/`: ERD, 마이그레이션, 스토리지 DDL/ERD
- `operations/`: 배포 및 운영 런북/운영 로그

## 빠른 링크

- 시작: [guides/README.md](./guides/README.md)
- API: [api/README.md](./api/README.md)
- 아키텍처: [architecture/README.md](./architecture/README.md)
- 데이터: [data/README.md](./data/README.md)
- 운영: [operations/README.md](./operations/README.md)

## 문서 운영 원칙

1. `guides`는 "어떻게 작업하는가", `architecture`는 "왜 이렇게 설계했는가"를 다룹니다.
2. API 스펙 변경 시 `api/` 문서를 먼저 업데이트합니다.
3. DB 변경 시 `data/` 문서와 마이그레이션 파일을 함께 업데이트합니다.
4. 운영 절차는 `operations/`에만 기록하고, 설계 문서에는 개요 링크만 남깁니다.
