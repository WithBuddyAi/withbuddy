"""
@tool + AgentExecutor 기반 RAG 체인
────────────────────────────────────────────
강사님 피드백 반영: 분야별 툴 분리로 복합 질문 토큰 절감
- HR / ADMIN / IT / WELFARE / LEGAL 5개 도메인 툴
- LLM이 질문에 맞는 툴만 선택 → 불필요한 컨텍스트 제거
- 기존 /chat 엔드포인트는 유지, /chat/agent 로 테스트 가능
"""

from typing import List, Tuple

from langchain_core.documents import Document
from langchain_core.tools import tool
from langgraph.prebuilt import create_react_agent

from chains.rag_chain import _format_docs, _fix_names, _detect_user_style
from core.llm import get_llm
from core.vectorstore import search_legal_docs, search_with_company_fallback
from memory.chat_history import get_chat_history, save_interaction


# ── 에이전트 시스템 프롬프트 ─────────────────────────────────

_AGENT_SYSTEM_PROMPT = """당신은 With Buddy입니다. 수습사원의 온보딩을 돕는 AI 어시스턴트예요.

반드시 아래 툴을 사용해 회사 문서를 검색한 후 답변하세요.
- search_hr: 연차, 휴가, 병가, 육아휴직, 출산휴가, 급여, 재택근무
- search_admin: 경비처리, 법인카드, 사무용품, 명함, 출장
- search_it: IT 장비 신청, 계정 설정, VPN, 소프트웨어, 보안
- search_welfare: 복지카드, 건강검진, 동호회, 경조사 지원
- search_legal: 근로기준법, 최저임금, 퇴직금, 노동법령

[답변 규칙]
- 검색 결과를 기반으로만 답변하세요. 없는 내용은 지어내지 마세요.
- 검색 결과가 없으면 "경영지원팀에 직접 문의해 주세요"라고 안내하세요.
- 친근하고 따뜻한 존댓말로 답변하세요."""


# ── 도메인별 툴 팩토리 ───────────────────────────────────────

def _make_tools(company_code: str) -> List:
    """company_code를 클로저로 캡처한 도메인별 검색 툴 5개 반환."""

    @tool
    def search_hr(query: str) -> str:
        """연차, 휴가, 병가, 재택근무, 육아휴직, 출산휴가, 급여 등 HR/인사 정책을 검색합니다."""
        docs = search_with_company_fallback(query, k=5, company_code=company_code)
        return _format_docs(docs) if docs else "HR 관련 문서를 찾지 못했습니다."

    @tool
    def search_admin(query: str) -> str:
        """경비처리, 법인카드, 영수증, 사무용품, 명함, 출장비 등 행정/총무 정보를 검색합니다."""
        docs = search_with_company_fallback(query, k=5, company_code=company_code)
        return _format_docs(docs) if docs else "행정 관련 문서를 찾지 못했습니다."

    @tool
    def search_it(query: str) -> str:
        """IT 장비 신청, 계정 설정, VPN, 소프트웨어 설치, 보안 정책을 검색합니다."""
        docs = search_with_company_fallback(query, k=5, company_code=company_code)
        return _format_docs(docs) if docs else "IT 관련 문서를 찾지 못했습니다."

    @tool
    def search_welfare(query: str) -> str:
        """복지카드, 건강검진, 동호회, 경조사 지원, 복리후생 혜택을 검색합니다."""
        docs = search_with_company_fallback(query, k=5, company_code=company_code)
        return _format_docs(docs) if docs else "복리후생 관련 문서를 찾지 못했습니다."

    @tool
    def search_legal(query: str) -> str:
        """근로기준법, 최저임금법, 퇴직급여법, 남녀고용평등법 등 노동법령을 검색합니다."""
        docs = search_legal_docs(query, k=7)
        return _format_docs(docs) if docs else "관련 법령을 찾지 못했습니다."

    return [search_hr, search_admin, search_it, search_welfare, search_legal]


# ── 에이전트 캐시 (company_code별 싱글톤) ────────────────────
_agent_cache: dict = {}


def _get_agent(company_code: str):
    """company_code별 에이전트 캐싱 — 매 요청마다 생성하지 않음."""
    if company_code not in _agent_cache:
        tools = _make_tools(company_code)
        llm = get_llm()
        _agent_cache[company_code] = (create_react_agent(llm, tools, prompt=_AGENT_SYSTEM_PROMPT), tools)
    return _agent_cache[company_code]


