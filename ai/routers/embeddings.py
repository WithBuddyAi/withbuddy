from fastapi import APIRouter
from pydantic import BaseModel
from core.embeddings import get_embeddings

router = APIRouter()


class EmbeddingRequest(BaseModel):
    companyCode: str
    content: str


class EmbeddingResponse(BaseModel):
    embeddingModel: str
    dimension: int
    embedding: list[float]


@router.post("/embeddings/question", response_model=EmbeddingResponse, tags=["embeddings"])
async def get_question_embedding(request: EmbeddingRequest):
    """no_result 질문 임베딩 생성 — BE가 unanswered_question_logs 저장 시 호출"""
    emb = get_embeddings().embed_query(request.content)
    return EmbeddingResponse(
        embeddingModel="models/gemini-embedding-2",
        dimension=len(emb),
        embedding=emb,
    )
