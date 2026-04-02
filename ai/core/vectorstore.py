"""
벡터 저장소 초기화 모듈
────────────────────────────────────────────
ChromaDB를 로컬 디스크(./chroma_db)에 영구 저장하고 관리합니다.
문서 검색기(Retriever)도 이 모듈에서 제공합니다.
싱글톤 패턴(@lru_cache)으로 앱 실행 중 한 번만 연결합니다.
"""

from functools import lru_cache

from langchain_chroma import Chroma
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


@lru_cache(maxsize=4)
def get_retriever(k: int = 3) -> VectorStoreRetriever:
    """
    문서 검색기(Retriever) 반환 (싱글톤)

    Args:
        k: 반환할 상위 유사 문서 개수 (기본값: 3)

    Returns:
        VectorStoreRetriever: 유사도 기반 문서 검색기
    """
    return get_vectorstore().as_retriever(
        search_type="similarity",
        search_kwargs={"k": k},
    )
