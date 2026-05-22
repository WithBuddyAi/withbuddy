"""
RAG (Retrieval-Augmented Generation) 체인 모듈
────────────────────────────────────────────
회사 문서를 ChromaDB에서 검색하고, 검색 결과를 컨텍스트로 삼아
Claude LLM이 답변을 생성하는 LCEL 파이프라인을 제공합니다.
멀티턴 대화 히스토리를 프롬프트에 포함시켜 이전 맥락을 유지합니다.
"""

import asyncio
import re
from typing import AsyncGenerator, Tuple, List

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.documents import Document
from langchain_core.messages import BaseMessage
from langchain_core.output_parsers import StrOutputParser

from core.llm import get_llm
from memory.chat_history import get_chat_history, save_interaction
from memory.unanswered_store import add_unanswered
from utils.prompts import RAG_PROMPT, RAG_PROMPT_CACHED
from chains.retriever import (
    retrieve, async_retrieve,
    resolve_selection, check_ambiguous,
    get_company_name, get_hr_contact, get_it_contact, get_company_specific_rules,
)

class _TokenCounter(BaseCallbackHandler):
    def __init__(self):
        self.reset()

    def reset(self):
        self.input_tokens    = 0
        self.output_tokens   = 0
        self.cache_read      = 0
        self.cache_creation  = 0

    def on_llm_end(self, response, **kwargs):
        for gen_list in response.generations:
            for gen in gen_list:
                msg = getattr(gen, "message", None)
                if msg and hasattr(msg, "usage_metadata") and msg.usage_metadata:
                    u = msg.usage_metadata
                    self.input_tokens   += u.get("input_tokens", 0)
                    self.output_tokens  += u.get("output_tokens", 0)
                    d = u.get("input_token_details") or {}
                    self.cache_read     += d.get("cache_read", 0)
                    self.cache_creation += d.get("cache_creation", 0)


_token_counter      = _TokenCounter()
_counter_registered = False


def _ensure_counter():
    global _counter_registered
    if not _counter_registered:
        llm = get_llm()
        llm.callbacks = list(llm.callbacks or []) + [_token_counter]
        _counter_registered = True


def pop_token_usage() -> dict:
    """마지막 run_rag_chain 호출의 토큰 사용량을 반환하고 초기화."""
    result = {
        "input_tokens":   _token_counter.input_tokens,
        "output_tokens":  _token_counter.output_tokens,
        "cache_read":     _token_counter.cache_read,
        "cache_creation": _token_counter.cache_creation,
    }
    _token_counter.reset()
    return result


_last_category = ""


def pop_category() -> str:
    """마지막 RAG 검색의 카테고리를 반환하고 초기화."""
    global _last_category
    result = _last_category
    _last_category = ""
    return result


def _extract_category(docs: List[Document]) -> str:
    """검색된 문서에서 가장 많이 등장한 category 메타데이터를 반환."""
    if not docs:
        return ""
    from collections import Counter
    cats = [d.metadata.get("category", "") for d in docs if d.metadata.get("category")]
    if not cats:
        return ""
    return Counter(cats).most_common(1)[0][0]


def _fix_names(text: str) -> str:
    """'님 -' → '님:' 통일 및 어색한 문장 교체."""
    text = re.sub(r'님\s*[-–]', '님:', text)
    text = re.sub(r'어떤 부분이 필요한지에 따라 연락하[^\n\.\!]*[\.\!]?', '필요한 부분 확인 후 연락해보세요!', text)
    return text



def _detect_user_style(history: List[BaseMessage], current_question: str) -> str:
    """
    기본은 존댓말 고정. 사용자가 명시적으로 반말을 요청한 경우에만 반말로 전환.
    """
    _CASUAL_REQUESTS = ["반말로", "반말 해", "반말해", "편하게 말해", "친구처럼", "캐주얼하게", "반말로 해줘", "편하게 해줘"]
    q = current_question.strip()
    if any(kw in q for kw in _CASUAL_REQUESTS):
        return (
            "사용자가 반말을 요청했습니다. 지금부터 친근한 반말(~해, ~야, ~거든, ~어, ~줘)로 답변하고 이후에도 유지하세요. "
            "🚫 ~습니다, ~요, ~세요 사용 금지."
        )
    return ""  # 기본값: 프롬프트의 존댓말 고정 규칙 적용

# ── 2-6: 근로기준법 fallback ──────────────────────────────────
_LABOR_LAW_KEYWORDS = ["근로기준법", "노동법", "최저임금법", "산업안전보건법", "고용노동부", "노동자 권리", "근로자 권리"]
def _get_labor_law_fallback(company_code: str) -> str:
    team, _ = get_hr_contact(company_code)
    return f"\n\n📌 근로기준법 관련 정확한 내용은 **{team}**께 확인하시는 게 가장 정확해요."


