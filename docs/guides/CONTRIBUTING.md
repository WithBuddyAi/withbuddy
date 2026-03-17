# 기여 가이드

> WithBuddy 프로젝트에 기여하는 방법

## 📋 목차
- [기여 프로세스](#기여-프로세스)
- [브랜치 전략](#브랜치-전략)
- [커밋 메시지 컨벤션](#커밋-메시지-컨벤션)
- [Pull Request 가이드](#pull-request-가이드)
- [코드 리뷰](#코드-리뷰)

---

## 기여 프로세스

### 1. Issue 확인 및 할당
```
1. GitHub Issues에서 작업할 태스크 선택
2. 본인에게 할당 (Assignees 설정)
3. 라벨 확인 (bug, feature, enhancement 등)
```

### 2. 브랜치 생성
```bash
# 최신 main 브랜치로 업데이트
git checkout main
git pull origin main

# 새 브랜치 생성
git checkout -b feature/123-add-feature
```

### 3. 개발
```bash
# 코드 작성
# 테스트 작성
# 로컬 테스트 실행
```

### 4. 커밋
```bash
git add .
git commit -m "feat: Add weekly report generation feature"
```

### 5. Push & Pull Request
```bash
git push origin feature/123-add-feature

# GitHub에서 Pull Request 생성
```

### 6. 코드 리뷰 & Merge
```
- 최소 1명의 approve 필요
- CI/CD 통과 확인
- Squash and Merge 권장
```

---

## 브랜치 전략

### 브랜치 명명 규칙
```
feature/이슈번호-간단한-설명
fix/이슈번호-버그-설명
docs/문서-수정-내용
refactor/리팩토링-내용
test/테스트-추가-내용
chore/빌드-설정-변경
```

### 예시
```bash
feature/123-add-weekly-report
fix/456-login-error
docs/update-readme
refactor/improve-service-logic
test/add-unit-tests
chore/update-dependencies
```

### 브랜치 수명
```
- feature/* : PR merge 후 삭제
- fix/* : PR merge 후 삭제
- main : 영구 유지
```

---

## 커밋 메시지 컨벤션

### 기본 형식
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type
```
feat     : 새로운 기능 추가
fix      : 버그 수정
docs     : 문서 수정
style    : 코드 포맷팅 (세미콜론 누락 등, 코드 변경 없음)
refactor : 코드 리팩토링
test     : 테스트 코드 추가/수정
chore    : 빌드, 라이브러리 업데이트 등
```

### Scope (선택사항)
```
record   : 기록 도메인
user     : 사용자 도메인
auth     : 인증
conversation : Q&A 도메인
progress : 진행률
checklist : 체크리스트
```

### Subject
```
- 50자 이내
- 영문으로 작성 시 첫 글자 대문자
- 마침표 없음
- 명령형 사용 (Add, Fix, Update)
```

### 예시
```bash
feat(record): Add AI summary feature for daily records

- Integrate with FastAPI summarization endpoint
- Add summary button to record detail page
- Display summarized content with markdown support

Resolves: #123

feat: Implement weekly report generation

fix(auth): Resolve login authentication error

docs: Update API documentation

refactor(record): Improve record service logic

test(user): Add unit tests for UserService
```

---

## Pull Request 가이드

### PR 제목 컨벤션
```
[TYPE] Brief description

예시:
[FEAT] Add weekly report generation
[FIX] Resolve login authentication error
[DOCS] Update API documentation
[REFACTOR] Improve record service logic
```

### PR 템플릿
```markdown
## 📋 작업 내용
<!-- 어떤 기능을 추가/수정했는지 -->

## 🔗 관련 이슈
Resolves #123

## ✅ 체크리스트
- [ ] 로컬 테스트 완료
- [ ] 코드 리뷰 준비 완료
- [ ] 문서 업데이트 (필요 시)

## 📸 스크린샷 (선택)
<!-- UI 변경이 있는 경우 -->

## 💬 리뷰어에게
<!-- 특별히 확인해주었으면 하는 부분 -->
```

### PR 생성 시 확인사항
```
✅ 브랜치명이 규칙에 맞는가?
✅ 커밋 메시지가 규칙에 맞는가?
✅ 로컬에서 테스트가 통과하는가?
✅ 충돌(Conflict)이 없는가?
✅ 리뷰어가 지정되어 있는가?
✅ 라벨이 추가되어 있는가?
✅ 관련 Issue가 링크되어 있는가?
```

---

## 코드 리뷰

### 리뷰어 역할
```
1. 코드 품질 확인
2. 버그 가능성 체크
3. 가독성 검토
4. 테스트 충분성 확인
5. 컨벤션 준수 확인
```

### 리뷰 가이드
```
- 건설적인 피드백 제공
- 코드뿐만 아니라 설계도 검토
- 질문은 명확하게
- 긍정적인 부분도 언급
```

### 리뷰 코멘트 예시
```
✅ Good:
"이 부분은 Optional을 사용하면 더 안전할 것 같습니다. 어떻게 생각하시나요?"

❌ Bad:
"이 코드는 잘못되었습니다."
```

### Approve 기준
```
- 로직이 올바른가?
- 테스트가 충분한가?
- 컨벤션을 지켰는가?
- 문서가 업데이트 되었는가? (필요 시)
- CI/CD가 통과했는가?
```

---

## 다음 단계

- [코딩 컨벤션](../conventions/CODING.md) - Java, TypeScript, Python 규칙
- [개발 환경 설정](SETUP.md) - 로컬 개발 환경 구축
