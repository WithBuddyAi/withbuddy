from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, AsyncIterator, Literal

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field


app = FastAPI(title="WithBuddy AI", version="0.1.0")


class MessageItem(BaseModel):
    senderType: Literal["USER", "BOT"] | str
    messageType: Literal["QUESTION", "ANSWER"] | str
    content: str


class UserPayload(BaseModel):
    id: int | str
    companyId: int | str
    name: str
    hireDate: str | None = None


class DocumentPayload(BaseModel):
    id: int | str
    title: str
    content: str
    documentType: str | None = None
    department: str | None = None


class InternalAnswerRequest(BaseModel):
    user: UserPayload
    question: str = Field(min_length=1)
    messageHistory: list[MessageItem] = Field(default_factory=list)
    documents: list[DocumentPayload] = Field(default_factory=list)


class InternalAnswerResponse(BaseModel):
    answer: str
    sourceDocumentId: int | str | None = None


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)
    metadata: dict[str, Any] = Field(default_factory=dict)


@app.get("/health")
async def health() -> dict[str, str]:
    return {
        "status": "UP",
        "service": "withbuddy-ai",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/internal/ai/answer", response_model=InternalAnswerResponse)
async def internal_ai_answer(payload: InternalAnswerRequest) -> InternalAnswerResponse:
    source_id = payload.documents[0].id if payload.documents else None
    if payload.documents:
        answer = f"{payload.documents[0].title} 문서를 기준으로 답변합니다. {payload.question}"
    else:
        answer = f"질문을 확인했습니다. {payload.question}"
    return InternalAnswerResponse(answer=answer, sourceDocumentId=source_id)


@app.post("/chat")
async def chat(payload: ChatRequest) -> dict[str, str]:
    return {"answer": payload.message}


async def _stream_text(content: str) -> AsyncIterator[bytes]:
    for token in content.split():
        yield f"data: {token}\n\n".encode("utf-8")
    yield b"data: [DONE]\n\n"


@app.post("/chat/stream")
async def chat_stream(payload: ChatRequest) -> StreamingResponse:
    return StreamingResponse(_stream_text(payload.message), media_type="text/event-stream")
