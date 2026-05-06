# AGENTS.md — With Buddy AI 에이전트 아키텍처

> AI 에이전트 시스템의 설계 원칙, 제약 조건, 의사결정 기록입니다.
> 새로운 기능을 추가하거나 구조를 변경할 때 반드시 이 문서를 먼저 읽으세요.

---

## 시스템 목표

With Buddy AI는 수습사원이 온보딩 과정에서 겪는 질문을 **정확하고 신뢰할 수 있게** 답변하는 것을 목표로 합니다.

> **핵심 원칙**: 모르면 지어내지 않는다. 없는 내용은 "경영지원팀에 문의"로 안내한다.

---

## 에이전트 구조

### 1. RAG 에이전트 (메인, `chains/rag_chain.py`)

**역할**: 사내 문서 기반 질의응답

**처리 흐름**:
1. OOS(Out-of-Scope) 감지 → 범위 외 질문 차단
2. 복합 질문 분리 → 단일 질문 여러 개로 분해
3. 병렬 검색 → 각 질문별 동시 벡터 검색
4. 리랭킹 → CrossEncoder로 문서 재정렬 (현재 비활성화, `RERANKING_ENABLED = False`)
5. 답변 생성 → Claude 기반 최종 답변

**제약**:
- 검색 결과 없으면 반드시 fallback 메시지 반환
- 회사 문서와 공통(법률) 문서 격리 유지
- 유저 말투 감지 후 존댓말/반말 맞춤 응답

### 2. ReAct 에이전트 (실험용, `chains/agent_rag_chain.py`)

**역할**: 도메인별 툴 선택으로 복합 질문 정확도 향상

**도메인 툴**:
| 툴 | 담당 도메인 |
|----|------------|
| `search_hr` | 연차, 휴가, 병가, 급여, 재택근무 |
| `search_admin` | 경비처리, 법인카드, 출장, 명함 |
| `search_it` | IT 장비, 계정, VPN, 보안 |
| `search_welfare` | 복지카드, 건강검진, 동호회 |
| `search_legal` | 근로기준법, 최저임금, 퇴직금 |

**제약**:
- 툴 미사용 시 fallback 메시지 반환 (hallucination 방지)
- company_code 클로저로 캡처 (멀티테넌트 격리)
- company_code별 에이전트 캐싱 (매 요청 생성 금지)
- 현재 `/chat/agent` 테스트 엔드포인트에서만 동작

---

## 아키텍처 제약 (변경 금지 항목)

### 절대 변경하면 안 되는 것

| 항목 | 이유 |
|------|------|
| `/internal/ai/answer` 응답 스키마 | 백엔드와 계약된 인터페이스 |
| `company_code` 격리 로직 | 멀티테넌트 보안 핵심 |
| OOS 감지 레이어 | 범위 외 질문 차단 필수 |
| 대화 히스토리 저장 구조 | 멀티턴 컨텍스트 의존 |

### 신중하게 변경해야 하는 것

| 항목 | 주의사항 |
|------|---------|
| `_search_sub_q` | 리랭킹 포함, 검색 품질 직접 영향 |
| `_decompose_question` | 복합 질문 분리 로직, 오작동 시 정확도 급락 |
| 시스템 프롬프트 (`utils/prompts.py`) | With Buddy 캐릭터 일관성 유지 |
| ChromaDB 필터 구조 | `$and` 연산자 필수, 단순 dict 필터 사용 불가 |

---

## 설계 의사결정 기록

### ADR-001: AgentExecutor → create_react_agent 전환 (2026-04-18)

**결정**: LangChain AgentExecutor 대신 LangGraph `create_react_agent` 사용

**이유**: LangChain 0.3에서 AgentExecutor 제거됨. LangGraph가 공식 후속 도구.

**영향**: `agent_rag_chain.py` 전면 재작성

---

### ADR-002: 복합 질문 처리 — 분리 검색 채택 (2026-04-14)

**결정**: 복합 질문을 단일 질문으로 분해 후 병렬 검색

**이유**: 단일 검색으로는 "연차 + 복지카드" 같은 복합 질문에서 한쪽 컨텍스트가 부족함