def _needs_labor_law_fallback(question: str, answer: str) -> bool:
    """근로기준법 관련 질문이고 RAG가 문서를 못 찾은 경우 True."""
    return (
        any(kw in question for kw in _LABOR_LAW_KEYWORDS)
        and any(kw in answer for kw in _NO_ANSWER_KEYWORDS)
    )


_NO_ANSWER_KEYWORDS = [
    "문서에서 확인되지",
    "관련 정보를 찾을 수 없",
    "확인되지 않습니다",
    "답변하기 어렵",
    # Haiku 모델이 실제로 사용하는 표현들
    "안내가 없습니다",
    "내용이 없습니다",
    "보유한 문서에는",
    "문서에는",
    "찾을 수 없습니다",
    "포함되어 있지 않",
    "정보가 없",
    "문서에 없어서",
    "안내드리기 어려워",
]

def _is_unanswered(answer: str, docs: List[Document]) -> bool:
    if not docs:
        return True
    return any(kw in answer for kw in _NO_ANSWER_KEYWORDS)



def _extract_contact_from_docs(docs: List[Document]) -> str | None:
    """
    검색된 문서 청크에서 '담당 부서 / 문의처:' 필드를 파싱합니다.
    문서 헤더 예시: "담당 부서 / 문의처: 경영지원팀 김지수 매니저 (Slack @jisoo.kim)"
    """
    pattern = re.compile(r'담당 부서\s*/\s*문의처\s*:\s*(.+)')
    for doc in docs:
        m = pattern.search(doc.page_content)
        if m:
            return m.group(1).strip()
    return None

async def _fire_unanswered_alert(user_id: str, question: str, company_code: str = "") -> None:
    """미답변 저장 + Slack 알림 + nudge Task 등록 (백그라운드)"""
    try:
        from tasks.slack_notifier import notify_unanswered_question
        qid = add_unanswered(user_id, question)
        await notify_unanswered_question(user_id, question, qid)
    except Exception:
        pass
    try:
        from core.be_client import enqueue_nudge
        enqueue_nudge(user_id, company_code, question, str(qid))
    except Exception:
        pass

_chain = None
_dedup_llm = None


def _get_dedup_llm():
    global _dedup_llm
    if _dedup_llm is None:
        import os
        from langchain_anthropic import ChatAnthropic
        _dedup_llm = ChatAnthropic(
            model="claude-haiku-4-5-20251001",
            anthropic_api_key=os.getenv("ANTHROPIC_API_KEY"),
            temperature=0,
            max_tokens=2048,
        )
    return _dedup_llm


def _has_duplicate_lines(text: str) -> bool:
    """연속 중복 줄 빠른 감지."""
    lines = [l.strip() for l in text.split('\n') if l.strip()]
    for i in range(len(lines) - 1):
        if lines[i] == lines[i + 1]:
            return True
    return False


def _dedup_answer(text: str) -> str:
    """중복 문장·단락이 감지되면 Haiku로 제거. 없으면 그대로 반환."""
    if not _has_duplicate_lines(text):
        return text
    from langchain_core.messages import HumanMessage
    result = _get_dedup_llm().invoke([HumanMessage(content=(
        "다음 텍스트에서 완전히 동일하거나 거의 동일한 중복 문장·단락을 하나만 남기고 제거하세요. "
        "내용·형식·말투·이모지는 절대 바꾸지 마세요. 설명 없이 정제된 텍스트만 반환하세요.\n\n"
        + text
    ))])
    return result.content


def _get_chain():
    global _chain
    if _chain is None:
        _chain = (RAG_PROMPT_CACHED | get_llm() | StrOutputParser()).with_config(
            {"tags": ["rag-chain"], "run_name": "withbuddy-rag"}
        )
    return _chain


