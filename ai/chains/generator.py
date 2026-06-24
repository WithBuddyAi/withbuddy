"""
답변 생성 모듈
────────────────────────────────────────────
검색된 컨텍스트와 프롬프트를 조합하여 LLM 답변을 생성합니다.
후처리(이름 교정, 중복 제거, fallback 문구 삽입)를 포함합니다.
"""

import os
import re
from typing import AsyncGenerator, List, Tuple

from langchain_core.documents import Document
from langchain_core.messages import BaseMessage, HumanMessage
from langchain_core.output_parsers import StrOutputParser

from core.llm import get_llm
from utils.prompts import RAG_PROMPT_CACHED


# ── 체인 싱글톤 ────────────────────────────────────────────────
_chain = None


def _get_chain():
    global _chain
    if _chain is None:
        _chain = (RAG_PROMPT_CACHED | get_llm() | StrOutputParser()).with_config(
            {"tags": ["rag-chain"], "run_name": "withbuddy-rag"}
        )
    return _chain


# ── dedup LLM 싱글톤 ────────────────────────────────────────────
_dedup_llm = None


def _get_dedup_llm():
    global _dedup_llm
    if _dedup_llm is None:
        from langchain_anthropic import ChatAnthropic
        _dedup_llm = ChatAnthropic(
            model="claude-haiku-4-5-20251001",
            anthropic_api_key=os.getenv("ANTHROPIC_API_KEY"),
            temperature=0,
            max_tokens=2048,
        )
    return _dedup_llm


# ── 후처리 헬퍼 ────────────────────────────────────────────────
def _fix_names(text: str) -> str:
    """'님 -' → '님:' 통일 및 어색한 문장 교체."""
    text = re.sub(r'님\s*[-–]', '님:', text)
    text = re.sub(r'어떤 부분이 필요한지에 따라 연락하[^\n\.\!]*[\.\!]?', '필요한 부분 확인 후 연락해보세요!', text)
    text = re.sub(r'(\d+)~(\d+)영업일', r'영업일 기준 \1일~\2일', text)
    text = text.replace("다만 참고로,", "참고로,").replace("다만 참고로", "참고로")
    text = re.sub(r'\*{0,2}직접 확인하세요\s*:\*{0,2}\s*\n?', '', text)
    return text


def _fix_section_spacing(text: str) -> str:
    """** 앞 \n\n은 섹션 구분으로 유지, 나머지 \n\n → \n으로 교체."""
    return re.sub(r'\n\n(?!\*\*)', '\n', text)


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
    result = _get_dedup_llm().invoke([HumanMessage(content=(
        "다음 텍스트에서 완전히 동일하거나 거의 동일한 중복 문장·단락을 하나만 남기고 제거하세요. "
        "내용·형식·말투·이모지는 절대 바꾸지 마세요. 설명 없이 정제된 텍스트만 반환하세요.\n\n"
        + text
    ))])
    return result.content


# ── 사용자 말투 감지 ────────────────────────────────────────────
def _detect_user_style(history: List[BaseMessage], current_question: str) -> str:
    """기본은 존댓말 고정. 사용자가 명시적으로 반말을 요청한 경우에만 반말로 전환."""
    _CASUAL_REQUESTS = ["반말로", "반말 해", "반말해", "편하게 말해", "친구처럼", "캐주얼하게", "반말로 해줘", "편하게 해줘"]
    if any(kw in current_question.strip() for kw in _CASUAL_REQUESTS):
        return (
            "사용자가 반말을 요청했습니다. 지금부터 친근한 반말(~해, ~야, ~거든, ~어, ~줘)로 답변하고 이후에도 유지하세요. "
            "🚫 ~습니다, ~요, ~세요 사용 금지."
        )
    return ""


# ── no_result / fallback 헬퍼 ──────────────────────────────────
_NO_ANSWER_KEYWORDS = [
    "문서에서 확인되지", "관련 정보를 찾을 수 없", "확인되지 않습니다", "답변하기 어렵",
    "안내가 없습니다", "내용이 없습니다", "보유한 문서에는", "문서에는",
    "찾을 수 없습니다", "포함되어 있지 않", "정보가 없", "문서에 없어서", "안내드리기 어려워",
    "없는 것 같아요", "나와있지 않", "명시되어 있지 않", "기재되어 있지 않",
    "확인되지 않", "나와 있지 않", "관련 내용이 없",
    "찾지 못했", "드리기 어렵",
]

_LABOR_LAW_KEYWORDS = ["근로기준법", "노동법", "최저임금법", "산업안전보건법", "고용노동부", "노동자 권리", "근로자 권리"]


def is_unanswered(answer: str, docs: List[Document]) -> bool:
    if not docs:
        return True
    return any(kw in answer for kw in _NO_ANSWER_KEYWORDS)


