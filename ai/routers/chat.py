"""
채팅 API 라우터
────────────────────────────────────────────
POST /chat        : 일반 질의응답
POST /chat/stream : SSE 스트리밍 질의응답 (토큰 단위 실시간 출력)
"""

import asyncio
import json

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from agents.orchestrator import run_orchestrator
from chains.rag_chain import run_rag_chain, stream_rag_chain
from core.llm import get_llm
from memory.chat_history import clear_memory, get_chat_history, get_history_as_text, save_interaction
from utils.sensitive_filter import check_sensitive

router = APIRouter(tags=["chat"])

_SUMMARIZE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 With Buddy입니다.
아래는 수습사원과 AI 간의 누적 대화 내역입니다.
이 대화를 바탕으로 다음을 한국어로 간결하게 정리해주세요:

1. **주요 질문 요약** – 어떤 주제를 물어봤는지 bullet로 정리
2. **해결된 내용** – AI가 안내해준 핵심 정보
3. **아직 확인이 필요한 사항** – 추가로 알아두면 좋을 것들

[대화 내역]
{history}"""),
    ("human", "위 대화를 요약 정리해주세요."),
])


# ── 요청 / 응답 스키마 ──────────────────────

class ChatUser(BaseModel):
    userId: int = Field(..., description="사용자 고유 ID", example=1)
    name: str = Field("", description="사용자 이름")
    companyCode: str = Field("", description="회사 고유 ID (다중 테넌트 문서 격리)")


class ChatRequest(BaseModel):
    questionId: int = Field(None, description="질문 ID")
    user: ChatUser = Field(..., description="사용자 정보")
    content: str = Field(..., description="사용자 질문", example="연차 신청 방법이 뭐야?")


class ChatResponse(BaseModel):
    answer: str = Field(..., description="AI 답변")
    source: str = Field(..., description="답변에 사용된 출처 문서명")
    related_docs: list = Field(default_factory=list, description="관련 양식/가이드 문서 목록")


# ── 엔드포인트 ──────────────────────────────

@router.post("/chat/agent", response_model=ChatResponse)
async def chat_agent(request: ChatRequest):
    """도메인 툴 분리 AgentExecutor 기반 RAG (토큰 절감 테스트용)"""
    try:
        from chains.agent_rag_chain import run_agent_rag_chain
        answer, source, related_docs, _ = run_agent_rag_chain(
            str(request.user.userId), request.content, request.user.name, request.user.companyCode
        )
        return ChatResponse(answer=answer, source=source, related_docs=related_docs)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"에이전트 처리 중 오류: {str(e)}")


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """회사 문서 기반 RAG 질의응답 (일반)"""
    try:
        answer, source, related_docs, _ = run_rag_chain(str(request.user.userId), request.content, request.user.name, request.user.companyCode)
        return ChatResponse(answer=answer, source=source, related_docs=related_docs)
    except ValueError as e:
        raise HTTPException(status_code=500, detail=f"설정 오류: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"질의응답 처리 중 오류: {str(e)}")


@router.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """
    오케스트레이터 기반 멀티에이전트 질의응답 (SSE 스트리밍)
    토큰 단위로 실시간 응답을 전송합니다.
    """
    async def event_generator():
        try:
            from chains.rag_chain import _resolve_selection
            from memory.chat_history import get_chat_history as _get_history
            # 2-7: 숫자 선택("1"~"4")을 이전 ambiguous 응답과 매핑
            uid = str(request.user.userId)
            message = request.content
            resolved = _resolve_selection(message, _get_history(uid))
            if resolved:
                message = resolved

            result = run_orchestrator(uid, request.user.name, message)
            if result.intent != "rag":
                from chains.rag_chain import _fix_names
                is_team_cards = result.metadata.get("type") == "team_cards"
                fixed_answer = _fix_names(result.answer)
                save_interaction(uid, message, fixed_answer)
                yield f"data: {json.dumps({'text': fixed_answer}, ensure_ascii=False)}\n\n"
                yield f"data: {json.dumps({'done': True, 'source': result.intent, 'docs': [], 'team_cards': is_team_cards}, ensure_ascii=False)}\n\n"
                return

            async for chunk, source, related_docs in stream_rag_chain(uid, message, request.user.name, request.user.companyCode):
                if source is not None:
                    yield f"data: {json.dumps({'done': True, 'source': source, 'docs': related_docs or []}, ensure_ascii=False)}\n\n"
                elif isinstance(chunk, str) and chunk.startswith("__STAGE__"):
                    yield f"data: {json.dumps({'stage': chunk[9:]}, ensure_ascii=False)}\n\n"
                else:
                    yield f"data: {json.dumps({'text': chunk}, ensure_ascii=False)}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


_PDF_AGENT_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 With Buddy AI입니다.
사용자가 PDF 파일을 업로드했고, 아래는 그 파일을 마크다운으로 변환한 전체 내용입니다.

[변환된 문서 내용]
{markdown}

위 문서를 바탕으로 사용자의 요청에 성실하게 답변하세요.
- 문서 내용을 요약할 때는 핵심 항목을 bullet로 정리하세요.
- 사용자가 특정 내용을 묻는다면 해당 내용을 문서에서 찾아 정확히 답변하세요.
- 문서에 없는 내용은 없다고 솔직하게 말하세요.
{user_style}"""),
    ("placeholder", "{chat_history}"),
    ("human", "{question}"),
])


