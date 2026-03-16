# WithBuddy

## ⚠️ 개발 규칙 (필수 숙지!)

### PR 생성 규칙
1. **절대 main 브랜치에 직접 푸시 금지**
2. **모든 변경사항은 PR 필수**
3. **리뷰어 1명 이상 지정 필수**
4. **승인 없이 머지 절대 금지**

### 리뷰어 지정 기준
- 백엔드 코드: @WithBuddyAi/backend-developers
- 프론트 코드: @WithBuddyAi/frontend  
- AI 코드: @WithBuddyAi/AI
- 긴급/핫픽스: @WithBuddyAi/admins

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

**위반 시**: 즉시 revert + 팀 미팅
```

### 5. 팀 미팅에서 합의 필요

**팀원들과 구두 합의 필요**:
```
📢 팀 규칙 (위반 시 커피 쏘기 등 벌칙)

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