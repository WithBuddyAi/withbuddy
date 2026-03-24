# WithBuddy 협업 규칙

이 문서는 팀원이 반드시 지켜야 하는 협업 원칙만 빠르게 확인할 수 있도록 정리한 요약본이다.

**최종 업데이트**: 2026-03-23  
**버전**: 1.0.0

- 실무 절차: [CONTRIBUTING.md](./CONTRIBUTING.md)
- 저장소 설정: [GIT-FLOW-SETUP.md](./GIT-FLOW-SETUP.md)

### Github 브랜치 전략
```
main (프로덕션)
  ↑
develop (개발)
  ↑
feature/* (기능 개발)
```
```
main (운영 배포)
  └── develop (개발)
      ├── feature/auth (A)
      ├── feature/user (A)
      ├── feature/record (A)
      ├── feature/ai-integration (B)
      ├── feature/conversation (B)
      └── feature/slack (B)
```

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

1. main 직접 푸시 = 커피 3잔
2. 리뷰 없이 머지 = 커피 2잔
3. 테스트 안 하고 푸시 = 커피 1잔

기술적 보호가 없으니 서로 책임감 갖고!

### 커밋 컨벤션
- feat: 새로운 기능
- fix: 버그 수정
- refactor: 리팩토링
- test: 테스트 코드
- docs: 문서 수정
- chore: 빌드, 설정 변경

예시:
```
 feat: User 회원가입 API 구현
 fix: JWT 토큰 만료 시간 오류 수정
 refactor: Record Service 쿼리 최적화
```

## 라이센스

**Copyright © 2026 WithBuddy Team. All Rights Reserved.**