@router.post("/chat/pdf")
async def chat_pdf(
    file: UploadFile = File(...),
    user_id: int = Form(...),
    message: str = Form(""),
    user_name: str = Form(""),
):
    """PDF 파일을 업로드하면 AI가 변환 후 내용을 분석·설명합니다."""
    from chains.rag_chain import _detect_user_style
    from routers.pdf2md import _pdf_bytes_to_md

    if not (file.filename or "").lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="PDF 파일만 업로드할 수 있습니다.")

    data = await file.read()
    if len(data) > 50 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="파일 크기는 50 MB 이하여야 합니다.")

    try:
        md_text = _pdf_bytes_to_md(data)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"PDF 변환 실패: {e}")

    if not md_text.strip():
        raise HTTPException(status_code=422, detail="텍스트를 추출할 수 없는 PDF입니다.")

    user_q = message.strip() or "이 문서의 내용을 핵심만 요약해줘."
    uid = str(user_id)
    chat_history = get_chat_history(uid)
    user_style = _detect_user_style(chat_history, user_q)
    md_for_llm = md_text[:6000] + ("\n\n...(이하 생략)" if len(md_text) > 6000 else "")

    chain = _PDF_AGENT_PROMPT | get_llm() | StrOutputParser()
    answer = chain.invoke({
        "markdown": md_for_llm,
        "question": user_q,
        "chat_history": chat_history,
        "user_style": user_style,
    })

    display_q = f"📎 {file.filename}\n{user_q}" if user_q != "이 문서의 내용을 핵심만 요약해줘." else f"📎 {file.filename} — 내용 요약 요청"
    save_interaction(uid, display_q, answer)

    return {"answer": answer, "markdown": md_text, "filename": (file.filename or "document").removesuffix(".pdf")}


# ── 내부 AI 연동 (백엔드 → AI 서버) ────────────────────────────

class InternalAIAnswerUser(BaseModel):
    userId: int
    name: str = ""
    companyCode: str = ""


class ConversationTurn(BaseModel):
    role: str
    content: str

class InternalAIAnswerRequest(BaseModel):
    questionId: int
    user: InternalAIAnswerUser
    content: str
    conversationHistory: list[ConversationTurn] = Field(default_factory=list)

class InternalAIAnswerResponse(BaseModel):
    questionId: int
    messageType: str = "rag_answer"
    content: str
    documents: list = Field(default_factory=list, description="검색된 문서 ID 목록")
    recommendedContacts: list = Field(default_factory=list, description="no_result 시 추천 담당자 목록")


