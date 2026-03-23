# WithBuddy 협업 규칙

이 문서는 팀원이 반드시 지켜야 하는 협업 원칙만 빠르게 확인할 수 있도록 정리한 요약본이다.

**최종 업데이트**: 2026-03-23  
**버전**: 1.0.0

- 실무 절차: [CONTRIBUTING.md](./CONTRIBUTING.md)
- 저장소 설정: [GIT-FLOW-SETUP.md](./GIT-FLOW-SETUP.md)

## 한눈에 보기

```text
기능 개발: develop -> feature/* -> develop
릴리스 준비: develop -> release/* -> main -> develop
긴급 수정: main -> hotfix/* -> main -> develop
```

## 반드시 지킬 규칙

1. `main` 브랜치에 직접 push 하지 않는다.
2. `develop` 브랜치에 직접 push 하지 않는다.
3. 모든 변경사항은 Pull Request로만 병합한다.
4. 최소 1명 이상의 승인 없이 merge 하지 않는다.
5. 필수 CI check가 실패하면 merge 하지 않는다.

## 브랜치 역할

- `main`: 운영 배포 브랜치
- `develop`: 다음 배포를 준비하는 통합 브랜치
- `docs/*`: 개발 문서. `develop`에서 분기
- `feature/*`: 기능 개발 브랜치. `develop`에서 분기
- `fix/*`: 버그 수정 브랜치. `develop`에서 분기
- `refactor/*`: 리팩토링 브랜치. `develop`에서 분기
- `test/*`: 테스트 추가/수정 브랜치. `develop`에서 분기
- `chore/*`: 설정 및 빌드 변경 브랜치. `develop`에서 분기
- `release/*`: 배포 준비 브랜치. `develop`에서 분기
- `hotfix/*`: 긴급 수정 브랜치. `main`에서 분기

## PR 대상 브랜치

- `feature/*` -> `develop`
- `fix/*` -> `develop`
- `docs/*` -> `develop`
- `refactor/*` -> `develop`
- `test/*` -> `develop`
- `chore/*` -> `develop`
- `release/*` -> `main`
- `hotfix/*` -> `main`

## merge 후 후속 작업

- `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*` 는 merge 후 브랜치 삭제
- `release/*`, `hotfix/*` 는 `main` 반영 후 반드시 `develop`에도 변경사항 동기화
- 운영 배포 시 Git tag 생성 권장: `v1.0.0`

## 리뷰어 기준

- 백엔드 코드: `@WithBuddyAi/backend-developers`
- 프론트 코드: `@WithBuddyAi/frontend`
- AI 코드: `@WithBuddyAi/AI`
- 공통 설정, GitHub 설정, 긴급 이슈: `@WithBuddyAi/admins`

## merge 방식

- 기본 merge 방식은 `Squash and merge`
- `release/*`, `hotfix/*` 는 이력 보존이 필요하면 일반 merge 허용
- self-merge는 예외 상황이 아니면 금지

## 커밋 메시지 기본 규칙

- `feat`: 새로운 기능
- `fix`: 버그 수정
- `refactor`: 리팩토링
- `test`: 테스트 코드
- `docs`: 문서 수정
- `chore`: 설정 및 빌드 변경

예시:
```text
feat: Add weekly report generation
fix: Resolve login authentication error
docs: Update collaboration guide
```
