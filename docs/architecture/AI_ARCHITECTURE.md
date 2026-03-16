# WithBuddy AI 아키텍처

> LLM 기반 지능형 온보딩 비서 시스템

**최종 업데이트**: 2026-03-17  
**버전**: 1.0.0

---

## 📋 목차

- [1. AI 시스템 개요](#1-ai-시스템-개요)
- [2. 기술 스택](#2-기술-스택)
- [3. RAG 파이프라인](#3-rag-파이프라인)
- [4. LangGraph Agent](#4-langgraph-agent)
- [5. Vector Database](#5-vector-database)
- [6. LLM 전략](#6-llm-전략)
- [7. Fine-tuning](#7-fine-tuning)
- [8. 캐싱 & 최적화](#8-캐싱--최적화)

---

## 1. AI 시스템 개요

### 1.1 AI 서비스 구조

```
┌─────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                 │
└────────────────────────┬────────────────────────────────┘
                         │ Internal API (HTTP)
                         ↓
┌─────────────────────────────────────────────────────────┐
│                AI Service (FastAPI)                      │
│  ┌──────────────────────────────────────────────────┐  │
│  │            LangChain / LangGraph                  │  │
│  │  ┌──────────────┐    ┌──────────────┐            │  │
│  │  │ RAG Pipeline │    │ Agent Router │            │  │
│  │  └──────┬───────┘    └──────┬───────┘            │  │
│  └─────────┼───────────────────┼────────────────────┘  │
│            ↓                   ↓                        │
│  ┌─────────────────┐  ┌─────────────────┐              │
│  │  Vector Store   │  │  LLM Provider   │              │
│  │  (ChromaDB)     │  │  (OpenAI/Llama) │              │
│  └─────────────────┘  └─────────────────┘              │
└────────┬────────────────────────┬───────────────────────┘
         │                        │
         ↓                        ↓
┌─────────────────┐      ┌─────────────────┐
│  MySQL          │      │  OpenAI API     │
│  (Metadata)     │      │  (GPT-4)        │
└─────────────────┘      └─────────────────┘
```

### 1.2 주요 기능

| 기능 | 설명 | 기술 |
|------|------|------|
| **Q&A** | 사내 문서 기반 질문 응답 | RAG + GPT-4 |
| **요약** | 기록/리포트 자동 요약 | GPT-4 Summarization |
| **추천** | 개인화된 체크리스트 추천 | Collaborative Filtering |
| **검색** | 문서 의미 기반 검색 | Vector Similarity Search |

---

## 2. 기술 스택

### 2.1 AI Framework

```yaml
# LLM 애플리케이션 프레임워크
LangChain: 0.1.0+
  - 문서 로더
  - 텍스트 분할
  - 임베딩 & 벡터 저장
  - Chain 구성

LangGraph: 0.0.20+
  - Agent 워크플로우
  - 상태 관리
  - 조건부 라우팅

# LLM 추론 엔진
vLLM: 0.3.0+
  - 고성능 추론
  - 배치 처리
  - GPU 최적화
```

### 2.2 AI Models

```yaml
# 상용 LLM
OpenAI GPT-4 Turbo
  - 용도: 복잡한 질문, 높은 품질
  - API: chat.completions

OpenAI GPT-3.5 Turbo
  - 용도: 간단한 질문, 비용 절감
  - API: chat.completions

# 오픈소스 LLM
Llama 3 (8B/70B)
  - 용도: 자체 호스팅, 비용 0원
  - 배포: vLLM

Qwen 2 (7B/72B)
  - 용도: 다국어 지원
  - 배포: vLLM

# Vision-Language Models
GPT-4 Vision
  - 용도: 이미지 기반 질문 (향후)
```

### 2.3 Vector Database

```yaml
# 벡터 저장소
ChromaDB: 0.4.0+
  - 용도: 중소규모 (<100만 문서)
  - 장점: 설치 쉬움, 관리 간편
  - 회사별 컬렉션 분리

FAISS: 1.7.0+
  - 용도: 대규모 (>100만 문서)
  - 장점: 초고속 검색
  - Meta 오픈소스

# 그래프 데이터베이스 (선택)
Neo4j: 5.0+
  - 용도: 지식 그래프, 관계 분석
  - 장점: 복잡한 관계 쿼리
```

### 2.4 ML/NLP Libraries

```yaml
# 딥러닝 프레임워크
PyTorch: 2.0+
  - 모델 학습/추론
  - CUDA 지원

# 모델 허브
HuggingFace Transformers: 4.30+
  - 사전학습 모델
  - Tokenizer
  - Pipeline

# 파인튜닝
PEFT (LoRA): 0.5.0+
  - Low-Rank Adaptation
  - 경량 파인튜닝

# 전통적 ML
Scikit-learn: 1.3+
  - 분류, 회귀
  - 클러스터링
```

---

## 3. RAG 파이프라인

### 3.1 문서 인덱싱 (Offline)

```python
from langchain.document_loaders import PDFLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.embeddings import OpenAIEmbeddings
from langchain.vectorstores import Chroma

# 1. 문서 로드
documents = PDFLoader("company_hr_policy.pdf").load()

# 2. 텍스트 분할
splitter = RecursiveCharacterTextSplitter(
    chunk_size=1000,        # 청크 크기
    chunk_overlap=200,      # 중복 영역
    separators=["\n\n", "\n", " ", ""]
)
chunks = splitter.split_documents(documents)

# 3. 임베딩 생성 & 벡터 저장
embeddings = OpenAIEmbeddings(model="text-embedding-3-small")
vectorstore = Chroma.from_documents(
    documents=chunks,
    embedding=embeddings,
    collection_name=f"company_{company_id}_docs",
    persist_directory="./chroma_db"
)
```

#### 인덱싱 프로세스

```
PDF/Markdown 문서
  ↓
[Document Loader] 문서 로드
  ↓
[Text Splitter] 청크 분할 (1000자, 200자 오버랩)
  ↓
[Embedding Model] 벡터 변환 (1536차원)
  ↓
[Vector Store] ChromaDB 저장
  ↓
company_1001_docs 컬렉션
```

### 3.2 질문 응답 (Online)

```python
from langchain.chains import RetrievalQA
from langchain.llms import OpenAI

# 1. Retriever 설정
retriever = vectorstore.as_retriever(
    search_type="similarity",
    search_kwargs={"k": 3}  # 상위 3개 문서
)

# 2. QA Chain 구성
qa_chain = RetrievalQA.from_chain_type(
    llm=OpenAI(model="gpt-4-turbo-preview", temperature=0.7),
    chain_type="stuff",  # 모든 문서를 컨텍스트에 포함
    retriever=retriever,
    return_source_documents=True
)

# 3. 질문 처리
result = qa_chain({
    "query": "복지카드는 어떻게 신청하나요?"
})

# 응답
# result["result"]: "복지카드는 인사팀 포털에서..."
# result["source_documents"]: [Document(...), Document(...)]
```

#### 질문 응답 프로세스

```
사용자 질문: "복지카드 신청 방법은?"
  ↓
[Embedding] 질문 벡터화
  ↓
[Vector Search] 유사도 검색 (Top 3)
  ↓
관련 문서 3개 추출
  ↓
[LLM Prompt] 컨텍스트 + 질문 구성
  ↓
[GPT-4] 답변 생성
  ↓
답변 + 출처 문서 반환
```

### 3.3 프롬프트 엔지니어링

```python
# RAG 프롬프트 템플릿
PROMPT_TEMPLATE = """
당신은 신입사원 온보딩을 돕는 AI 비서입니다.

다음 문서를 참고하여 질문에 답변해주세요:
{context}

질문: {question}

답변 가이드라인:
1. 문서 내용을 기반으로 정확히 답변하세요
2. 문서에 없는 내용은 "해당 정보를 찾을 수 없습니다"라고 말하세요
3. 친절하고 이해하기 쉽게 설명하세요
4. 필요시 단계별로 설명하세요

답변:
"""
```

---

## 4. LangGraph Agent

### 4.1 Agent 워크플로우

```python
from langgraph.graph import StateGraph, END
from typing import TypedDict, List

# Agent 상태 정의
class AgentState(TypedDict):
    user_query: str
    company_id: int
    retrieved_docs: List[str]
    answer: str
    confidence: float
    needs_regeneration: bool

# 노드 함수들
def retrieve_documents(state: AgentState):
    """벡터 DB에서 관련 문서 검색"""
    docs = vectorstore.similarity_search(
        state["user_query"],
        filter={"company_id": state["company_id"]},
        k=3
    )
    return {"retrieved_docs": [doc.page_content for doc in docs]}

def generate_answer(state: AgentState):
    """LLM으로 답변 생성"""
    context = "\n\n".join(state["retrieved_docs"])
    prompt = f"Context: {context}\n\nQuestion: {state['user_query']}\n\nAnswer:"
    
    answer = llm.invoke(prompt)
    confidence = calculate_confidence(answer, state["retrieved_docs"])
    
    return {
        "answer": answer,
        "confidence": confidence
    }

def verify_answer(state: AgentState):
    """답변 검증"""
    if state["confidence"] < 0.7:
        return {"needs_regeneration": True}
    return {"needs_regeneration": False}

# 조건부 라우팅
def should_regenerate(state: AgentState):
    if state["needs_regeneration"]:
        return "regenerate"
    return "finish"

# 워크플로우 그래프 구성
workflow = StateGraph(AgentState)

# 노드 추가
workflow.add_node("retrieve", retrieve_documents)
workflow.add_node("generate", generate_answer)
workflow.add_node("verify", verify_answer)

# 엣지 정의
workflow.set_entry_point("retrieve")
workflow.add_edge("retrieve", "generate")
workflow.add_edge("generate", "verify")
workflow.add_conditional_edges(
    "verify",
    should_regenerate,
    {
        "regenerate": "generate",  # 재생성
        "finish": END              # 종료
    }
)

# 컴파일 & 실행
app = workflow.compile()
result = app.invoke({
    "user_query": "연차 신청 방법",
    "company_id": 1001
})
```

### 4.2 Agent 흐름도

```
START
  ↓
[retrieve] 문서 검색
  ↓
[generate] 답변 생성
  ↓
[verify] 신뢰도 검증
  ↓
신뢰도 < 0.7? ─Yes→ [generate] 재생성
  │
  No
  ↓
END (답변 반환)
```

---

## 5. Vector Database

### 5.1 ChromaDB 구조

```python
# 회사별 컬렉션 생성
collection = client.create_collection(
    name=f"company_{company_id}_documents",
    metadata={"company_id": company_id}
)

# 문서 추가
collection.add(
    documents=["1. 연차 신청은 인사 포털에서..."],
    metadatas=[{
        "company_id": 1001,
        "category": "HR",
        "document_id": "hr_policy_001",
        "title": "인사 규정"
    }],
    ids=["doc_hr_001"]
)

# 검색
results = collection.query(
    query_texts=["복지카드 신청"],
    n_results=3,
    where={"company_id": 1001}  # 회사별 필터링
)
```

#### 컬렉션 구조

```
ChromaDB
├── company_1001_documents
│   ├── doc_hr_001
│   │   ├── embedding: [0.123, -0.456, ..., 0.789]  # 1536차원
│   │   ├── metadata: {
│   │   │     "company_id": 1001,
│   │   │     "category": "HR",
│   │   │     "title": "인사 규정"
│   │   │   }
│   │   └── document: "1. 채용 절차..."
│   └── doc_it_001
│       └── ...
└── company_1002_documents
    └── ...
```

### 5.2 FAISS 인덱스 (대용량)

```python
import faiss
from langchain.vectorstores import FAISS

# FAISS 인덱스 생성
vectorstore = FAISS.from_documents(
    documents=chunks,
    embedding=embeddings
)

# 저장 (회사별)
vectorstore.save_local(f"./faiss_index/company_{company_id}")

# 로드
vectorstore = FAISS.load_local(
    f"./faiss_index/company_{company_id}",
    embeddings
)

# 검색 (매우 빠름)
docs = vectorstore.similarity_search("연차 신청", k=5)
```

---

## 6. LLM 전략

### 6.1 OpenAI API (기본)

```python
from langchain.llms import OpenAI

llm = OpenAI(
    model="gpt-4-turbo-preview",
    temperature=0.7,
    max_tokens=1000,
    top_p=0.9
)

response = llm.invoke("복지카드 신청 방법 설명")
```

### 6.2 오픈소스 LLM (vLLM)

```python
from vllm import LLM, SamplingParams

# vLLM 서버 시작
llm = LLM(
    model="meta-llama/Meta-Llama-3-8B-Instruct",
    tensor_parallel_size=2,
    gpu_memory_utilization=0.9
)

# 추론
sampling_params = SamplingParams(
    temperature=0.7,
    top_p=0.9,
    max_tokens=1000
)

outputs = llm.generate(
    prompts=["복지카드 신청 방법은?"],
    sampling_params=sampling_params
)
```

### 6.3 하이브리드 전략

```python
def select_llm(query: str, complexity: float):
    """질문 복잡도에 따라 LLM 선택"""
    
    if complexity < 0.3:
        # 간단한 질문 → Llama3 (비용 0원)
        return llm_llama3
    
    elif complexity < 0.7:
        # 중간 복잡도 → GPT-3.5 ($0.002/1K tokens)
        return llm_gpt35
    
    else:
        # 복잡한 질문 → GPT-4 ($0.03/1K tokens)
        return llm_gpt4

# 복잡도 계산
complexity = calculate_complexity(query)
llm = select_llm(query, complexity)
```

#### 비용 절감 효과

```
전체 질문 분포:
- 간단 (70%): Llama3 → $0
- 중간 (20%): GPT-3.5 → $0.002/1K
- 복잡 (10%): GPT-4 → $0.03/1K

→ 약 85% 비용 절감
```

---

## 7. Fine-tuning

### 7.1 LoRA Fine-tuning

```python
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig, get_peft_model, TaskType

# 베이스 모델 로드
model = AutoModelForCausalLM.from_pretrained(
    "meta-llama/Llama-2-7b-hf",
    load_in_8bit=True,  # 메모리 절약
    device_map="auto"
)

# LoRA 설정
lora_config = LoraConfig(
    task_type=TaskType.CAUSAL_LM,
    r=16,  # Low-rank dimension
    lora_alpha=32,
    lora_dropout=0.05,
    target_modules=["q_proj", "v_proj"]  # 어텐션 레이어만
)

# LoRA 모델 생성
model = get_peft_model(model, lora_config)

# 학습 가능한 파라미터 확인
model.print_trainable_parameters()
# trainable params: 4,194,304 || all params: 6,742,609,920 || trainable%: 0.06%
```

### 7.2 회사별 맞춤 학습

```python
from datasets import load_dataset

# 회사 A 데이터로 학습
company_a_dataset = load_dataset(
    "json",
    data_files=f"training_data/company_{company_id}.jsonl"
)

# 학습
trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=company_a_dataset
)
trainer.train()

# 저장
model.save_pretrained(f"./models/company_{company_id}_lora")
```

### 7.3 LoRA 장점

- ✅ **메모리 효율**: 전체 모델의 0.1%만 학습
- ✅ **빠른 학습**: 학습 시간 단축
- ✅ **회사별 특화**: 각 회사 용어/문화 반영
- ✅ **쉬운 배포**: 베이스 모델 + LoRA 가중치

---

## 8. 캐싱 & 최적화

### 8.1 Redis 캐싱

```python
import redis
import hashlib
import json

redis_client = redis.Redis(
    host='localhost',
    port=6379,
    db=0,
    decode_responses=True
)

def get_cached_answer(query: str, company_id: int):
    # 캐시 키 생성
    cache_key = hashlib.md5(
        f"{company_id}:{query}".encode()
    ).hexdigest()
    
    # 캐시 조회
    cached = redis_client.get(cache_key)
    if cached:
        print("Cache HIT")
        return json.loads(cached)
    
    print("Cache MISS")
    
    # LLM 호출
    answer = qa_chain({"query": query})
    
    # 캐시 저장 (24시간)
    redis_client.setex(
        cache_key,
        86400,  # TTL: 24시간
        json.dumps(answer)
    )
    
    return answer
```

### 8.2 비용 추적

```python
class CostTracker:
    def __init__(self):
        self.costs = {
            "gpt-4": 0.03 / 1000,      # $0.03 per 1K tokens
            "gpt-3.5": 0.002 / 1000,   # $0.002 per 1K tokens
            "llama3": 0.0              # 자체 호스팅
        }
    
    def calculate_cost(self, model: str, tokens: int):
        return self.costs.get(model, 0) * tokens
    
    def log_usage(self, company_id: int, model: str, tokens: int):
        cost = self.calculate_cost(model, tokens)
        
        # DB에 기록
        usage_log = {
            "company_id": company_id,
            "model": model,
            "tokens": tokens,
            "cost": cost,
            "timestamp": datetime.now()
        }
        
        db.save(usage_log)
        
        return cost
```

### 8.3 모니터링 (LangSmith)

```python
from langsmith import Client

client = Client()

# 체인 실행 추적
with client.trace(
    project_name="withbuddy-ai",
    tags=[f"company_{company_id}", "rag", "qa"]
):
    result = qa_chain({"query": query})

# 메트릭 수집
# - 응답 시간
# - 토큰 사용량
# - 에러율
# - 사용자 피드백
```

---

## 부록

### A. 성능 벤치마크

| 모델 | 응답 시간 | 비용/1K tokens | 품질 점수 |
|------|-----------|---------------|----------|
| GPT-4 | 2.5s | $0.03 | 9.2/10 |
| GPT-3.5 | 1.2s | $0.002 | 7.8/10 |
| Llama3-8B | 0.8s | $0 | 7.5/10 |
| Llama3-70B | 1.5s | $0 | 8.5/10 |

### B. 참고 자료

- [LangChain Documentation](https://python.langchain.com/)
- [LangGraph Guide](https://langchain-ai.github.io/langgraph/)
- [vLLM Documentation](https://docs.vllm.ai/)
- [ChromaDB Guide](https://docs.trychroma.com/)
- [LoRA Paper](https://arxiv.org/abs/2106.09685)

---

**문서 버전**: 1.0.0  
**작성일**: 2026-03-17
