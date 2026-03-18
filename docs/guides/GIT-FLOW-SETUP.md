# Git Flow 설정 체크리스트

이 문서는 저장소 관리자나 어드민이 Git Flow 규칙을 GitHub 설정으로 강제할 때 사용하는 문서다.

- 팀원용 규칙 요약: [COLLABORATION.md](./COLLABORATION.md)
- 작업자용 절차: [CONTRIBUTING.md](./CONTRIBUTING.md)

## 권장 브랜치 구조

```text
main
develop
feature/*
release/*
hotfix/*
```

## 1. 기본 브랜치 확인

- 기본 브랜치를 `main`으로 유지
- `develop` 브랜치가 원격 저장소에 존재하는지 확인
- 팀원이 `main` 대신 `develop`을 통합 브랜치로 사용하도록 안내

확인 명령:
```bash
git branch -a
```

## 2. Branch Protection Rule 설정

GitHub Repository -> Settings -> Branches 에서 다음 규칙을 추가한다.

### `main` 보호 규칙
- 대상 브랜치: `main`
- Require a pull request before merging: 활성화
- Require approvals: 최소 1명 이상
- Dismiss stale pull request approvals when new commits are pushed: 활성화 권장
- Require review from Code Owners: 활성화
- Require status checks to pass before merging: 활성화
- Require branches to be up to date before merging: 활성화 권장
- Restrict who can push to matching branches: 활성화 권장
- Allow force pushes: 비활성화
- Allow deletions: 비활성화

### `develop` 보호 규칙
- 대상 브랜치: `develop`
- Require a pull request before merging: 활성화
- Require approvals: 최소 1명 이상
- Require review from Code Owners: 활성화 권장
- Require status checks to pass before merging: 활성화
- Restrict who can push to matching branches: 활성화 권장
- Allow force pushes: 비활성화
- Allow deletions: 비활성화

## 3. Required Status Check 등록

현재 CI는 `main`, `develop`에 대한 push/PR에서 동작한다. 아래 job들을 merge 필수 조건으로 등록한다.

대상 파일: `.github/workflows/ci.yml`

권장 required checks:
- `Backend Tests`
- `Frontend Tests`
- `AI Tests`
- `Code Quality Check`

주의:
- required status check로 사용할 workflow는 `paths-ignore` 나 branch filter로 전체 workflow가 skip되지 않도록 구성한다
- 문서 전용 PR도 workflow 자체는 실행되고, 관련 없는 job만 `skipped` 처리되게 만드는 것이 안전하다
- 현재 workflow 안에는 `continue-on-error: true`가 들어간 step이 있어 일부 실패가 merge blocker가 되지 않을 수 있다
- required check로 쓰려면 lint/test 실패가 실제 job 실패로 이어지도록 조정하는 것이 안전하다

## 4. CODEOWNERS 강제

대상 파일: `.github/CODEOWNERS`

확인 사항:
- 백엔드 경로에 백엔드 팀 지정
- 프론트엔드 경로에 프론트 팀 지정
- AI 경로에 AI 팀 지정
- `.github/`, 설정 파일은 admins 지정

추가 권장 사항:
- 실제 저장소 경로 기준으로 owner 경로가 맞는지 점검
- 팀 slug가 GitHub 조직 내 실제 이름과 정확히 일치하는지 점검

현재 점검 포인트:
- AI 팀 slug는 `@WithBuddyAi/AI` 로 통일한다

## 5. Pull Request 운영 규칙

- `feature/*` PR target: `develop`
- `release/*` PR target: `main`
- `hotfix/*` PR target: `main`
- `main` 반영 후 `release/*`, `hotfix/*` 변경은 반드시 `develop`에도 동기화
- 기본 merge 방식은 `Squash and merge`
- Merge 후 브랜치 자동 삭제 활성화 권장

GitHub Repository -> Settings -> General 에서:
- Allow squash merging: 활성화
- Automatically delete head branches: 활성화

## 6. 릴리스 태그 규칙

- `main`에 릴리스가 반영되면 Git tag 생성
- 형식: `vMAJOR.MINOR.PATCH`

예시:
```bash
git checkout main
git pull origin main
git tag v1.0.0
git push origin v1.0.0
```

## 7. 팀 운영 원칙

- 기능 개발은 `develop`에서 분기
- 운영 장애는 `main`에서 `hotfix/*`로 분기
- 배포 직전 안정화 작업은 `release/*`에서 수행
- 문서 규칙과 GitHub 보호 규칙이 다르면 GitHub 보호 규칙을 우선 기준으로 삼는다

## 8. 현재 저장소 기준 권장 추가 작업

### 필수
- `CONTRIBUTING.md`를 Git Flow 기준으로 유지
- `COLLABORATION.md`와 팀 운영 규칙을 일치시킬 것
- `main`, `develop` Branch Protection Rule 생성
- Required status checks 등록
- Code owner review 필수화

### 권장
- `release/*`, `hotfix/*` 브랜치 사용 예시를 README에도 노출
- CI job 이름과 branch protection required check 이름을 정확히 일치시켜 문서화
- staging 배포는 `develop`, production 배포는 `main`으로 연결

## 9. 적용 완료 기준

다음 조건이 모두 충족되면 Git Flow 기반 운영이 가능하다고 판단할 수 있다.

- 팀 문서가 `main/develop/feature/release/hotfix` 흐름으로 일관된다
- `feature/*`가 `develop`에서 분기되도록 안내된다
- `main`, `develop` direct push가 기술적으로 차단된다
- PR approval과 CI 통과가 merge 조건으로 강제된다
- `main` 변경이 `develop`으로 역반영되는 절차가 문서화된다
