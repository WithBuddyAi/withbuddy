"""
RAG (Retrieval-Augmented Generation) 체인 모듈
────────────────────────────────────────────
회사 문서를 ChromaDB에서 검색하고, 검색 결과를 컨텍스트로 삼아
Claude LLM이 답변을 생성하는 LCEL 파이프라인을 제공합니다.
멀티턴 대화 히스토리를 프롬프트에 포함시켜 이전 맥락을 유지합니다.
"""

import asyncio
import json
import re
from pathlib import Path
from typing import AsyncGenerator, Tuple, List

from langchain_core.documents import Document
from langchain_core.messages import BaseMessage
from langchain_core.output_parsers import StrOutputParser

from core.llm import get_llm
from core.vectorstore import search_with_company_fallback
from memory.chat_history import get_chat_history, save_interaction
from memory.unanswered_store import add_unanswered
from utils.prompts import RAG_PROMPT

# ── 팀장 이름 정규화 (team_config.json 기준) ─────────────────
def _build_name_patterns() -> list[tuple[re.Pattern, str]]:
    """team_config.json에서 팀장 이름을 읽어 정규식 교정 패턴을 빌드합니다."""
    config_path = Path(__file__).parent.parent / "data" / "team_config.json"
    try:
        teams = json.loads(config_path.read_text(encoding="utf-8")).get("teams", [])
    except Exception:
        return []
    patterns = []
    for team in teams:
        name = team.get("leader_name", "")  # 예: "김*수"
        if len(name) == 3 and name[1] == "*":
            first, last = re.escape(name[0]), re.escape(name[2])
            # 김*수, 김 수, 김수 → 김*수 님 (이미 님이 붙어 있으면 그대로)
            pat = re.compile(rf"{first}[\s*]?{last}(?:\s*님)?")
            patterns.append((pat, f"{name} 님"))
    return patterns

_NAME_PATTERNS = _build_name_patterns()


def _fix_names(text: str) -> str:
    """LLM 응답에서 팀장 이름을 '김*수 님' 형식으로 교정하고 '님 -' → '님:' 로 통일합니다."""
    for pat, replacement in _NAME_PATTERNS:
        text = pat.sub(replacement, text)
    # '님 -', '님 –', '님-' → '님:'
    text = re.sub(r'님\s*[-–]', '님:', text)
    # '어떤 부분이 필요한지에 따라 연락하면 돼요' 계열 → 자연스러운 문장으로 교체
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

# ── 2-7: 숫자 선택 → 이전 ambiguous 응답 매핑 ───────────────
_SELECT_MAP: dict[str, int] = {"1": 0, "①": 0, "2": 1, "②": 1, "3": 2, "③": 2, "4": 3, "④": 3}
_OPT_PATTERN = re.compile(r'[①②③④]\s+(.+)')


def _resolve_selection(question: str, history: List[BaseMessage]) -> str | None:
    """숫자·기호 선택을 이전 ambiguous 응답의 선택지와 매핑. 매핑 성공 시 실제 질문 반환."""
    q = question.strip()
    idx = _SELECT_MAP.get(q)
    if idx is None:
        return None
    last_ai = next((m.content for m in reversed(history) if m.type == "ai"), "")
    opts = _OPT_PATTERN.findall(last_ai)
    if opts and idx < len(opts):
        # 이전 ambiguous 응답의 topic 추출 (예: "연차에 대해 궁금하신 거 맞죠?")
        topic_match = re.match(r'(.+?)에 대해', last_ai)
        topic = topic_match.group(1).strip() if topic_match else ""
        return f"{topic} {opts[idx]}".strip()
    return None


# ── 2-7: 모호한 질문 감지 ─────────────────────────────────────
_AMBIGUOUS_TOPICS: dict[str, list[str]] = {
    "연차": ["신청 방법", "잔여 연차 확인", "연차 계산 기준", "연차 수당"],
    "휴가": ["휴가 신청 방법", "병가 처리", "경조사 휴가", "특별 휴가"],
    "경비": ["경비 신청 방법", "경비 처리 절차", "법인카드 사용", "영수증 제출"],
    "장비": ["IT 장비 신청", "장비 반납", "장비 고장 신고", "소프트웨어 설치"],
    "계정": ["계정 생성 신청", "비밀번호 초기화", "권한 요청", "VPN 설정"],
    "급여": ["급여 지급일", "급여 명세서 확인", "세금 공제", "성과급"],
    "복리후생": ["복지 포인트 사용", "건강검진", "경조사 지원", "동호회 지원"],
}
_NUMS = "①②③④"


