# 기여 가이드

WithBuddy 저장소 작업자가 실제로 따라야 하는 브랜치, 커밋, PR 절차를 정의한다.

**최종 업데이트**: 2026-04-02  
**버전**: 1.1.3

## 1. 브랜치 생성

```bash
git checkout develop
git pull origin develop
git checkout -b feature/SCRUM-68-add-feature
```

브랜치 규칙:

```text
feature/SCRUM-##-설명
fix/SCRUM-##-설명
docs/SCRUM-##-설명
refactor/SCRUM-##-설명
test/SCRUM-##-설명
chore/SCRUM-##-설명
hotfix/SCRUM-##-설명
```

예시:

```text
feature/SCRUM-68-onboarding-roadmap
fix/SCRUM-72-login-timeout
docs/SCRUM-81-redis-rabbitmq-ops
```

## 2. 개발/테스트

- 변경 목적에 맞게 코드 또는 문서를 수정한다.
- 로컬 테스트 가능한 범위에서 검증한다.
- 불필요한 파일이 포함되지 않도록 `git status`로 확인한다.

## 3. 커밋

```bash
git add .
git commit -m "feat: Add weekly report generation"
```

커밋 메시지 규칙(Conventional Commits):

```text
feat, fix, docs, refactor, test, chore, ci, build, perf
```

자동 버전 산정 기준:

- `BREAKING CHANGE` 또는 `type(scope)!:` 포함 커밋이 있으면 `MAJOR` 증가
- `feat:` 커밋이 있으면 `MINOR` 증가
- 그 외 커밋은 `PATCH` 증가

## 4. PR 생성

```bash
git push origin feature/SCRUM-68-add-feature
```

PR base 규칙:

- `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*` -> `develop`
- `develop`, `hotfix/*` -> `main`

## 5. PR 본문 자동 생성

- PR 생성/업데이트 시 `.github/workflows/pr-autofill.yml`이 커밋 메시지를 기반으로 PR 본문을 자동 작성한다.
- 기본 템플릿은 `.github/pull_request_template.md` 형식을 유지한다.
- 수동으로 본문을 고정하려면 `<!-- AUTO_FILL_LOCK -->`를 PR 본문에 추가한다.

## 6. 리뷰/병합

- 최소 1명 승인 후 병합
- 필수 CI 통과 확인
- 기본 병합 방식은 `Squash and merge`
- `hotfix/*`는 `main` 반영 후 `develop` 동기화 필수

## 7. 자동 버전 태그/릴리스

- `main` 브랜치로 push(= PR merge) 되면 `.github/workflows/release-tag.yml`이 자동 실행된다.
- 최신 태그(`v*`) 이후 커밋을 분석해 `vMAJOR.MINOR.PATCH` 태그를 자동 생성한다.
- 릴리스 대상 커밋에 포함된 Markdown 문서 중 `**버전**`, `**최종 업데이트**` 헤더가 있는 파일은 태그 버전/당일 날짜로 자동 동기화한다.
- 태그는 `annotated tag`로 발행된다.
- 태그 생성 후 GitHub Release가 자동 생성되고 릴리스 노트가 자동 첨부된다.

기본 원칙:

- 릴리스 태그는 `main`에만 발행한다.
- 버전 포맷은 `v0.x.y`로 시작하고, 안정화 시점에 `v1.0.0`으로 전환한다.
- 커밋 메시지가 규칙에서 벗어나면 의도한 버전 상승이 되지 않을 수 있으므로 타입 접두사를 반드시 명시한다.

수동 예외 처리:

- 긴급 패치 등으로 자동 증가 결과를 조정해야 할 경우 릴리스 담당자가 수동 태그를 우선 발행한다.
- 자동 워크플로는 이미 존재하는 태그를 재생성하지 않는다.

## 8. PR 체크리스트

```text
브랜치명 규칙 준수
PR 대상 브랜치 적합
커밋 메시지 규칙 준수
테스트/검증 수행
리뷰어 지정
관련 이슈 연결
```

## 변경 이력

- 2026-03-30: 문서를 정리하고 `pr-autofill.yml` 기반 PR 본문 자동 생성 절차를 추가.
- 2026-04-02: `release-tag.yml` 기반 자동 SemVer 태그/Release 정책을 추가했다.
- 2026-04-02: `release/*` 브랜치 정책을 제거하고 `develop -> main` 직접 반영 규칙으로 통일했다.
- 2026-04-02: Jira 서브태스크 브랜치 키 표기를 `SCRUM-##` 대문자로 통일했다.