def run_rag_chain(user_id: str, question: str, user_name: str = "", company_code: str = "", company_name: str = "", hire_date: str = "", injected_history: List[BaseMessage] | None = None) -> Tuple[str, str, List[dict], List[int]]:
    """
    RAG 체인을 실행하여 답변, 출처, 관련 양식 목록, 문서 ID 목록을 반환합니다.

    Returns:
        Tuple[str, str, List[dict], List[int]]: (AI 답변, 출처 문서명, 관련 양식 목록, 문서 ID 목록)
    """
    from routers.docs import find_related_docs

    _ensure_counter()
    _token_counter.reset()

    chat_history = injected_history if injected_history is not None else get_chat_history(user_id)

    result = retrieve(question, company_code, chat_history)

    if result.ambiguous_response:
        return result.ambiguous_response, "", [], []

    if result.direct_legal_answer:
        save_interaction(user_id, result.question, result.direct_legal_answer)
        related_docs = find_related_docs(result.question)
        return result.direct_legal_answer, result.source_names, related_docs, result.doc_ids

    formatted_context = result.formatted_context
    user_style = _detect_user_style(chat_history, result.question)
    company_name = company_name or get_company_name(company_code)
    hr_team, _ = get_hr_contact(company_code)

    from datetime import date as _date
    today_date = _date.today().strftime("%Y년 %m월 %d일")
    hire_info = ""
    if hire_date:
        try:
            diff = (_date.today() - _date.fromisoformat(hire_date)).days
            days = diff + 1
            hire_info = (
                f"\n[입사 일차 계산]\n"
                f"입사일: {hire_date} / 오늘: {_date.today().isoformat()}\n"
                f"날짜 차이: {diff}일 → 입사 당일을 1일차로 계산하므로 {diff}+1 = {days}일차\n"
                f"※ 반드시 입사 {days}일차로 답하세요."
            )
        except Exception:
            pass

    _PROFILE_KEYWORDS = ["팀장", "내 부서", "우리 팀", "내 팀", "나의 팀장", "누구야"]
    if any(kw in result.question for kw in _PROFILE_KEYWORDS):
        from memory.profile_store import format_profile_context, get_profile
        profile_ctx = format_profile_context(get_profile(user_id))
        if profile_ctx:
            formatted_context = f"[사용자 프로필]\n{profile_ctx}\n\n{formatted_context}"

    answer = _get_chain().invoke({
        "question": result.question,
        "context": formatted_context,
        "chat_history": chat_history,
        "user_style": user_style,
        "user_name": user_name,
        "company_name": company_name,
        "hr_team": hr_team,
        "it_contact": get_it_contact(company_code),
        "company_specific_rules": get_company_specific_rules(company_code),
        "today_date": today_date,
        "hire_info": hire_info,
    })
    answer = _fix_names(answer)
    answer = _dedup_answer(answer)

    # 2-6: 근로기준법 fallback
    if _needs_labor_law_fallback(result.question, answer):
        answer += _get_labor_law_fallback(company_code)

    # Case A: 회사 문서 없음 + 공통 법령 문서만 검색된 경우 안내 문구 추가
    if (company_code and result.docs and not _is_unanswered(answer, result.docs)
            and all(d.metadata.get("company_code", "") == "" for d in result.docs)):
        answer += f"\n\n참고로 이 답변은 공통 법령 문서를 기준으로 안내드렸어요. 회사별 세부 운영 방식은 다를 수 있으니, 실제 적용 전에는 {hr_team}에 한 번 확인해 주세요."

    # no_result 시 문서 헤더에서 담당자 정보 추출하여 삽입 (LLM이 이미 언급한 경우 중복 방지)
    if _is_unanswered(answer, result.docs):
        contact = _extract_contact_from_docs(result.docs)
        if contact:
            if contact not in answer:
                answer += f"\n\n관련 문의는 **{contact}** 에 직접 여쭤보시면 가장 빠를 거예요! 😊"
        else:
            if hr_team not in answer:
                answer += f"\n\n이 부분은 **{hr_team}**에 직접 여쭤보시면 가장 정확한 답을 얻으실 수 있어요!"

    global _last_category
    _last_category = _extract_category(result.docs)

    save_interaction(user_id, result.question, answer)
    related_docs = find_related_docs(result.question)

    return answer, result.source_names, related_docs, result.doc_ids