def _check_ambiguous(question: str) -> str | None:
    """단어 하나짜리 모호한 질문 → 선택지 반환, 아니면 None."""
    q = question.strip().rstrip("?？")
    options = _AMBIGUOUS_TOPICS.get(q)
    if options:
        opts = "\n".join(f"{_NUMS[i]} {opt}" for i, opt in enumerate(options))
        return f"{q}에 대해 궁금하신 거 맞죠? 😊 어떤 부분이 궁금하신가요?\n{opts}"
    return None


# ── 2-6: 근로기준법 fallback ──────────────────────────────────
_LABOR_LAW_KEYWORDS = ["근로기준법", "노동법", "최저임금법", "산업안전보건법", "고용노동부", "노동자 권리", "근로자 권리"]
_LABOR_LAW_FALLBACK = (
    "\n\n📌 근로기준법 관련 정확한 내용은 **고용노동부 고객상담센터 ☎1350** 또는 "
    "**인사팀 김지수님**께 확인하시는 게 가장 정확해요."
)


def _needs_labor_law_fallback(question: str, answer: str) -> bool:
    """근로기준법 관련 질문이고 RAG가 문서를 못 찾은 경우 True."""
    return (
        any(kw in question for kw in _LABOR_LAW_KEYWORDS)
        and any(kw in answer for kw in _NO_ANSWER_KEYWORDS)
    )


# 조항 질문 패턴: "제1조", "제10조의2", "제2절" 등
_ARTICLE_PATTERN = re.compile(r'제\s*\d+\s*[조절장]')

def _get_k_for_question(question: str) -> int:
    """
    질문 유형에 따라 검색할 청크 수(k)를 결정합니다.
    조항 질문("제N조")은 더 많은 청크를 검색하여 해당 조항을 찾을 확률을 높입니다.
    """
    if _ARTICLE_PATTERN.search(question):
        return 7  # 조항 질문: 넓게 검색
    return 5      # 일반 질문: 기본값 (기존 3에서 상향)

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
]

def _is_unanswered(answer: str, docs: List[Document]) -> bool:
    if not docs:
        return True
    return any(kw in answer for kw in _NO_ANSWER_KEYWORDS)

async def _fire_unanswered_alert(user_id: str, question: str) -> None:
    """미답변 저장 + Slack 알림 (백그라운드)"""
    try:
        from tasks.slack_notifier import notify_unanswered_question
        qid = add_unanswered(user_id, question)
        await notify_unanswered_question(user_id, question, qid)
    except Exception:
        pass  # 알림 실패가 채팅 응답에 영향주지 않도록

_chain = None


def _get_chain():
    global _chain
    if _chain is None:
        _chain = RAG_PROMPT | get_llm() | StrOutputParser()
    return _chain


_MAX_CHUNK_CHARS = 400    # 청크당 최대 글자 수 (토큰 절약)
_MAX_CONTEXT_CHARS = 2000  # 전체 컨텍스트 최대 글자 수

# 문서 내 회사명 치환 목록 — PDF 원본의 회사명을 서비스명으로 변경
_COMPANY_REPLACEMENTS = [
    ("한전KDN", "테크 주식회사"),
    ("한국전력공사", "테크 주식회사"),
    ("한국전력", "테크 주식회사"),
    ("KEPCO", "테크 주식회사"),
    ("KDN", "테크 주식회사"),
    ("한전", "테크 주식회사"),
]

def _replace_company(text: str) -> str:
    for old, new in _COMPANY_REPLACEMENTS:
        text = text.replace(old, new)
    return text


def _format_docs(docs: List[Document]) -> str:
    """
    검색된 Document 목록을 LLM에 전달할 컨텍스트 문자열로 포맷합니다.
    청크당/전체 글자 수를 제한하여 토큰을 절약합니다.
    """
    if not docs:
        return "관련 문서를 찾을 수 없습니다."

    parts = []
    total = 0
    for doc in docs:
        content = _replace_company(doc.page_content[:_MAX_CHUNK_CHARS])
        source = doc.metadata.get("source", "알 수 없음")
        entry = f"[출처: {source}]\n{content}"
        if total + len(entry) > _MAX_CONTEXT_CHARS:
            break
        parts.append(entry)
        total += len(entry)

    return "\n\n".join(parts)


def _extract_sources(docs: List[Document]) -> str:
    """
    검색된 문서들의 출처(파일명)를 중복 없이 반환합니다.

    Args:
        docs: ChromaDB에서 검색된 Document 리스트

    Returns:
        str: 쉼표로 구분된 출처 문서명
    """
    sources = {
        doc.metadata.get("source", "알 수 없음")
        for doc in docs
    }
    return ", ".join(sources) if sources else "알 수 없음"