**영향**: `_decompose_question` + `ThreadPoolExecutor` 도입

---

### ADR-003: Cross-Encoder 리랭킹 도입 → 비활성화 결정 (2026-04-18 → 2026-04-27)

**최초 결정**: 벡터 검색 후 BAAI/bge-reranker-v2-m3로 재정렬

**이유**: 벡터 유사도는 의미적 유사성 기반이라 실제 관련성과 차이날 수 있음. Cross-Encoder는 질문-문서 쌍을 직접 비교해 더 정확.

**트레이드오프**: 첫 로딩 2.27GB, 이후 로컬 추론이라 API 비용 없음

**2026-04-27 평가 결과 — 리랭킹 비활성화로 변경**

20문항(HR/ADMIN/WELFARE/IT × 5) 키워드 정확도 + 응답 시간 측정:

| 조건 | 키워드 정확도 | 평균 응답 시간 |
|------|------------|--------------|
| 리랭킹 ON | 87.5% | 8,221ms |
| 리랭킹 OFF | 92.5% | 4,926ms |

**원인 분석**:
- BAAI/bge-reranker-v2-m3는 다국어 범용 모델 — 한국어 HR/사규 도메인에 최적화되지 않음
- 임베딩 모델(`jhgan/ko-sroberta-multitask`)이 이미 한국어 특화라 bi-encoder 검색 품질이 충분히 높음
- Cross-Encoder가 문서를 300자로 잘라 점수 매기는 과정에서 오히려 정보 손실 발생

**현재 결정**: `RERANKING_ENABLED = False` (토글 플래그 유지, 코드 제거 보류)

**향후 재검토 조건**: 한국어 특화 Cross-Encoder 모델 발견 시 교체 테스트

---

### ADR-004: 멀티테넌트 — company_code 메타데이터 필터 (2026-03-xx)

**결정**: ChromaDB 메타데이터 필터로 회사별 문서 격리

**이유**: 다른 회사 사규가 섞이면 오답 가능성. 법률 문서는 공통으로 모든 회사에 제공.

**구현**: `search_with_company_fallback()` — 회사 문서 + 공통 문서 OR 조건 검색

---

### ADR-005: 도메인별 툴 분리 (2026-04-18)

**결정**: HR/ADMIN/IT/WELFARE/LEGAL 5개 도메인으로 툴 분리

**이유**: 단일 검색 툴은 복합 질문에서 모든 도메인 컨텍스트를 한 번에 로드해 토큰 낭비. 도메인 분리로 필요한 컨텍스트만 선택 가능.

**한계**: 현재 category 메타데이터 미적용 (서버 데이터 확인 후 추가 예정)

---

## 평가 기준

| 지표 | 목표 | 측정 도구 |
|------|------|----------|
| Answer Relevancy | 0.75 이상 | RAGAS |
| Faithfulness | 0.75 이상 | RAGAS |
| 응답 시간 (단순 질문) | 5초 이내 | LangSmith |
| 응답 시간 (복합 질문) | 10초 이내 | LangSmith |
| OOS 차단율 | 범위 외 질문 100% 차단 | 수동 테스트 |

---

## 알려진 한계 및 개선 예정

| 항목 | 현황 | 개선 방향 |
|------|------|----------|
| 에이전트 doc_ids 미반환 | `[]` 하드코딩 | 툴 결과에서 문서 ID 추출 |
| category 메타데이터 미적용 | 서버 데이터 확인 필요 | `$and` 필터로 도메인 격리 강화 |
| 에이전트 실유저 미연결 | 테스트 엔드포인트만 존재 | 성능 검증 후 메인 연결 |
| Guardrail 재시도 없음 | 툴 미사용 시 fallback만 | `tool_choice` 파라미터 적용 예정 |

---

## 로컬 개발 환경 체크리스트

```bash
# 1. 가상환경 활성화
venv\Scripts\activate

# 2. 서버 실행
uvicorn main:app --reload --port 8000

# 3. 에이전트 테스트
python scripts/test_agent.py

# 4. RAG 평가
python scripts/evaluate.py

# 5. PR 전 requirements.txt 업데이트
pip freeze > requirements.txt
```