async def stream_rag_chain(user_id: str, question: str, user_name: str = "", company_code: str = "", company_name: str = "", hire_date: str = "", injected_history: List[BaseMessage] | None = None) -> AsyncGenerator[Tuple[str, str | None, List[dict] | None, List[int] | None], None]:
    """
    RAG 체인을 스트리밍으로 실행합니다.
    토큰 단위로 (chunk, None, None, None)을 yield하고, 마지막에 ("", source_names, related_docs, rag_doc_ids)를 yield합니다.
    """
    from routers.docs import find_related_docs

    _ensure_counter()
    _token_counter.reset()

    chat_history = injected_history if injected_history is not None else get_chat_history(user_id)

    # 모호한 질문은 __STAGE__searching 전에 처리 (UX: 검색 중 표시 안 함)
    resolved_q = resolve_selection(question, chat_history)
    if resolved_q:
        question = resolved_q
    ambiguous = check_ambiguous(question)
    if ambiguous:
        save_interaction(user_id, question, ambiguous)
        yield ambiguous, None, None, None
        yield "", "", [], []
        return

    yield "__STAGE__searching", None, None, None

    loop = asyncio.get_event_loop()
    result = await async_retrieve(question, company_code, chat_history)

    if result.direct_legal_answer:
        save_interaction(user_id, result.question, result.direct_legal_answer)
        related_docs = find_related_docs(result.question)
        yield result.direct_legal_answer, None, None, None
        yield "", result.source_names, related_docs, result.doc_ids
        return

    formatted_context = result.formatted_context
    user_style = _detect_user_style(chat_history, result.question)
    company_name = company_name or get_company_name(company_code)
    hr_team, _ = get_hr_contact(company_code)

    from datetime import date as _date
    today_date = _date.today().strftime("%Y년 %m월 %d일")
    hire_info = ""
    if hire_date:
        try:
            diff = (_date.today() - _date.fromisoformat(hire_date)).days
            days = diff + 1
            hire_info = (
                f"\n[입사 일차 계산]\n"
                f"입사일: {hire_date} / 오늘: {_date.today().isoformat()}\n"
                f"날짜 차이: {diff}일 → 입사 당일을 1일차로 계산하므로 {diff}+1 = {days}일차\n"
                f"※ 반드시 입사 {days}일차로 답하세요."
            )
        except Exception:
            pass

    _PROFILE_KEYWORDS = ["팀장", "내 부서", "우리 팀", "내 팀", "나의 팀장", "누구야"]
    if any(kw in result.question for kw in _PROFILE_KEYWORDS):
        from memory.profile_store import format_profile_context, get_profile
        profile_ctx = format_profile_context(get_profile(user_id))
        if profile_ctx:
            formatted_context = f"[사용자 프로필]\n{profile_ctx}\n\n{formatted_context}"

    yield "__STAGE__generating", None, None, None

    full_answer = ""
    async for chunk in _get_chain().astream({
        "question": result.question,
        "context": formatted_context,
        "chat_history": chat_history,
        "user_style": user_style,
        "user_name": user_name,
        "company_name": company_name,
        "hr_team": hr_team,
        "it_contact": get_it_contact(company_code),
        "company_specific_rules": get_company_specific_rules(company_code),
        "today_date": today_date,
        "hire_info": hire_info,
    }):
        full_answer += chunk
        yield chunk, None, None, None

    # 2-6: 근로기준법 fallback
    if _needs_labor_law_fallback(result.question, full_answer):
        labor_fallback = _get_labor_law_fallback(company_code)
        yield labor_fallback, None, None, None
        full_answer += labor_fallback

    # 이름 교정 + 중복 제거: 스트리밍 완료 후 교정본 전송
    fixed = _fix_names(full_answer)
    fixed = await loop.run_in_executor(None, _dedup_answer, fixed)
    if fixed != full_answer:
        yield "\x00" + fixed, None, None, None  # \x00 prefix → 프론트에서 전체 교체 신호

    # Case A: 회사 문서 없음 + 공통 법령 문서만 검색된 경우 안내 문구 추가
    if (company_code and result.docs and not _is_unanswered(fixed, result.docs)
            and all(d.metadata.get("company_code", "") == "" for d in result.docs)):
        case_a_msg = f"\n\n참고로 이 답변은 공통 법령 문서를 기준으로 안내드렸어요. 회사별 세부 운영 방식은 다를 수 있으니, 실제 적용 전에는 {hr_team}에 한 번 확인해 주세요."
        yield case_a_msg, None, None, None
        fixed += case_a_msg

    # 미답변 감지 → 문서 헤더에서 담당자 정보 추출하여 삽입 + 백그라운드 Slack 알림 (LLM이 이미 언급한 경우 중복 방지)
    if _is_unanswered(full_answer, result.docs):
        contact = _extract_contact_from_docs(result.docs)
        contact_msg = ""
        if contact:
            if contact not in fixed:
                contact_msg = f"\n\n관련 문의는 **{contact}** 에 직접 여쭤보시면 가장 빠를 거예요! 😊"
        else:
            if hr_team not in fixed:
                contact_msg = f"\n\n이 부분은 **{hr_team}**에 직접 여쭤보시면 가장 정확한 답을 얻으실 수 있어요!"
        if contact_msg:
            yield contact_msg, None, None, None
            fixed += contact_msg
        asyncio.create_task(_fire_unanswered_alert(user_id, result.question, company_code))

    global _last_category
    _last_category = _extract_category(result.docs)

    save_interaction(user_id, result.question, fixed)
    related_docs = find_related_docs(result.question)

    yield "", result.source_names, related_docs, result.doc_ids
