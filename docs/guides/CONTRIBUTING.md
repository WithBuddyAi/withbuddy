# 기여 가이드

WithBuddy 저장소 작업자가 실제로 따라야 하는 브랜치, 커밋, PR 절차를 정의한다.

**최종 업데이트**: 2026-03-30  
**버전**: 1.0.1

## 1. 브랜치 생성

```bash
git checkout develop
git pull origin develop
git checkout -b feature/123-add-feature
```

브랜치 규칙:

```text
feature/이슈번호-설명
fix/이슈번호-설명
docs/설명
refactor/설명
test/설명
chore/설명
release/버전
hotfix/버전-설명
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

커밋 메시지 타입:

```text
feat, fix, docs, refactor, test, chore
```

## 4. PR 생성

```bash
git push origin feature/123-add-feature
```

PR base 규칙:

- `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*` -> `develop`
- `release/*`, `hotfix/*` -> `main`

## 5. PR 본문 자동 생성

- PR 생성/업데이트 시 `.github/workflows/pr-autofill.yml`이 커밋 메시지를 기반으로 PR 본문을 자동 작성한다.
- 기본 템플릿은 `.github/pull_request_template.md` 형식을 유지한다.
- 수동으로 본문을 고정하려면 `<!-- AUTO_FILL_LOCK -->`를 PR 본문에 추가한다.

## 6. 리뷰/병합

- 최소 1명 승인 후 병합
- 필수 CI 통과 확인
- 기본 병합 방식은 `Squash and merge`
- `release/*`, `hotfix/*`는 `main` 반영 후 `develop` 동기화 필수

## 7. PR 체크리스트

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