@router.post("/internal/ai/answer", response_model=InternalAIAnswerResponse, tags=["internal"])
async def internal_ai_answer(request: InternalAIAnswerRequest):
    """백엔드 서버 → AI 서버 내부 연동 엔드포인트 (10초 타임아웃)"""
    action, answer = check_sensitive(request.content, request.user.name)
    if action == "block":
        return InternalAIAnswerResponse(
            questionId=request.questionId,
            messageType="out_of_scope",
            content=answer,
        )

    # 오케스트레이터 intent 체크 — out_of_scope/chitchat은 RAG 건너뜀
    from agents.orchestrator import (
        _get_intent_chain, _get_chitchat_chain, _LABOR_LAW_KEYWORDS, _ARTICLE_PATTERN, _OUT_OF_SCOPE_MESSAGE, _OUT_OF_SCOPE_EXTERNAL_MESSAGE,
    )
    if not (any(kw in request.content for kw in _LABOR_LAW_KEYWORDS) or _ARTICLE_PATTERN.search(request.content)):
        try:
            async with asyncio.timeout(3):
                raw_intent = await asyncio.get_event_loop().run_in_executor(
                    None, lambda: _get_intent_chain().invoke({"message": request.content}).strip().lower()
                )
        except asyncio.TimeoutError:
            raw_intent = "rag"  # intent 체크 지연 시 RAG로 폴백
        if "out_of_scope_internal" in raw_intent:
            from routers.recommend import get_contact_for_question
            contact = await get_contact_for_question(request.user.companyCode, request.content)
            return InternalAIAnswerResponse(
                questionId=request.questionId,
                messageType="out_of_scope",
                content=_OUT_OF_SCOPE_MESSAGE,
                recommendedContacts=[contact],
            )
        if "out_of_scope_external" in raw_intent:
            return InternalAIAnswerResponse(
                questionId=request.questionId,
                messageType="out_of_scope",
                content=_OUT_OF_SCOPE_EXTERNAL_MESSAGE,
            )
        if "chitchat" in raw_intent:
            user_id = str(request.user.userId)
            from memory.chat_history import get_chat_history, save_interaction
            chat_history = get_chat_history(user_id)
            history_text = "\n".join(
                f"{'사용자' if m['role'] == 'human' else 'AI'}: {m['content']}"
                for m in chat_history[-6:]
            ) if chat_history else ""
            chitchat_answer = await asyncio.get_event_loop().run_in_executor(
                None,
                lambda: _get_chitchat_chain().invoke({
                    "message": request.content,
                    "user_style": "",
                    "chat_history": history_text,
                }),
            )
            save_interaction(user_id, request.content, chitchat_answer)
            return InternalAIAnswerResponse(
                questionId=request.questionId,
                messageType="chitchat",
                content=chitchat_answer,
            )

    from langchain_core.messages import HumanMessage, AIMessage
    injected_history = None
    if request.conversationHistory:
        injected_history = []
        for turn in request.conversationHistory:
            if turn.role == "user":
                injected_history.append(HumanMessage(content=turn.content))
            else:
                injected_history.append(AIMessage(content=turn.content))

    try:
        async with asyncio.timeout(18):
            answer, _, _, doc_ids = await asyncio.get_event_loop().run_in_executor(
                None,
                lambda: run_rag_chain(
                    str(request.user.userId),
                    request.content,
                    user_name=request.user.name,
                    company_code=request.user.companyCode,
                    injected_history=injected_history,
                )
            )
    except asyncio.TimeoutError:
        raise HTTPException(status_code=408, detail="AI 응답 시간이 초과되었습니다. 다시 시도해주세요.")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

    _NO_RESULT_KW = ["문서에서 확인되지", "관련 정보를 찾을 수 없", "확인되지 않습니다", "답변하기 어렵",
                     "안내가 없습니다", "내용이 없습니다", "보유한 문서에는", "문서에는",
                     "찾을 수 없습니다", "포함되어 있지 않", "정보가 없", "찾지 못했어요",
                     "알 수 없어요", "알 수 없습니다", "확인이 어렵", "파악이 어렵"]
    _OUT_OF_SCOPE_KW = ["서비스 범위", "담당 사수님과 직접"]

    if any(kw in answer for kw in _OUT_OF_SCOPE_KW):
        message_type = "out_of_scope"
    elif any(kw in answer for kw in _NO_RESULT_KW):
        message_type = "no_result"
    else:
        message_type = "rag_answer"

    recommended_contacts = []
    if message_type in ("no_result", "out_of_scope"):
        from routers.recommend import get_contact_for_question
        contact = await get_contact_for_question(request.user.companyCode, request.content)
        recommended_contacts = [contact]

    return InternalAIAnswerResponse(
        questionId=request.questionId,
        messageType=message_type,
        content=answer,
        documents=[{"documentId": did} for did in doc_ids],
        recommendedContacts=recommended_contacts,
    )


class SummarizeRequest(BaseModel):
    user_id: int = Field(..., description="사용자 고유 ID")


class SummarizeResponse(BaseModel):
    summary: str = Field(..., description="누적 대화 요약")


@router.post("/chat/clear")
async def clear_chat_history(request: SummarizeRequest):
    """대화 기록 초기화 (서버 side)"""
    clear_memory(str(request.user_id))
    return {"ok": True}


@router.post("/chat/summarize", response_model=SummarizeResponse)
async def summarize_history(request: SummarizeRequest):
    """누적 대화 내역을 AI가 요약·정리합니다."""
    try:
        history_text = get_history_as_text(str(request.user_id))
        if history_text == "대화 내역이 없습니다.":
            return SummarizeResponse(summary="아직 대화 내역이 없습니다. 먼저 질문을 몇 가지 해보세요!")
        chain = _SUMMARIZE_PROMPT | get_llm() | StrOutputParser()
        summary = chain.invoke({"history": history_text})
        return SummarizeResponse(summary=summary)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