def needs_labor_law_fallback(question: str, answer: str) -> bool:
    return (
        any(kw in question for kw in _LABOR_LAW_KEYWORDS)
        and any(kw in answer for kw in _NO_ANSWER_KEYWORDS)
    )


def get_labor_law_fallback(hr_team: str) -> str:
    return f"\n\n📌 근로기준법 관련 정확한 내용은 **{hr_team}**께 확인하시는 게 가장 정확해요."


def extract_contact_from_docs(docs: List[Document]) -> str | None:
    """검색된 문서 청크에서 '담당 부서 / 문의처:' 필드를 파싱합니다."""
    pattern = re.compile(r'담당 부서\s*/\s*문의처\s*:\s*(.+)')
    for doc in docs:
        m = pattern.search(doc.page_content)
        if m:
            return m.group(1).strip()
    return None


def build_contact_suffix(answer: str, docs: List[Document], hr_team: str, question: str = "", it_card: dict = None) -> str:
    """미답변 시 담당자 안내 문구를 반환합니다. 이미 포함된 경우 빈 문자열."""
    _CONTACT_HINTS = ["문의하시면", "여쭤보시면", "연락하시면", "담당자에게"]
    if any(hint in answer for hint in _CONTACT_HINTS):
        return ""
    contact = extract_contact_from_docs(docs)
    if contact:
        return f"\n\n관련 문의는 **{contact}** 에 직접 여쭤보시면 가장 빠를 거예요! 😊"
    if it_card:
        dept = it_card.get("department", hr_team)
        return f"\n\n**{dept} 담당자**님께 문의하시면 가장 정확한 답을 얻으실 수 있어요!"
    return f"\n\n이 부분은 **{hr_team}**에 여쭤보시면 가장 정확한 답을 얻으실 수 있어요!"


def build_case_a_suffix(hr_team: str) -> str:
    """법령 fallback 안내 문구를 반환합니다."""
    return f"\n\n참고로 이 답변은 공통 법령 문서를 기준으로 안내드렸어요. 회사별 세부 운영 방식은 다를 수 있으니, 실제 적용 전에는 {hr_team}에 한 번 확인해 주세요."


# ── 메인 생성 함수 ─────────────────────────────────────────────
def generate_answer(
    question: str,
    context: str,
    chat_history: List[BaseMessage],
    user_style: str,
    user_name: str,
    company_name: str,
    hr_team: str,
    it_contact: str,
    company_specific_rules: str,
    today_date: str,
    hire_info: str,
) -> str:
    """LLM을 호출하여 답변을 생성하고 후처리합니다."""
    answer = _get_chain().invoke({
        "question": question,
        "context": context,
        "chat_history": chat_history,
        "user_style": user_style,
        "user_name": user_name,
        "company_name": company_name,
        "hr_team": hr_team,
        "it_contact": it_contact,
        "company_specific_rules": company_specific_rules,
        "today_date": today_date,
        "hire_info": hire_info,
    })
    answer = _fix_names(answer)
    answer = _fix_section_spacing(answer)
    answer = _dedup_answer(answer)
    return answer


async def stream_answer(
    question: str,
    context: str,
    chat_history: List[BaseMessage],
    user_style: str,
    user_name: str,
    company_name: str,
    hr_team: str,
    it_contact: str,
    company_specific_rules: str,
    today_date: str,
    hire_info: str,
) -> AsyncGenerator[str, None]:
    """LLM을 스트리밍으로 호출하여 raw 토큰을 yield합니다."""
    async for chunk in _get_chain().astream({
        "question": question,
        "context": context,
        "chat_history": chat_history,
        "user_style": user_style,
        "user_name": user_name,
        "company_name": company_name,
        "hr_team": hr_team,
        "it_contact": it_contact,
        "company_specific_rules": company_specific_rules,
        "today_date": today_date,
        "hire_info": hire_info,
    }):
        yield chunk


def _fix_grammar(text: str) -> str:
    return text.replace("있아서", "있어서").replace("없아서", "없어서")


async def postprocess_answer_async(full_answer: str) -> str:
    """스트리밍 완료 후 비동기 후처리 (이름 교정 + 중복 제거)."""
    import asyncio
    loop = asyncio.get_event_loop()
    fixed = _fix_names(full_answer)
    fixed = _fix_grammar(fixed)
    fixed = _fix_section_spacing(fixed)
    fixed = await loop.run_in_executor(None, _dedup_answer, fixed)
    return fixed


def postprocess_answer(full_answer: str) -> str:
    """동기 후처리 (이름 교정 + 중복 제거)."""
    fixed = _fix_names(full_answer)
    fixed = _fix_grammar(fixed)
    fixed = _fix_section_spacing(fixed)
    fixed = _dedup_answer(fixed)
    return fixed
