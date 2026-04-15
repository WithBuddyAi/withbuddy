"""
임베딩 모델 초기화 모듈
────────────────────────────────────────────
HuggingFace의 한국어 문장 임베딩 모델(jhgan/ko-sroberta-multitask)을 로드합니다.
싱글톤 패턴(@lru_cache)을 사용해 앱 실행 중 모델을 한 번만 로드합니다.
"""

from functools import lru_cache
from langchain_huggingface import HuggingFaceEmbeddings


@lru_cache(maxsize=1)
def get_embeddings() -> HuggingFaceEmbeddings:
    """
    HuggingFace 임베딩 모델 인스턴스 반환 (싱글톤)

    Returns:
        HuggingFaceEmbeddings: 한국어 문장 임베딩 모델
    """
    print("임베딩 모델 로드 중: jhgan/ko-sroberta-multitask")
    return HuggingFaceEmbeddings(
        model_name="jhgan/ko-sroberta-multitask",
        model_kwargs={"device": "cpu"},           # GPU 미사용 환경 기본값
        encode_kwargs={"normalize_embeddings": True},  # 코사인 유사도 계산 최적화
    )
