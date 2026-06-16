"""
벡터 저장소 초기화 모듈
────────────────────────────────────────────
ChromaDB를 로컬 디스크(./chroma_db)에 영구 저장하고 관리합니다.
문서 검색기(Retriever)도 이 모듈에서 제공합니다.
싱글톤 패턴(@lru_cache)으로 앱 실행 중 한 번만 연결합니다.
"""

import hashlib
import logging
from concurrent.futures import ThreadPoolExecutor
from functools import lru_cache
from typing import Any, List, Optional

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.vectorstores import VectorStoreRetriever
from core.embeddings import get_embeddings

logger = logging.getLogger(__name__)

# BM25 인덱스 캐시 (company_code → BM25Retriever)
_bm25_cache: dict[str, Any] = {}

# ChromaDB 영구 저장 경로
CHROMA_DB_PATH = "./chroma_db"

# ChromaDB 컬렉션 이름 (gemini-embedding-2 전용, cosine distance)
COLLECTION_NAME = "onboarding_docs_v2"


@lru_cache(maxsize=1)
def get_vectorstore() -> Chroma:
    """
    ChromaDB 벡터 저장소 인스턴스 반환 (싱글톤)

    Returns:
        Chroma: 영구 저장된 ChromaDB 인스턴스
    """
    return Chroma(
        collection_name=COLLECTION_NAME,
        embedding_function=get_embeddings(),
        persist_directory=CHROMA_DB_PATH,
        collection_metadata={"hnsw:space": "cosine"},
    )


@lru_cache(maxsize=16)
def get_retriever(k: int = 3, company_code: str = "") -> VectorStoreRetriever:
    """
    문서 검색기(Retriever) 반환 (싱글톤)

    Args:
        k: 반환할 상위 유사 문서 개수 (기본값: 3)
        company_code: 회사 고유 ID — 지정 시 해당 회사 문서만 검색 (기본값: "" = 전체)

    Returns:
        VectorStoreRetriever: 유사도 기반 문서 검색기
    """
    search_kwargs: dict = {"k": k}
    if company_code:
        search_kwargs["filter"] = {"company_code": company_code}
    return get_vectorstore().as_retriever(
        search_type="similarity",
        search_kwargs=search_kwargs,
    )


def search_legal_docs(query: str, k: int = 7, score_threshold: float = 0.30) -> List[Document]:
    """
    document_type=LEGAL 메타데이터를 가진 법률 문서만 검색합니다.
    정제된 txt 파일(근로기준법, 최저임금법, 퇴직급여법 등)만 대상으로 합니다.
    """
    vs = get_vectorstore()
    raw = vs.similarity_search_with_relevance_scores(
        query, k=k, filter={"document_type": "LEGAL"}
    )
    return [doc for doc, score in raw if score >= score_threshold]


def _build_bm25_corpus(company_code: str) -> List[Document]:
    """ChromaDB에서 회사+공통 문서 전체 로드 (BM25 코퍼스용)"""
    vs = get_vectorstore()
    col = vs._collection
    docs: List[Document] = []

    if company_code:
        res = col.get(
            where={"$and": [{"company_code": company_code}, {"document_type": {"$ne": "TEMPLATE"}}]},
            include=["documents", "metadatas"],
        )
        for content, meta in zip(res["documents"], res["metadatas"]):
            docs.append(Document(page_content=content, metadata=meta or {}))

    common = col.get(
        where={"$and": [{"company_code": ""}, {"document_type": {"$ne": "TEMPLATE"}}]},
        include=["documents", "metadatas"],
    )
    for content, meta in zip(common["documents"], common["metadatas"]):
        docs.append(Document(page_content=content, metadata=meta or {}))

    return docs


_kiwi_instance = None


_BM25_CONTENT_TAGS = {"NNG", "NNP", "SL", "SH", "SN"}


@lru_cache(maxsize=512)
def _tokenize_ko(text: str) -> List[str]:
    """kiwipiepy 형태소 분석 — 명사/고유명사/외국어만 추출 (BM25 토큰화용)"""
    global _kiwi_instance
    try:
        from kiwipiepy import Kiwi
        if _kiwi_instance is None:
            # sbg: skip-bigram 경량 모델 (ARM neon 비지원 환경에서도 빠름), num_workers=0: 단일 스레드
            _kiwi_instance = Kiwi(model_type='sbg', num_workers=0)
        return [t.form for t in _kiwi_instance.tokenize(text)
                if t.tag in _BM25_CONTENT_TAGS]
    except Exception:
        return text.split()


def get_bm25_retriever(company_code: str, k: int = 5) -> Optional[Any]:
    """BM25Retriever 반환 (company_code별 캐시)"""
    try:
        from langchain_community.retrievers import BM25Retriever
    except ImportError:
        return None

    if company_code not in _bm25_cache:
        docs = _build_bm25_corpus(company_code)
        if not docs:
            _bm25_cache[company_code] = None
        else:
            _bm25_cache[company_code] = BM25Retriever.from_documents(docs, preprocess_func=_tokenize_ko)
            logger.info("BM25 인덱스 생성: %s (%d개 문서)", company_code or "공통", len(docs))

    retriever = _bm25_cache[company_code]
    if retriever is None:
        return None
    retriever.k = k
    return retriever


