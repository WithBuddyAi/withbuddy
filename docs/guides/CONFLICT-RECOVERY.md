# Git 충돌/오염 상태 복구 매뉴얼 (develop 기준)

이 문서는 이미 문제가 발생한 상태(머지 충돌, 미완료 merge, 로컬 오염)에서 **정상 상태로 복구**한 뒤, 충돌 방지 규칙을 다시 따라갈 수 있도록 정리한 매뉴얼이다.

최신 업데이트: 2026-03-25  
버전: 1.0.0

---

## 1. 현재 상태 확인

```bash
git status
```

다음 메시지가 보이면 **merge가 미완료 상태**다:

- `You have not concluded your merge (MERGE_HEAD exists)`
- `Pulling is not possible because you have unmerged files`

---

## 2. 미완료 merge 중단 (가장 먼저)

```bash
git merge --abort
```

이 명령이 실패한다면, 아래의 “강제 초기화”로 이동한다.

---

## 3. 로컬 변경사항 전부 폐기 (가장 확실한 복구)

로컬 변경을 모두 버리고 원격 `develop`과 동일하게 맞춘다.

```bash
git fetch origin
git reset --hard origin/develop
```

---

## 4. 추적되지 않은 파일 정리

IDE/빌드 캐시 등 불필요한 파일 제거:

```bash
git clean -fd
```

---

## 5. 강제 초기화 (merge abort가 안 될 때)

```bash
git reset --hard HEAD
git reset --hard origin/develop
```

---

## 6. 복구 완료 확인

```bash
git status
```

정상 상태 예시:
```
On branch develop
Your branch is up to date with 'origin/develop'.
nothing to commit, working tree clean
```

---

## 7. 복구 후 충돌 방지 규칙 (요약)

1. 작업 시작 전 `develop` 최신화  
2. `develop`에서 작업 브랜치 분기  
3. PR 전에 반드시 `develop`을 내 브랜치에 merge  
4. PR 대상은 `develop`  

상세 절차는 `docs/guides/CONFLICT-MINIMIZATION.md` 참고.