def run_rag_chain(user_id: str, question: str, user_name: str = "", company_id: str = "") -> Tuple[str, str, List[dict]]:
    """
    RAG 체인을 실행하여 답변, 출처, 관련 양식 목록을 반환합니다.

    Returns:
        Tuple[str, str, List[dict]]: (AI 답변, 출처 문서명, 관련 양식 목록)
    """
    from routers.docs import find_related_docs

    chat_history = get_chat_history(user_id)

    # 2-7: 숫자 선택 해석 (이전 ambiguous 응답 매핑)
    resolved = _resolve_selection(question, chat_history)
    if resolved:
        question = resolved

    # 2-7: 모호한 질문 선처리
    ambiguous = _check_ambiguous(question)
    if ambiguous:
        return ambiguous, "", []

    retrieved_docs = search_with_company_fallback(question, k=_get_k_for_question(question), company_id=company_id)
    formatted_context = _format_docs(retrieved_docs)
    source_names = _extract_sources(retrieved_docs)
    user_style = _detect_user_style(chat_history, question)

    _PROFILE_KEYWORDS = ["팀장", "내 부서", "우리 팀", "내 팀", "나의 팀장", "누구야"]
    if any(kw in question for kw in _PROFILE_KEYWORDS):
        from memory.profile_store import format_profile_context, get_profile
        profile_ctx = format_profile_context(get_profile(user_id))
        if profile_ctx:
            formatted_context = f"[사용자 프로필]\n{profile_ctx}\n\n{formatted_context}"

    answer = _get_chain().invoke({
        "question": question,
        "context": formatted_context,
        "chat_history": chat_history,
        "user_style": user_style,
        "user_name": "",
    })
    answer = _fix_names(answer)

    # 2-6: 근로기준법 fallback
    if _needs_labor_law_fallback(question, answer):
        answer += _LABOR_LAW_FALLBACK

    save_interaction(user_id, question, answer)
    related_docs = find_related_docs(question)
    return answer, source_names, related_docs


async def stream_rag_chain(user_id: str, question: str, user_name: str = "", company_id: str = "") -> AsyncGenerator[Tuple[str, str | None, List[dict] | None], None]:
    """
    RAG 체인을 스트리밍으로 실행합니다.
    토큰 단위로 (chunk, None, None)을 yield하고, 마지막에 ("", source_names, related_docs)를 yield합니다.
    """
    from routers.docs import find_related_docs

    chat_history = get_chat_history(user_id)

    # 2-7: 숫자 선택 해석
    resolved = _resolve_selection(question, chat_history)
    if resolved:
        question = resolved

    # 2-7: 모호한 질문 선처리
    ambiguous = _check_ambiguous(question)
    if ambiguous:
        save_interaction(user_id, question, ambiguous)
        yield ambiguous, None, None   # 텍스트로 전송
        yield "", "", []              # done 시그널
        return

    retrieved_docs = search_with_company_fallback(question, k=_get_k_for_question(question), company_id=company_id)
    formatted_context = _format_docs(retrieved_docs)
    source_names = _extract_sources(retrieved_docs)
    user_style = _detect_user_style(chat_history, question)

    _PROFILE_KEYWORDS = ["팀장", "내 부서", "우리 팀", "내 팀", "나의 팀장", "누구야"]
    if any(kw in question for kw in _PROFILE_KEYWORDS):
        from memory.profile_store import format_profile_context, get_profile
        profile_ctx = format_profile_context(get_profile(user_id))
        if profile_ctx:
            formatted_context = f"[사용자 프로필]\n{profile_ctx}\n\n{formatted_context}"

    full_answer = ""
    async for chunk in _get_chain().astream({
        "question": question,
        "context": formatted_context,
        "chat_history": chat_history,
        "user_style": user_style,
        "user_name": "",
    }):
        full_answer += chunk
        yield chunk, None, None

    # 2-6: 근로기준법 fallback
    if _needs_labor_law_fallback(question, full_answer):
        yield _LABOR_LAW_FALLBACK, None, None
        full_answer += _LABOR_LAW_FALLBACK

    # 이름 교정: 스트리밍 완료 후 전체 텍스트 기준으로 교정본 전송
    fixed = _fix_names(full_answer)
    if fixed != full_answer:
        yield "\x00" + fixed, None, None  # \x00 prefix → 프론트에서 전체 교체 신호

    save_interaction(user_id, question, fixed)

    # 미답변 감지 → 백그라운드로 Slack 알림
    if _is_unanswered(full_answer, retrieved_docs):
        asyncio.create_task(_fire_unanswered_alert(user_id, question))

    related_docs = find_related_docs(question)
    yield "", source_names, related_docs
