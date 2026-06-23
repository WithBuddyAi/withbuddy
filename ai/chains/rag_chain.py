"""
RAG (Retrieval-Augmented Generation) 체인 모듈
────────────────────────────────────────────
retriever.py(검색)와 generator.py(생성)를 조합하는 오케스트레이터.
토큰 카운팅, 카테고리 추적, 미답변 알림을 담당합니다.
"""

import asyncio
import re
from typing import AsyncGenerator, Tuple, List

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.documents import Document
from langchain_core.messages import BaseMessage

from core.llm import get_llm
from memory.chat_history import get_chat_history, save_interaction
from memory.unanswered_store import add_unanswered
from chains.retriever import (
    retrieve, async_retrieve,
    resolve_selection, check_ambiguous,
    get_company_name, get_hr_contact, get_it_contact, get_company_specific_rules,
    is_legal_question,
)
from chains.generator import (
    generate_answer, stream_answer, postprocess_answer, postprocess_answer_async,
    _fix_names, _detect_user_style,
    is_unanswered, needs_labor_law_fallback, get_labor_law_fallback,
    build_contact_suffix, build_case_a_suffix,
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
    """검색된 문서에서 가장 많이 등장한 document_type을 category로 반환."""
    if not docs:
        return ""
    from collections import Counter
    cats = [
        d.metadata.get("category") or d.metadata.get("document_type", "")
        for d in docs
        if d.metadata.get("category") or d.metadata.get("document_type")
    ]
    if not cats:
        return ""
    return Counter(cats).most_common(1)[0][0]


async def _fire_unanswered_alert(user_id: str, question: str, company_code: str = "", user_name: str = "") -> None:
    """미답변 저장 + Slack 알림 + nudge Task 등록 (백그라운드)"""
    try:
        from tasks.slack_notifier import notify_unanswered_question
        qid = add_unanswered(user_id, question, company_code)
        await notify_unanswered_question(user_id, question, qid, user_name=user_name, company_code=company_code)
    except Exception:
        pass
    try:
        from core.be_client import enqueue_nudge
        enqueue_nudge(user_id, company_code, question, str(qid))
    except Exception:
        pass


def _build_hire_info(hire_date: str) -> str:
    from datetime import date as _date
    if not hire_date:
        return ""
    try:
        today = _date.today()
        hd = _date.fromisoformat(hire_date)
        diff = (today - hd).days
        days = diff + 1
        months = diff // 30
        return (
            f"\n[입사 일차 계산]\n"
            f"입사일: {hire_date} / 오늘: {today.isoformat()}\n"
            f"날짜 차이: {diff}일 → 입사 당일을 1일차로 계산하므로 {diff}+1 = {days}일차 (약 {months}개월 경과)\n"
            f"※ 반드시 입사 {days}일차로 답하세요.\n"
            f"※ 문서에 '입사 N개월 후부터' 조건이 있을 때, 위 경과 개월수를 기준으로 판단하세요. 이미 조건을 충족했으면 '현재 지원 가능'으로 답하세요."
        )
    except Exception:
        return ""


def _inject_profile_context(user_id: str, question: str, formatted_context: str) -> str:
    _PROFILE_KEYWORDS = ["팀장", "내 부서", "우리 팀", "내 팀", "나의 팀장", "누구야"]
    if any(kw in question for kw in _PROFILE_KEYWORDS):
        from memory.profile_store import format_profile_context, get_profile
        profile_ctx = format_profile_context(get_profile(user_id))
        if profile_ctx:
            return f"[사용자 프로필]\n{profile_ctx}\n\n{formatted_context}"
    return formatted_context


def run_rag_chain(user_id: str, question: str, user_name: str = "", company_code: str = "", company_name: str = "", hire_date: str = "", injected_history: List[BaseMessage] | None = None) -> Tuple[str, str, List[dict], List[int]]:
    """
    RAG 체인을 실행하여 답변, 출처, 관련 양식 목록, 문서 ID 목록을 반환합니다.

    Returns:
        Tuple[str, str, List[dict], List[int]]: (AI 답변, 출처 문서명, 관련 양식 목록, 문서 ID 목록)
    """
    from routers.docs import find_related_docs
    from datetime import date as _date

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

    if not result.docs:
        hr_team, _ = get_hr_contact(company_code)
        no_result_answer = f"아직 이 질문에 답할 수 있는 사내 문서나 공통 기준을 찾지 못했어요.\n정확한 확인이 필요한 내용이라 **{hr_team}**에 직접 문의하시거나, 관리자에게 관련 문서 추가를 요청해 주세요."
        save_interaction(user_id, result.question, no_result_answer)
        return no_result_answer, "", [], []

    # 사내 문서 없이 법령 문서만 히트된 비법령 질문 → LLM 호출 없이 no_result 처리
    if (company_code and not is_legal_question(result.question)
            and all(d.metadata.get("company_code", "") == "" for d in result.docs)):
        hr_team, _ = get_hr_contact(company_code)
        no_result_answer = f"아직 이 질문에 답할 수 있는 사내 문서나 공통 기준을 찾지 못했어요.\n정확한 확인이 필요한 내용이라 **{hr_team}**에 직접 문의하시거나, 관리자에게 관련 문서 추가를 요청해 주세요."
        save_interaction(user_id, result.question, no_result_answer)
        return no_result_answer, "", [], []

    formatted_context = _inject_profile_context(user_id, result.question, result.formatted_context)
    company_name = company_name or get_company_name(company_code)
    hr_team, _ = get_hr_contact(company_code)

    answer = generate_answer(
        question=result.question,
        context=formatted_context,
        chat_history=chat_history,
        user_style=_detect_user_style(chat_history, result.question),
        user_name=user_name,
        company_name=company_name,
        hr_team=hr_team,
        it_contact=get_it_contact(company_code),
        company_specific_rules=get_company_specific_rules(company_code),
        today_date=_date.today().strftime("%Y년 %m월 %d일"),
        hire_info=_build_hire_info(hire_date),
    )

    if needs_labor_law_fallback(result.question, answer):
        answer += get_labor_law_fallback(hr_team)

    if (company_code and result.docs and not is_unanswered(answer, result.docs)
            and all(d.metadata.get("company_code", "") == "" for d in result.docs)):
        answer += build_case_a_suffix(hr_team)

    if is_unanswered(answer, result.docs):
        answer += build_contact_suffix(answer, result.docs, hr_team)

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
    from datetime import date as _date

    _ensure_counter()
    _token_counter.reset()

    chat_history = injected_history if injected_history is not None else get_chat_history(user_id)

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

    result = await async_retrieve(question, company_code, chat_history)

    if result.direct_legal_answer:
        save_interaction(user_id, result.question, result.direct_legal_answer)
        related_docs = find_related_docs(result.question)
        _legal = re.sub(r'\n\n(?=\*\*)', '\x00', result.direct_legal_answer)
        _legal = re.sub(r'\n\n', '\n', _legal)
        _legal = re.sub(r'\n', '  \n', _legal)
        _legal = _legal.replace('\x00', '\n\n')
        yield _legal, None, None, None
        yield "", result.source_names, related_docs, result.doc_ids
        return

    if not result.docs:
        hr_team, _ = get_hr_contact(company_code)
        no_result_answer = f"아직 이 질문에 답할 수 있는 사내 문서나 공통 기준을 찾지 못했어요.\n정확한 확인이 필요한 내용이라 **{hr_team}**에 직접 문의하시거나, 관리자에게 관련 문서 추가를 요청해 주세요."
        save_interaction(user_id, result.question, no_result_answer)
        asyncio.create_task(_fire_unanswered_alert(user_id, result.question, company_code, user_name=user_name))
        yield no_result_answer, None, None, None
        yield "", "", [], []
        return

    # 사내 문서 없이 법령 문서만 히트된 비법령 질문 → LLM 호출 없이 no_result 처리
    if (company_code and not is_legal_question(result.question)
            and all(d.metadata.get("company_code", "") == "" for d in result.docs)):
        hr_team, _ = get_hr_contact(company_code)
        no_result_answer = f"아직 이 질문에 답할 수 있는 사내 문서나 공통 기준을 찾지 못했어요.\n정확한 확인이 필요한 내용이라 **{hr_team}**에 직접 문의하시거나, 관리자에게 관련 문서 추가를 요청해 주세요."
        save_interaction(user_id, result.question, no_result_answer)
        asyncio.create_task(_fire_unanswered_alert(user_id, result.question, company_code, user_name=user_name))
        yield no_result_answer, None, None, None
        yield "", "", [], []
        return

    formatted_context = _inject_profile_context(user_id, result.question, result.formatted_context)
    company_name = company_name or get_company_name(company_code)
    hr_team, _ = get_hr_contact(company_code)

    yield "__STAGE__generating", None, None, None

    def _fmt(text: str) -> str:
        text = re.sub(r'\n\n(?=\*\*)', '\x00', text)
        text = re.sub(r'\n\n', '\n', text)
        text = re.sub(r'\n', '  \n', text)
        text = text.replace('\x00', '\n\n')
        return text

    full_answer = ""
    _buf = ""
    async for _raw in stream_answer(
        question=result.question,
        context=formatted_context,
        chat_history=chat_history,
        user_style=_detect_user_style(chat_history, result.question),
        user_name=user_name,
        company_name=company_name,
        hr_team=hr_team,
        it_contact=get_it_contact(company_code),
        company_specific_rules=get_company_specific_rules(company_code),
        today_date=_date.today().strftime("%Y년 %m월 %d일"),
        hire_info=_build_hire_info(hire_date),
    ):
        _buf += _raw
        while True:
            idx = _buf.find('\n\n')
            if idx == -1:
                break
            after = idx + 2
            if after >= len(_buf):
                break  # \n\n이 버퍼 끝 — 다음 청크 기다림
            if _buf[after:after + 2] == '**':
                out = _fmt(_buf[:idx]) + '\n\n'
            else:
                out = _fmt(_buf[:after])
            full_answer += out
            yield out, None, None, None
            _buf = _buf[after:]
        if len(_buf) > 2:
            safe = _fmt(_buf[:-2])
            full_answer += safe
            yield safe, None, None, None
            _buf = _buf[-2:]
    if _buf:
        final = _fmt(_buf)
        full_answer += final
        yield final, None, None, None

    fixed = await postprocess_answer_async(full_answer)
    if fixed != full_answer:
        yield "\x00" + fixed, None, None, None

    if needs_labor_law_fallback(result.question, fixed):
        labor_fallback = get_labor_law_fallback(hr_team)
        yield labor_fallback, None, None, None
        fixed += labor_fallback

    if (company_code and result.docs and not is_unanswered(fixed, result.docs)
            and all(d.metadata.get("company_code", "") == "" for d in result.docs)):
        case_a_msg = build_case_a_suffix(hr_team)
        yield case_a_msg, None, None, None
        fixed += case_a_msg

    if is_unanswered(full_answer, result.docs):
        contact_msg = build_contact_suffix(fixed, result.docs, hr_team)
        if contact_msg:
            yield contact_msg, None, None, None
            fixed += contact_msg
        asyncio.create_task(_fire_unanswered_alert(user_id, result.question, company_code, user_name=user_name))

    global _last_category
    _last_category = _extract_category(result.docs)

    save_interaction(user_id, result.question, fixed)
    related_docs = find_related_docs(result.question)

    yield "", result.source_names, related_docs, result.doc_ids
