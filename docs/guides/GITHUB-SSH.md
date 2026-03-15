# GitHub SSH 키 설정 가이드

> WithBuddyAi 팀원 필수 설정

## 🎯 왜 SSH 키가 필요한가요?

- HTTPS보다 안전하고 편리
- 매번 비밀번호 입력 불필요
- Git 작업 속도 향상

## ✅ 설정 체크리스트

```
□ SSH 키 생성
□ GitHub에 공개키 등록
□ 연결 테스트
□ Git Remote URL을 SSH로 변경
□ Push 테스트
```

---

## 📋 단계별 가이드

### 1. SSH 키 생성

**터미널 (Mac/Linux) 또는 Git Bash (Windows) 실행:**

```bash
# SSH 키 생성
ssh-keygen -t ed25519 -C "your_email@example.com"
# ↑ your_email@example.com을 본인 이메일로 변경

# 질문이 나오면 엔터 3번
# 1. 저장 위치: (엔터 - 기본값 사용)
# 2. 비밀번호: (엔터 - 비워두기 권장)
# 3. 비밀번호 확인: (엔터)
```

**결과:**
```
Your identification has been saved in /Users/username/.ssh/id_ed25519
Your public key has been saved in /Users/username/.ssh/id_ed25519.pub
```

### 2. 공개키 복사

**Mac:**
```bash
cat ~/.ssh/id_ed25519.pub | pbcopy
```

**Windows (Git Bash):**
```bash
cat ~/.ssh/id_ed25519.pub | clip
```

**Linux:**
```bash
cat ~/.ssh/id_ed25519.pub
# 출력된 내용을 마우스로 복사
```

### 3. GitHub에 SSH 키 등록

1. **GitHub.com 접속** → 로그인
2. 우측 상단 **프로필 사진** 클릭 → **Settings**
3. 왼쪽 메뉴 **SSH and GPG keys** 클릭
4. **New SSH key** 버튼 클릭
5. 입력:
   - **Title**: `내 맥북` (또는 `회사 노트북` 등)
   - **Key**: 복사한 공개키 붙여넣기 (ssh-ed25519로 시작)
6. **Add SSH key** 클릭
7. GitHub 비밀번호 입력 (확인용)

### 4. 연결 테스트

```bash
ssh -T git@github.com
```

**처음 연결 시 질문:**
```
Are you sure you want to continue connecting (yes/no)?
→ yes 입력 후 엔터
```

**성공 메시지:**
```
Hi username! You've successfully authenticated, but GitHub 
does not provide shell access.
```

### 5. 프로젝트 Clone (신규)

```bash
# SSH로 클론
git clone git@github.com:WithBuddyAi/withbuddy.git
cd withbuddy
```

### 6. 기존 프로젝트 URL 변경

**이미 HTTPS로 클론한 경우:**

```bash
# 현재 URL 확인
git remote -v

# SSH로 변경
git remote set-url origin git@github.com:WithBuddyAi/withbuddy.git

# 확인
git remote -v
# git@github.com:WithBuddyAi/withbuddy.git 로 표시되면 성공
```

### 7. 테스트 Push

```bash
# 테스트 파일 생성
echo "# SSH Test" > test.md

# Git 작업
git add test.md
git commit -m "test: SSH connection test"
git push origin main

# 비밀번호 없이 push 되면 성공! 🎉
```

---

## 🐛 트러블슈팅

### ❌ "Permission denied (publickey)"

**원인:** SSH 키가 제대로 등록되지 않음

**해결:**
```bash
# 1. SSH 키 다시 확인
cat ~/.ssh/id_ed25519.pub

# 2. GitHub Settings → SSH keys 에서 키 재등록

# 3. ssh-agent 시작
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_ed25519

# 4. 다시 테스트
ssh -T git@github.com
```

### ❌ "Could not open a connection to your authentication agent"

**해결:**
```bash
# ssh-agent 시작
eval "$(ssh-agent -s)"

# SSH 키 추가
ssh-add ~/.ssh/id_ed25519
```

### ❌ "No such file or directory: ~/.ssh/id_ed25519"

**원인:** SSH 키를 아직 생성하지 않음

**해결:**
```bash
# 다시 1단계부터 진행
ssh-keygen -t ed25519 -C "your_email@example.com"
```

---

## 💡 팁

### 여러 GitHub 계정 사용하는 경우

**~/.ssh/config 파일 생성:**
```bash
# 개인 계정
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_ed25519

# 회사 계정
Host github-work
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_ed25519_work
```

### SSH 키 비밀번호 설정한 경우

매번 비밀번호 입력을 피하려면:
```bash
# Mac
ssh-add --apple-use-keychain ~/.ssh/id_ed25519

# Linux/Windows
ssh-add ~/.ssh/id_ed25519
```

---

## 📞 도움이 필요하면?

- **Discord**: 개발
- **GitHub Issues**: 기술 문의
- **팀 미팅**: 매일 16:00 AM

---

## ✅ 완료 확인

설정이 완료되면 디스코드 개발 채널에 메시지 남겨주세요:
```
✅ SSH 설정 완료 - [본인 이름]
```

**Happy Coding! 🚀**
