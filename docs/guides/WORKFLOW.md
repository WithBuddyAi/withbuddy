# 개발 워크플로우 (통합)

협업 문서에 흩어져 있던 브랜치/PR/충돌 대응 규칙을 한 곳으로 정리한 문서입니다.

## 1) 기본 브랜치 전략

- 기능 개발: `develop -> feature/* -> develop`
- 릴리즈 반영: `develop -> main`
- 긴급 수정: `main -> hotfix/* -> main -> develop`

## 2) 작업 시작

```bash
git checkout develop
git pull origin develop
git checkout -b feature/SCRUM-68-short-description
```

브랜치 네이밍:

```text
feature/SCRUM-##-description
fix/SCRUM-##-description
docs/SCRUM-##-description
refactor/SCRUM-##-description
test/SCRUM-##-description
chore/SCRUM-##-description
hotfix/SCRUM-##-description
```

## 3) PR 전 필수 절차

```bash
git checkout develop
git pull origin develop
git checkout <working-branch>
git merge develop
```

PR 대상 브랜치:

- `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*` -> `develop`
- `develop`, `hotfix/*` -> `main`

## 4) 충돌 발생 시

1. 우선 `git merge --abort`
2. 복구가 필요하면 [CONFLICT-RECOVERY.md](./CONFLICT-RECOVERY.md) 절차 수행
3. 재시도 시 [CONFLICT-MINIMIZATION.md](./CONFLICT-MINIMIZATION.md) 기준으로 재정렬

## 5) 상세 문서

- 기여 절차 상세: [CONTRIBUTING.md](./CONTRIBUTING.md)
- 저장소 관리자 설정: [GITHUB-FLOW-SETUP.md](./GITHUB-FLOW-SETUP.md)
