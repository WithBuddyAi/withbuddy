# WithBuddy Docs

WithBuddy 프로젝트 문서를 영역별로 정리한 인덱스입니다. 각 하위 패키지의 README는 해당 폴더에 포함된 문서를 기준으로 관리합니다.

## 문서 구조

- `guides/`: 개발 환경, 협업 규칙, Git/GitHub 운영 가이드
- `api/`: 현재 API, 확장 예정 API, AI/스토리지 연동 명세
- `architecture/`: 서비스 구조, 인프라, 보안, 멀티테넌시, 스토리지 전략
- `data/`: ERD, 마이그레이션, 스토리지 DDL/ERD
- `operations/`: 배포 및 운영 런북, 운영 로그
- `planning/`: 제품 기획 및 PRD

## 빠른 이동

- 시작 가이드: [guides/README.md](./guides/README.md)
- API 문서: [api/README.md](./api/README.md)
- 아키텍처 문서: [architecture/README.md](./architecture/README.md)
- 데이터 문서: [data/README.md](./data/README.md)
- 운영 문서: [operations/README.md](./operations/README.md)
- 기획 문서: [planning/PRD_v4.0.md](./planning/PRD_v4.0.md)

## 문서 관리 원칙

1. 문서 위치가 바뀌면 상위 README의 링크와 설명도 같이 수정합니다.
2. 각 폴더 README는 그 폴더에 실제로 존재하는 문서만 나열합니다.
3. 구현/스키마/운영 변경이 발생하면 관련 상세 문서와 해당 폴더 README를 함께 갱신합니다.
