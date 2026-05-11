# API 문서

`docs/api` 폴더에 포함된 API 명세 문서 목록입니다.

## 문서 목록

- [API_CURRENT.md](./API_CURRENT.md): 현재 구현 기준의 MVP REST API 명세
- [API_PLANNED.md](./API_PLANNED.md): MVP 이후 확장 예정 API 초안
- [STORAGE_API_SPEC.md](./STORAGE_API_SPEC.md): 문서 스토리지 API 명세
- [Redis_RMQ_AI_API.md](./Redis_RMQ_AI_API.md): Backend-AI 내부 연동 API 규약
- [api-conversation-history.md](./api-conversation-history.md): 대화 이력 전달용 내부 API 명세

## 관리 원칙

1. 실제 구현과 계약이 달라지면 `API_CURRENT.md`를 먼저 수정합니다.
2. 아직 구현되지 않은 설계나 후보 기능은 `API_PLANNED.md`에 유지합니다.
3. 내부 연동 API 변경 시 관련 상세 문서와 이 README를 함께 갱신합니다.
