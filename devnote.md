# 개발 일지 (통합)

문서 전반에 흩어진 개발 일지/변경 이력을 날짜 기준으로 통합 정리한 노트입니다.

## 2026-04-02
- Jira 서브태스크 키(`SCRUM-##`)를 브랜치 네이밍 규칙에 통합했다.
  - 형식: `type/SCRUM-##-description`
  - 반영 문서: `CONTRIBUTING`, `COLLABORATION`, `CONFLICT-MINIMIZATION`, `GIT-FLOW-SETUP`, `README`, `backend/README`, `frontend/README`
- 브랜치 운영 정책에서 `release/*`를 제거하고 `develop -> main` 직접 릴리스 방식으로 문서를 통일했다.
  - 대상: `CONTRIBUTING`, `COLLABORATION`, `CONFLICT-MINIMIZATION`, `GIT-FLOW-SETUP`, `backend/README`
- `main` 머지 기준 자동 버전 태깅/릴리스 워크플로(`.github/workflows/release-tag.yml`)를 추가했다.
  - Conventional Commits 분석으로 `vMAJOR.MINOR.PATCH` 자동 산정
  - `annotated tag` 자동 발행
  - GitHub Release 및 자동 릴리스 노트 생성
  - 릴리스 대상 커밋에 포함된 Markdown 문서의 `**버전**`, `**최종 업데이트**` 헤더를 자동 동기화 후 반영
- `CONTRIBUTING`에 자동 버전 관리 운영 기준을 반영했다.
  - 커밋 타입 규칙과 버전 상승 규칙(`major/minor/patch`) 명시
  - 수동 예외 처리 원칙(긴급 수동 태그 우선) 명시
- `DEPLOYMENT-ORACLE` 인프라 다이어그램에서 DB 서버 구성(MySQL/Redis/RabbitMQ)을 명확히 표기하도록 이미지를 보강했다.
- `ARCHITECTURE` 문서 서버 구성에 DB 서버 내 Redis/RabbitMQ 공용 설치와 역할 분리를 명시했다.
- 용어 통일을 진행했다.
  - `주별 회고` → `주간 회고`
  - RabbitMQ를 `메시징 시스템`으로 명시
- OS 표기를 `Canonical Ubuntu 24.04` 기준으로 정리했다.
- `network-topology` 다이어그램을 현재 운영 구조(Tenancy A: AI / Tenancy B: Backend+DB) 기준으로 재작성하고 미사용 구성 표기를 제거했다.
- 공개 저장소 노출 방지를 위해 실 공인 IP(`217.142.242.239`)를 플레이스홀더(`AI_SERVER_PUBLIC_IP`)로 마스킹했다.
- PR 자동 본문 워크플로를 정리했다.
  - 생성/재오픈 시점에만 실행
  - 템플릿 기본 본문은 자동 생성으로 대체
  - 사용자가 직접 입력한 본문은 덮어쓰지 않도록 보호
  - 빈 섹션은 출력하지 않도록 정리
- PR 템플릿을 간소화했다.
  - `PR 요약은 자동으로 생성됩니다. 제목만 적어주세요.` 안내 문구 적용
- `devjobcollector`의 배포 패턴을 참고해 Backend 자동배포 워크플로(`.github/workflows/backend-deploy.yml`)를 재작성했다.
  - bootJar 빌드 → app.jar 업로드 → 원격 env 주입 → 백엔드 재시작 → 헬스체크
- 문서 메타데이터(최종 업데이트/버전)와 상대 링크를 전수 점검해 깨진 링크를 정리했다.

## 2026-04-01
- Redis(캐시)와 RabbitMQ(메시징) 역할 분리 원칙을 아키텍처/인프라/보안/환경변수 문서에 반영했다.
- AI 지연 대응 운영 패턴을 문서화했다.
  - 채팅/간단 액션: Redis 중심(즉시성 우선)
  - 주간 회고/리포트/재인덱싱/알림: RabbitMQ 비동기 처리(완결성 우선)
- DB 서버 공용 배치(MySQL + Redis + RabbitMQ) 기준의 보안 정책(NSG/UFW/접근제어)과 점검 명령을 추가했다.
- `DEPLOYMENT-ORACLE`에 Redis/RabbitMQ 설치/설정/검증 절차를 추가했다.

## 2026-03-30
- GitHub Actions `Environment: production` 기준으로 AI 배포 시크릿이 등록 완료된 상태를 문서에 명시했다.
- AI 시크릿 이름 표기를 `${{ secrets.* }}` 형식으로 통일했다.
- 개발 단계 AI 서버 공개 정책(8000 비공개, 80/443 리버스 프록시) 및 배포 검증 기준 문구를 보강했다.
- 문서 정리 및 PR 자동 본문(`pr-autofill.yml`) 운영 규칙을 반영했다.

## 2026-03-29
- 문서 내 AI 배포 시크릿 체계를 실제 워크플로우 기준으로 정리했다.
- 자동배포 실패를 방지하기 위해 서버 선행조건(systemd/venv/sudoers/health-check)과 점검 명령어를 명문화했다.
- 운영 기준을 노트북+터널에서 OCI 실서버+CI/CD 기준으로 전환했다.
- AI 서버 운영 과정에서 확인된 노출 리스크를 반영해 개발단계 공개 정책을 문서화했다.
- CI/CD 재시작 성공만으로 배포를 완료로 보지 않고 헬스체크까지 확인하도록 기준을 명시했다.

## 개발 일지 원문 이관

### 2026-03-30
- (DEPLOYMENT-ORACLE) GitHub Actions `Environment: production` 기준으로 AI 배포 시크릿이 등록 완료된 상태를 문서에 명시.
- (DEPLOYMENT-ORACLE) AI 시크릿 이름 표기를 `${{ secrets.* }}` 형식으로 통일.

### 2026-03-29
- (DEPLOYMENT-ORACLE) 문서 내 AI 배포 시크릿 체계를 실제 워크플로우 기준으로 정리.
- (DEPLOYMENT-ORACLE) 자동배포 실패를 방지하기 위해 서버 선행조건과 점검 명령어를 명문화.
- (SERVER_GUIDE) 운영 기준을 "노트북 + 터널"에서 "OCI 실서버 + systemd + CI/CD" 기준으로 전면 전환.
- (SERVER_GUIDE) 실서버 점검 결과를 문서에 반영하여 배포 전 필수 선행조건을 명시.
- (SERVER_GUIDE) CI/CD 준비 완료 판정 기준을 `service active`와 `health check 200`으로 정의.
- (SECURITY) AI 서버 운영 과정에서 확인된 노출 리스크를 반영해 개발단계 공개 정책을 문서화.
- (SECURITY) CI/CD 재시작 성공만으로 배포를 완료로 보지 않고 헬스체크까지 확인하도록 기준을 명시.

## 2026-03-27
- 오사카 리전 기준 테넌시 분리 구조(VCN-A/VCN-B)와 LPG 통신 경로를 반영했다.
- 인프라/보안 규칙, 서버 스펙, 다이어그램을 업데이트했다.

## 2026-03-24 ~ 2026-03-23
- AI 아키텍처 문서 초안을 정리했다.
- AI/Backend/Frontend 환경변수 구조를 정리했다.