# ── 내부 fallback 전용 (저장 없음) ───────────────────────────
def _run_agent_search(question: str, company_code: str, history: list) -> str:
    """
    에이전트 검색·답변만 수행하고 히스토리 저장은 하지 않습니다.
    RAG no_result fallback 용도. 호출자가 히스토리를 직접 관리합니다.
    """
    from langchain_core.messages import HumanMessage, AIMessage, ToolMessage
    agent, _ = _get_agent(company_code)
    messages = list(history) + [HumanMessage(content=question)]
    result = agent.invoke({"messages": messages})

    all_msgs = result.get("messages", [])
    tool_messages = [m for m in all_msgs if isinstance(m, ToolMessage)]
    if not tool_messages:
        return ""  # 툴 미사용 → fallback 효과 없음

    ai_messages = [m for m in all_msgs if isinstance(m, AIMessage)]
    return _fix_names(ai_messages[-1].content if ai_messages else "")


# ── 퍼블릭 API ───────────────────────────────────────────────

def run_agent_rag_chain(
    user_id: str,
    question: str,
    user_name: str = "",
    company_code: str = "",
) -> Tuple[str, str, List[dict], List[int]]:
    """
    @tool + AgentExecutor 기반 RAG 체인.
    LLM이 도메인 툴을 선택하여 검색 후 답변 생성.

    Returns:
        Tuple[str, str, List[dict], List[int]]: (답변, 출처, 관련문서, doc_ids)
    """
    chat_history = get_chat_history(user_id)
    user_style = _detect_user_style(chat_history, question)

    agent, _ = _get_agent(company_code)

    from langchain_core.messages import HumanMessage, AIMessage, ToolMessage
    messages = list(chat_history) + [HumanMessage(content=question)]
    result = agent.invoke({"messages": messages})

    all_msgs = result.get("messages", [])
    ai_messages = [m for m in all_msgs if isinstance(m, AIMessage)]
    answer = _fix_names(ai_messages[-1].content if ai_messages else "")

    # 툴 미사용 시 fallback
    tool_messages = [m for m in all_msgs if isinstance(m, ToolMessage)]
    if not tool_messages:
        answer = "관련 문서를 찾지 못했어요. 경영지원팀에 직접 문의해 주세요! 😊"

    used_tools = list({getattr(m, "name", "") for m in tool_messages if getattr(m, "name", "")})
    source = ", ".join(used_tools) if used_tools else "agent"

    save_interaction(user_id, question, answer)
    try:
        from routers.docs import find_related_docs
        related_docs = find_related_docs(question)
    except Exception:
        related_docs = []

    return answer, source, related_docs, []


async def stream_agent_rag_chain(
    user_id: str,
    question: str,
    user_name: str = "",
    company_code: str = "",
):
    """
    에이전트 RAG 체인 스트리밍 버전 (SSE 용).
    토큰 단위로 (텍스트, None, None)을 yield하고,
    마지막에 (None, source, related_docs)를 yield합니다.
    """
    from langchain_core.messages import HumanMessage, AIMessage, ToolMessage

    chat_history = get_chat_history(user_id)
    agent, _ = _get_agent(company_code)
    messages = list(chat_history) + [HumanMessage(content=question)]

    full_answer = ""
    used_tools: set[str] = set()

    async for event in agent.astream_events({"messages": messages}, version="v2"):
        kind = event.get("event", "")
        if kind == "on_tool_end":
            tool_name = event.get("name", "")
            if tool_name:
                used_tools.add(tool_name)
        if kind == "on_chat_model_stream":
            chunk = event.get("data", {}).get("chunk")
            if chunk and hasattr(chunk, "content") and chunk.content:
                # seq:step:1 태그는 첫 번째 LLM 판단 단계 → 스킵
                tags = event.get("tags", [])
                if "seq:step:1" not in tags:
                    text = chunk.content
                    full_answer += text
                    yield text, None, None

    full_answer = _fix_names(full_answer)
    source = ", ".join(used_tools) if used_tools else "agent"
    save_interaction(user_id, question, full_answer)

    try:
        from routers.docs import find_related_docs
        related_docs = find_related_docs(question)
    except Exception:
        related_docs = []

    yield None, source, related_docs