def invalidate_bm25_cache(company_code: str = "") -> None:
    """문서 추가/삭제 후 BM25 캐시 무효화"""
    if company_code:
        _bm25_cache.pop(company_code, None)
    else:
        _bm25_cache.clear()


def _rrf_merge(vec_docs: List[Document], bm25_docs: List[Document], k: int) -> List[Document]:
    """Reciprocal Rank Fusion으로 벡터 + BM25 결과 병합"""
    scores: dict[str, float] = {}
    doc_map: dict[str, Document] = {}

    for rank, doc in enumerate(vec_docs):
        key = doc.page_content[:120]
        scores[key] = scores.get(key, 0.0) + 1.0 / (rank + 60)
        doc_map[key] = doc

    for rank, doc in enumerate(bm25_docs):
        key = doc.page_content[:120]
        scores[key] = scores.get(key, 0.0) + 1.0 / (rank + 60)
        doc_map[key] = doc

    ranked = sorted(scores, key=lambda x: scores[x], reverse=True)
    return [doc_map[key] for key in ranked[:k]]


_SEARCH_CACHE_TTL = 7200  # 2시간 (문서 업로드 빈도 낮음, TTL로만 관리)


def _search_cache_key(query: str, company_code: str, category: str, k: int) -> str:
    raw = f"{query}|{company_code}|{category}|{k}"
    return "search:" + hashlib.md5(raw.encode()).hexdigest()


def _docs_to_json(docs: List[Document]) -> list:
    return [{"page_content": d.page_content, "metadata": d.metadata} for d in docs]


def _json_to_docs(data: list) -> List[Document]:
    return [Document(page_content=d["page_content"], metadata=d.get("metadata", {})) for d in data]


def search_with_company_fallback(query: str, k: int = 5, company_code: str = "", score_threshold: float = 0.40, category: str = "") -> List[Document]:
    """
    ST-027: company_code 기준 벡터 DB 격리 검색
    회사 특화 문서(company_code 일치) + 공통 문서(company_code 없음) OR 조건 검색.
    유사도 점수 임계값 이하의 무관한 문서를 필터링합니다.

    company_code가 없으면 전체 문서 검색.

    Args:
        query: 검색 쿼리
        k: 반환할 문서 수
        company_code: 회사 고유 ID
        score_threshold: 유사도 임계값 (0~1, 높을수록 관련성 높음, 기본 0.35)

    Returns:
        List[Document]: 중복 제거 + 점수 필터링된 검색 결과
    """
    # 캐시 체크
    _cache_key = _search_cache_key(query, company_code, category, k)
    try:
        from core.be_client import cache_get, cache_set
        cached = cache_get("search", _cache_key)
        if cached:
            return _json_to_docs(cached)
    except Exception:
        pass

    vs = get_vectorstore()

    def _filter_by_score(results: List[tuple]) -> List[Document]:
        """(Document, score) 리스트에서 임계값 이상만 반환."""
        return [doc for doc, score in results if score >= score_threshold]

    if not company_code:
        f = {"category": category} if category else {}
        raw = vs.similarity_search_with_relevance_scores(query, k=k, filter=f or None)
        return _filter_by_score(raw)

    # 필터 구성
    if category:
        company_filter = {"$and": [{"company_code": company_code}, {"category": category}, {"document_type": {"$ne": "TEMPLATE"}}]}
        common_filter  = {"$and": [{"company_code": ""},           {"category": category}, {"document_type": {"$ne": "TEMPLATE"}}]}
    else:
        company_filter = {"$and": [{"company_code": company_code}, {"document_type": {"$ne": "TEMPLATE"}}]}
        common_filter  = {"$and": [{"company_code": ""},           {"document_type": {"$ne": "TEMPLATE"}}]}

    bm25 = get_bm25_retriever(company_code, k=k)

    # 회사 벡터 / 공통 벡터 / BM25 병렬 실행
    def _vec_company():
        return vs.similarity_search_with_relevance_scores(query, k=k, filter=company_filter)

    def _vec_common():
        return vs.similarity_search_with_relevance_scores(query, k=k, filter=common_filter)

    def _bm25_search():
        return bm25.invoke(query) if bm25 else []

    with ThreadPoolExecutor(max_workers=3) as pool:
        f_company = pool.submit(_vec_company)
        f_common  = pool.submit(_vec_common)
        f_bm25    = pool.submit(_bm25_search)
        company_raw  = f_company.result()
        common_raw   = f_common.result()
        bm25_results = f_bm25.result() if bm25 else []

    company_docs = _filter_by_score(company_raw)
    common_docs  = _filter_by_score(common_raw)

    # 중복 제거 (page_content 기준)
    seen = set()
    merged = []
    for doc in company_docs + common_docs:
        key = doc.page_content[:100]
        if key not in seen:
            seen.add(key)
            merged.append(doc)

    if not merged:
        return []

    result = _rrf_merge(merged, bm25_results, k) if bm25_results else merged[:k]

    try:
        from core.be_client import cache_get, cache_set
        cache_set("search", _cache_key, _docs_to_json(result), _SEARCH_CACHE_TTL)
    except Exception:
        pass

    return result
