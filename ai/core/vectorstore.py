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
def get_retriever(k: int = 3, company_id: str = "") -> VectorStoreRetriever:
    """
    문서 검색기(Retriever) 반환 (싱글톤)

    Args:
        k: 반환할 상위 유사 문서 개수 (기본값: 3)
        company_id: 회사 고유 ID — 지정 시 해당 회사 문서만 검색 (기본값: "" = 전체)

    Returns:
        VectorStoreRetriever: 유사도 기반 문서 검색기
    """
    search_kwargs: dict = {"k": k}
    if company_id:
        search_kwargs["filter"] = {"company_id": company_id}
    return get_vectorstore().as_retriever(
        search_type="similarity",
        search_kwargs=search_kwargs,
    )


def search_with_company_fallback(query: str, k: int = 5, company_id: str = "") -> List[Document]:
    """
    ST-027: company_id 기준 벡터 DB 격리 검색
    회사 특화 문서(company_id 일치) + 공통 문서(company_id 없음) OR 조건 검색.

    company_id가 없으면 전체 문서 검색.

    Args:
        query: 검색 쿼리
        k: 반환할 문서 수
        company_id: 회사 고유 ID

    Returns:
        List[Document]: 중복 제거된 검색 결과
    """
    vs = get_vectorstore()

    if not company_id:
        return vs.similarity_search(query, k=k)

    # 회사 특화 문서 검색
    company_docs = vs.similarity_search(
        query, k=k, filter={"company_id": company_id}
    )

    # 공통 문서 검색 (company_id 메타데이터가 없는 문서)
    common_docs = vs.similarity_search(
        query, k=k, filter={"company_id": ""}
    )

    # 중복 제거 (page_content 기준)
    seen = set()
    merged = []
    for doc in company_docs + common_docs:
        key = doc.page_content[:100]
        if key not in seen:
            seen.add(key)
            merged.append(doc)

    return merged[:k]
