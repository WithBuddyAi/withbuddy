# Git 충돌 최소화 매뉴얼 (develop 기반)

이 문서는 `develop` 중심 협업에서 충돌을 최소화하고, 안정적으로 `develop`에 병합한 뒤 `main`으로 릴리스하기 위한 작업 순서를 정리한 매뉴얼이다.

최신 업데이트: 2026-03-25  
버전: 1.0.0

---

## 기본 원칙

1. `main`, `develop`에 직접 push 하지 않는다.
2. 모든 작업은 `develop`에서 분기한 브랜치에서 진행한다.
3. PR 대상은 기본적으로 `develop`이다.
4. PR 전에 반드시 `develop` 최신을 작업 브랜치에 반영한다.

---

## 작업 시작 순서 (필수)

1. `develop` 최신화
```bash
git checkout develop
git pull origin develop
```

2. 작업 브랜치 생성
```bash
git checkout -b feature/이슈번호-간단설명
```

---

## 작업 중 규칙 (충돌 최소화 핵심)

- 작은 단위로 자주 커밋한다.
- 오래된 작업 브랜치를 방치하지 않는다.
- PR 올리기 전에 반드시 `develop`을 내 브랜치에 합친다.

---

## PR 전 최신화 (가장 중요)

PR을 올리기 전에 **반드시 `develop` 최신을 내 브랜치에 반영**한다.  
충돌은 이 단계에서 해결하는 것이 가장 안전하다.

1. `develop` 최신화
```bash
git checkout develop
git pull origin develop
```

2. 작업 브랜치로 복귀
```bash
git checkout feature/이슈번호-간단설명
```

3. `develop` 반영 (팀 규칙: merge)
```bash
git merge develop
```

---

## PR 생성

1. 원격에 push
```bash
git push origin feature/이슈번호-간단설명
```

2. PR 대상 브랜치
- `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*` -> `develop`
- `release/*`, `hotfix/*` -> `main`

---

## 충돌 발생 시 처리

1. 충돌 파일을 열고 표시된 부분을 직접 정리한다.
2. 정리 후 커밋한다.
```bash
git add 충돌파일
git commit -m "fix: Resolve merge conflict"
```

---

## 릴리스 흐름 요약

- 기능 개발: `develop` -> `feature/*` -> `develop`
- 릴리스 준비: `develop` -> `release/*` -> `main` -> `develop`
- 긴급 수정: `main` -> `hotfix/*` -> `main` -> `develop`

---

## 빠른 체크리스트

- 작업 시작 전에 `develop` 최신화했는가?
- 작업 브랜치가 `develop`에서 분기되었는가?
- PR 전에 `develop` 최신을 내 브랜치에 반영했는가?
- PR 대상 브랜치가 맞는가?
- 충돌은 PR 전에 로컬에서 해결했는가?
