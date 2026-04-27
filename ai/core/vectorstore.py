"""
벡터 저장소 초기화 모듈
────────────────────────────────────────────
ChromaDB를 로컬 디스크(./chroma_db)에 영구 저장하고 관리합니다.
문서 검색기(Retriever)도 이 모듈에서 제공합니다.
싱글톤 패턴(@lru_cache)으로 앱 실행 중 한 번만 연결합니다.
"""

from functools import lru_cache
from typing import List

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.vectorstores import VectorStoreRetriever
from core.embeddings import get_embeddings

# ChromaDB 영구 저장 경로
CHROMA_DB_PATH = "./chroma_db"

# ChromaDB 컬렉션 이름
COLLECTION_NAME = "onboarding_docs"


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
    filtered = [doc for doc, score in raw if score >= score_threshold]
    if not filtered and raw:
        filtered = [doc for doc, _ in raw[:1]]
    return filtered


def search_with_company_fallback(query: str, k: int = 5, company_code: str = "", score_threshold: float = 0.30, category: str = "") -> List[Document]:
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
    vs = get_vectorstore()

    def _filter_by_score(results: List[tuple]) -> List[Document]:
        """(Document, score) 리스트에서 임계값 이상만 반환."""
        return [doc for doc, score in results if score >= score_threshold]

    if not company_code:
        f = {"category": category} if category else {}
        raw = vs.similarity_search_with_relevance_scores(query, k=k, filter=f or None)
        filtered = _filter_by_score(raw)
        # 필터링 후 결과가 0개면 임계값 낮춰서 재시도 (최소 1개 보장)
        if not filtered:
            filtered = [doc for doc, _ in raw[:1]]
        return filtered

    # 회사 특화 문서 검색
    if category:
        company_filter = {"$and": [{"company_code": company_code}, {"category": category}]}
    else:
        company_filter = {"company_code": company_code}
    company_raw = vs.similarity_search_with_relevance_scores(
        query, k=k, filter=company_filter
    )
    company_docs = _filter_by_score(company_raw)

    # 공통 문서 검색 (company_code 메타데이터가 없는 문서)
    if category:
        common_filter = {"$and": [{"company_code": ""}, {"category": category}]}
    else:
        common_filter = {"company_code": ""}
    common_raw = vs.similarity_search_with_relevance_scores(
        query, k=k, filter=common_filter
    )
    common_docs = _filter_by_score(common_raw)

    # 중복 제거 (page_content 기준)
    seen = set()
    merged = []
    for doc in company_docs + common_docs:
        key = doc.page_content[:100]
        if key not in seen:
            seen.add(key)
            merged.append(doc)

    # 필터링 후 결과가 0개면 점수 무관하게 상위 1개 반환
    if not merged:
        all_raw = [(doc for doc, _ in company_raw)] + [(doc for doc, _ in common_raw)]
        fallback = [doc for doc, _ in (company_raw + common_raw)[:1]]
        return fallback

    return merged[:k]
