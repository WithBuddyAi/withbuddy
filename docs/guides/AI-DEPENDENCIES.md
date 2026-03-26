# AI 개발: 의존성 파일 관리 가이드

이 문서는 AI 파트 개발자가 `requirements.txt`를 **반드시** 제공해야 하는 이유와 작성 방법을 정리한다.

---

## 왜 필요한가

- CI/CD에서 서버 배포 시 `pip install -r requirements.txt`로 의존성을 설치한다.
- 파일이 없으면 배포가 실패하고 서버가 실행되지 않는다.
- 의존성 파일은 **AI 코드와 함께 버전 관리**되어야 한다.

---

## 필수 규칙

1. `ai/requirements.txt`는 반드시 커밋한다.
2. 새 라이브러리를 추가하면 `requirements.txt`를 즉시 갱신한다.
3. 로컬에서 실행되는 버전과 서버 설치 버전이 동일해야 한다.

---

## 작성 방법

### 방법 1) pip freeze 사용

```bash
pip freeze > requirements.txt
```

### 방법 2) 주요 패키지만 정리

```text
fastapi==0.115.0
uvicorn==0.30.6
langchain==0.2.0
langgraph==0.0.40
chromadb==0.5.5
```

---

## 배포와의 연결

배포 워크플로는 아래 경로를 기준으로 설치한다:

```
ai/requirements.txt
```

파일이 없으면 배포는 실패한다.
