# WithBuddy 협업 규칙

팀원이 빠르게 확인해야 하는 Git Flow 협업 규칙 요약 문서다.

**최종 업데이트**: 2026-04-02  
**버전**: 1.0.4

- 실무 절차: [CONTRIBUTING.md](./CONTRIBUTING.md)
- 관리자 설정: [GIT-FLOW-SETUP.md](./GIT-FLOW-SETUP.md)

## 브랜치 흐름

```text
기능 개발: develop -> feature/* -> develop
릴리스 반영: develop -> main
긴급 수정: main -> hotfix/* -> main -> develop
```

## 반드시 지킬 규칙

1. `main` 직접 push 금지
2. `develop` 직접 push 금지
3. 모든 변경은 PR로만 병합
4. 최소 1명 승인 후 병합
5. 필수 CI check 통과 후 병합
6. `main` 반영 후 `develop` 역반영 필수 (`hotfix/*`)

## 브랜치 역할

- `main`: 운영 배포 브랜치
- `develop`: 다음 배포 준비 통합 브랜치
- `feature/*`: 기능 개발
- `fix/*`: 버그 수정
- `docs/*`: 문서 수정
- `refactor/*`: 리팩토링
- `test/*`: 테스트
- `chore/*`: 설정/빌드 변경
- `hotfix/*`: 운영 긴급 수정

## PR 대상 브랜치

- `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*` -> `develop`
- `develop`, `hotfix/*` -> `main`

## 브랜치 네이밍 규칙

- 작업 브랜치는 `type/SCRUM-##-description` 형식으로 생성한다.
- 예: `feature/SCRUM-68-onboarding-roadmap`

## PR 작성 규칙

- 기본 PR 템플릿은 `.github/pull_request_template.md`를 사용
- PR 생성 시 `.github/workflows/pr-autofill.yml`이 커밋 메시지 기반으로 본문을 자동 작성
- 자동 작성 잠금이 필요하면 PR 본문에 `<!-- AUTO_FILL_LOCK -->` 추가

## 리뷰/병합 규칙

- 기본 병합 방식: `Squash and merge`
- 필요 시 `hotfix/*`는 이력 보존 merge 허용
- self-merge는 예외 상황 외 금지

## 변경 이력

- 2026-03-30: 문서 중복 내용을 정리하고 `pr-autofill.yml` 기반 PR 자동 본문 규칙 추가.
- 2026-04-02: `release/*` 브랜치 전략을 제거하고 `develop -> main` 직접 릴리스 흐름으로 통일.
- 2026-04-02: Jira 서브태스크 브랜치 키 표기를 `SCRUM-##` 대문자로 통일.
