"""
임베딩 모델 초기화 모듈
────────────────────────────────────────────
Google Gemini Embedding 2 (gemini-embedding-2) 모델을 사용합니다.
싱글톤 패턴(@lru_cache)을 사용해 앱 실행 중 한 번만 초기화합니다.
"""

import os
from functools import lru_cache

from langchain_google_genai import GoogleGenerativeAIEmbeddings


@lru_cache(maxsize=1)
def get_embeddings() -> GoogleGenerativeAIEmbeddings:
    """
    Google Gemini Embedding 2 인스턴스 반환 (싱글톤)

    Returns:
        GoogleGenerativeAIEmbeddings: gemini-embedding-2
    """
    print("임베딩 모델 초기화: models/gemini-embedding-2")
    return GoogleGenerativeAIEmbeddings(
        model="models/gemini-embedding-2",
        google_api_key=os.getenv("GOOGLE_API_KEY"),
    )
