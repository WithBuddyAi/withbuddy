# Architecture 문서

`docs/architecture` 폴더에 포함된 설계 문서 목록입니다.

## 문서 목록

- [ARCHITECTURE.md](./ARCHITECTURE.md): 서비스 전체 구조와 주요 흐름
- [INFRASTRUCTURE.md](./INFRASTRUCTURE.md): 클라우드 인프라와 네트워크 구성
- [AI_ARCHITECTURE.md](./AI_ARCHITECTURE.md): AI 서비스 구조와 구성 요소
- [AI_SERVER_GUIDE.md](./AI_SERVER_GUIDE.md): AI 서버 배포/운영 가이드
- [Redis_RMQ_SSE.md](./Redis_RMQ_SSE.md): Redis, RabbitMQ, SSE 상세 설계
- [MULTI_TENANCY.md](./MULTI_TENANCY.md): 멀티테넌시 모델과 데이터 분리 전략
- [SECURITY.md](./SECURITY.md): 인증, 인가, 네트워크, 데이터 보안 설계
- [STORAGE_STRATEGY.md](./STORAGE_STRATEGY.md): Object Storage 도입 전략
- [OCI_OBJECT_STORAGE_STRATEGY.md](./OCI_OBJECT_STORAGE_STRATEGY.md): OCI 이중 테넌시 Object Storage 운영 전략

## 다이어그램

- `images/`: 아키텍처 및 인프라 다이어그램 이미지

## 관리 원칙

1. 구조 문서는 시스템 구성 변경과 함께 갱신합니다.
2. 새 설계 문서를 추가하면 이 README의 목록에도 반영합니다.
