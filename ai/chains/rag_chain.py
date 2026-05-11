"""
RAG (Retrieval-Augmented Generation) 체인 모듈
────────────────────────────────────────────
회사 문서를 ChromaDB에서 검색하고, 검색 결과를 컨텍스트로 삼아
Claude LLM이 답변을 생성하는 LCEL 파이프라인을 제공합니다.
멀티턴 대화 히스토리를 프롬프트에 포함시켜 이전 맥락을 유지합니다.
"""

import asyncio
import re
from concurrent.futures import ThreadPoolExecutor
from typing import AsyncGenerator, Tuple, List

from langchain_core.documents import Document
from langchain_core.messages import BaseMessage
from langchain_core.output_parsers import StrOutputParser

from core.llm import get_llm
from core.vectorstore import search_with_company_fallback, search_legal_docs
from memory.chat_history import get_chat_history, save_interaction
from memory.unanswered_store import add_unanswered
from utils.prompts import RAG_PROMPT

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


# ── 원문 직접 출력 (LLM 우회) 키워드 ────────────────────────
_DIRECT_LEGAL_KEYWORDS = ["배우자출산휴가", "배우자 출산휴가"]

def _is_direct_legal_question(question: str) -> bool:
    """LLM 해석 없이 원문을 바로 반환해야 할 질문 감지."""
    return any(kw in question for kw in _DIRECT_LEGAL_KEYWORDS)


# ── 법률 문서 전용 검색 감지 ──────────────────────────────────
_LEGAL_KEYWORDS = [
    "최저임금", "최저시급", "퇴직금", "퇴직급여", "육아휴직", "산재", "임금체불",
    "근로계약", "해고", "근로기준법", "노동법", "연장근로", "야간수당",
    "주휴수당", "소정근로시간", "월 환산", "퇴직연금", "평균임금", "통상임금",
    "출산휴가", "배우자출산휴가", "육아기단축근무", "가족돌봄휴직",
]
_ARTICLE_PATTERN_LEGAL = re.compile(r"제?\d+조")

def _is_legal_question(question: str) -> bool:
    """노동법/법률 관련 질문 여부 감지."""
    return any(kw in question for kw in _LEGAL_KEYWORDS) or bool(_ARTICLE_PATTERN_LEGAL.search(question))


# ── 2-6: 근로기준법 fallback ──────────────────────────────────
_LABOR_LAW_KEYWORDS = ["근로기준법", "노동법", "최저임금법", "산업안전보건법", "고용노동부", "노동자 권리", "근로자 권리"]
def _get_labor_law_fallback(company_code: str) -> str:
    team, _ = _get_hr_contact(company_code)
    return f"\n\n📌 근로기준법 관련 정확한 내용은 **{team}**께 확인하시는 게 가장 정확해요."


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
        return 5  # 조항 질문: 넓게 검색
    return 3      # 일반 질문: 리랭킹이 품질 커버

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

_TEMPLATE_STOPWORDS = {"신청", "신청서", "방법", "처리", "서류", "안내", "가이드", "작성", "주세요", "알려줘"}
_KO_PARTICLE_RE = re.compile(r"(은|는|이|가|을|를|의|에서|에게|에|으로|로|과|와|도|만|부터|까지)$")


def _match_template_docs(company_code: str, question: str) -> tuple[list[int], list[str]]:
    """질문 키워드와 BE TEMPLATE 문서 title/fileName 매칭 → (documentId 리스트, title 리스트) 반환."""
    if not company_code:
        return [], []
    try:
        from core.be_client import get_template_docs
        templates = get_template_docs(company_code)
        if not templates:
            return [], []
        raw_words = {w for w in re.split(r"[\s_\-?？]+", question) if len(w) >= 2}
        # 조사 제거: "비품은" → "비품", "연차를" → "연차"
        q_words = {_KO_PARTICLE_RE.sub("", w) for w in raw_words} - _TEMPLATE_STOPWORDS
        q_words.discard("")
        ids, titles = [], []
        for doc in templates:
            target = f"{doc.get('title', '')} {doc.get('fileName', '')}"
            t_words = {w for w in re.split(r"[\s_\-\.]+", target) if len(w) >= 2} - _TEMPLATE_STOPWORDS
            if any(qw in tw or tw in qw for qw in q_words for tw in t_words):
                ids.append(doc["documentId"])
                titles.append(doc.get("title") or doc.get("fileName") or "")
        return ids, titles
    except Exception:
        return [], []


_COMPANY_NAMES: dict[str, str] = {
    "WB0001": "테크 주식회사",
    "WB0002": "스튜디오 프리즘",
}

def _get_company_name(company_code: str) -> str:
    return _COMPANY_NAMES.get(company_code, "우리 회사")

_COMPANY_HR_CONTACTS: dict[str, dict] = {
    "WB0001": {"team": "경영지원팀", "contact": "경영지원팀 김지수 매니저"},
    "WB0002": {"team": "운영팀", "contact": "운영팀 김현아 매니저"},
}

def _get_hr_contact(company_code: str) -> tuple[str, str]:
    """(팀명, 담당자명) 반환. 미등록 회사는 기본값."""
    info = _COMPANY_HR_CONTACTS.get(company_code, {"team": "담당 부서", "contact": "담당자"})
    return info["team"], info["contact"]

_COMPANY_IT_CONTACTS: dict[str, str] = {
    "WB0001": "Slack @minjun.park (IT담당 박민준)으로 문의해 주세요.",
    "WB0002": "Slack @soyeon.park (운영팀 박소연 담당)으로 문의해 주세요.",
}

def _get_it_contact(company_code: str) -> str:
    return _COMPANY_IT_CONTACTS.get(company_code, "담당 IT 담당자에게 문의해 주세요.")

_COMPANY_SPECIFIC_RULES: dict[str, str] = {
    "WB0001": "    ⚠️ 이 회사 사용자에게는 수습 감액 분기를 적용하지 않습니다. 이 회사는 수습 기간 중에도 급여 100%를 지급합니다.",
}

def _get_company_specific_rules(company_code: str) -> str:
    return _COMPANY_SPECIFIC_RULES.get(company_code, "")


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
        _chain = (RAG_PROMPT | get_llm() | StrOutputParser()).with_config(
            {"tags": ["rag-chain"], "run_name": "withbuddy-rag"}
        )
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


# 법령명 축약 매핑
_LEGAL_SOURCE_NAMES = {
    "index_근로기준법": "근로기준법",
    "index_최저임금법": "최저임금법",
    "index_퇴직급여법": "근로자퇴직급여 보장법",
    "index_남녀고용평등법": "남녀고용평등법",
}

def _get_legal_source_name(source: str) -> str:
    for key, name in _LEGAL_SOURCE_NAMES.items():
        if key in source:
            return name
    return source


def _format_legal_answer(docs: List[Document], question: str, company_code: str = "") -> str:
    """
    LEGAL 문서 검색 결과를 LLM 해석 없이 원문 그대로 포맷합니다.
    할루시네이션 방지를 위해 법령 조항은 원문만 반환합니다.
    """
    _, hr_contact = _get_hr_contact(company_code)
    if not docs:
        return (
            f"음, 제가 가진 법령 자료에서는 해당 내용을 찾지 못했어요 🤔\n"
            f"정확한 내용은 {hr_contact}께 확인해보세요!"
        )

    # 질문 키워드로 관련 청크만 필터링 (너무 많은 청크 출력 방지)
    filter_keywords = [kw for kw in _DIRECT_LEGAL_KEYWORDS if kw in question]
    if filter_keywords:
        filtered = [d for d in docs if any(kw in d.page_content for kw in filter_keywords)]
        if filtered:
            docs = filtered

    # 다음 조항 헤더 이후 내용 제거 (청크 경계 문제로 다음 조항이 섞이는 경우 방지)
    _NEXT_ARTICLE_PATTERN = re.compile(r'\n\[제\d+조', re.MULTILINE)

    # 중복 청크 제거 후 출처별로 묶기
    seen_content = set()
    by_source: dict[str, list[str]] = {}
    for doc in docs:
        content = doc.page_content.strip()
        # ingest.py에서 추가한 [doc_title] prefix 제거 (예: "[index_남녀고용평등법_v2]\n")
        content = re.sub(r'^\[[^\]]+\]\n', '', content)
        # 청크 첫머리의 법률명/시행일 헤더 라인 제거 (상단 📋 헤더와 중복)
        content = re.sub(r'^.+\(약칭:.+\)\s*\n', '', content)
        content = re.sub(r'^\[시행 \d{4}\.\s*\d+\.\s*\d+\.\]\s*\n', '', content)
        content = content.strip()
        # 첫 번째 조항 이후에 다음 조항 헤더가 나오면 그 앞까지만 사용
        first_article = re.search(r'\[제\d+조', content)
        if first_article:
            rest = content[first_article.end():]
            next_article = _NEXT_ARTICLE_PATTERN.search(rest)
            if next_article:
                content = content[:first_article.end() + next_article.start()].strip()
        key = content[:80]
        if key in seen_content:
            continue
        seen_content.add(key)
        source = _get_legal_source_name(doc.metadata.get("source", ""))
        by_source.setdefault(source, []).append(content)

    parts = []
    for source, contents in by_source.items():
        parts.append(f"📋 **{source}** 관련 법령 내용이에요 😊\n")
        parts.append("\n\n".join(contents))

    result = "\n".join(parts)
    result += f"\n\n정확한 적용 방법은 {hr_contact}께도 확인해보세요! 😊"
    return result


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


def _search_sub_q(sub_q: str, company_code: str) -> List[Document]:
    """단일 서브 질문에 대한 검색 수행 (병렬 실행용)"""
    k = _get_k_for_question(sub_q)
    if _is_legal_question(sub_q):
        docs = search_legal_docs(sub_q, k=k * 2)
    else:
        docs = search_with_company_fallback(sub_q, k=k * 2, company_code=company_code)
    return docs[:k]


def _search_sub_q_raw(sub_q: str, company_code: str) -> List[Document]:
    """단일 서브 질문 검색만 수행 (리랭킹 없음, 복합 질문 병렬 실행용)"""
    k = _get_k_for_question(sub_q)
    if _is_legal_question(sub_q):
        return search_legal_docs(sub_q, k=k * 2)
    return search_with_company_fallback(sub_q, k=k * 2, company_code=company_code)


def run_rag_chain(user_id: str, question: str, user_name: str = "", company_code: str = "", company_name: str = "", hire_date: str = "", injected_history: List[BaseMessage] | None = None) -> Tuple[str, str, List[dict], List[int]]:
    """
    RAG 체인을 실행하여 답변, 출처, 관련 양식 목록, 문서 ID 목록을 반환합니다.

    Returns:
        Tuple[str, str, List[dict], List[int]]: (AI 답변, 출처 문서명, 관련 양식 목록, 문서 ID 목록)
    """
    from routers.docs import find_related_docs

    chat_history = injected_history if injected_history is not None else get_chat_history(user_id)

    # 2-7: 숫자 선택 해석 (이전 ambiguous 응답 매핑)
    resolved = _resolve_selection(question, chat_history)
    if resolved:
        question = resolved

    # 2-7: 모호한 질문 선처리
    ambiguous = _check_ambiguous(question)
    if ambiguous:
        return ambiguous, "", [], []

    # 복합 질문 분리 검색: "?" / "그리고" / "이랑" / "이 궁금하고" 패턴으로 분리
    sub_questions = [q.strip() for q in re.split(
        r'[?？]\s*그리고|그리고\s*[?？]?|[?？]\s+|이랑\s+|이\s*궁금하고,?\s*', question
    ) if q.strip() and len(q.strip()) > 2]
    if len(sub_questions) < 2:
        sub_questions = [question]

    retrieved_docs = []
    seen_contents = set()

    if len(sub_questions) == 1:
        retrieved_docs = _search_sub_q(sub_questions[0], company_code)
    else:
        # 복합 질문: 각 서브 질문 검색만 병렬 실행 → 머지 → 리랭킹 1번
        with ThreadPoolExecutor() as executor:
            futures = [executor.submit(_search_sub_q_raw, sub_q, company_code) for sub_q in sub_questions]
            for future in futures:
                for d in future.result():
                    key = d.page_content[:80]
                    if key not in seen_contents:
                        seen_contents.add(key)
                        retrieved_docs.append(d)
        top_k = min(3 * len(sub_questions), 6)
        retrieved_docs = retrieved_docs[:top_k]

    source_names = _extract_sources(retrieved_docs)
    # 공통 법령 문서(company_code="")는 BE 다운로드 권한 없으므로 회사 문서 ID만 반환
    template_ids, template_titles = _match_template_docs(company_code, question)
    doc_ids = list({int(d.metadata["doc_id"]) for d in retrieved_docs if d.metadata.get("doc_id") and d.metadata.get("company_code", "") == company_code} | set(template_ids))

    # 원문 직접 출력 (LLM 우회) — 할루시네이션 방지
    if _is_direct_legal_question(question):
        answer = _format_legal_answer(retrieved_docs, question, company_code)
        save_interaction(user_id, question, answer)
        related_docs = find_related_docs(question)
        return answer, source_names, related_docs, doc_ids

    formatted_context = _format_docs(retrieved_docs)
    if template_titles:
        formatted_context = f"📎 아래 양식 파일이 첨부됩니다: {', '.join(template_titles)}\n\n" + formatted_context
    if _is_legal_question(question):
        formatted_context = (
            "⚠️ 아래는 법령 원문입니다. 각 항목(1., 2., 3., 4. ...)의 문장을 절대 바꾸거나 해석하지 말고 원문 그대로 전달하세요.\n\n"
            + formatted_context
        )
    user_style = _detect_user_style(chat_history, question)
    company_name = company_name or _get_company_name(company_code)
    hr_team, _ = _get_hr_contact(company_code)

    from datetime import date as _date
    today_date = _date.today().strftime("%Y년 %m월 %d일")
    hire_info = ""
    if hire_date:
        try:
            days = (_date.today() - _date.fromisoformat(hire_date)).days + 1
            hire_info = f"\n사용자 입사 {days}일차입니다. (입사일: {hire_date})"
        except Exception:
            pass

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
        "user_name": user_name,
        "company_name": company_name,
        "hr_team": hr_team,
        "it_contact": _get_it_contact(company_code),
        "company_specific_rules": _get_company_specific_rules(company_code),
        "today_date": today_date,
        "hire_info": hire_info,
    })
    answer = _fix_names(answer)
    answer = _dedup_answer(answer)

    # 2-6: 근로기준법 fallback
    if _needs_labor_law_fallback(question, answer):
        answer += _get_labor_law_fallback(company_code)

    # Case A: 회사 문서 없음 + 공통 법령 문서만 검색된 경우 안내 문구 추가
    if (company_code and retrieved_docs and not _is_unanswered(answer, retrieved_docs)
            and all(d.metadata.get("company_code", "") == "" for d in retrieved_docs)):
        answer += f"\n\n참고로 이 답변은 공통 법령 문서를 기준으로 안내드렸어요. 회사별 세부 운영 방식은 다를 수 있으니, 실제 적용 전에는 {hr_team}에 한 번 확인해 주세요."

    # no_result 시 문서 헤더에서 담당자 정보 추출하여 삽입 (LLM이 이미 언급한 경우 중복 방지)
    if _is_unanswered(answer, retrieved_docs):
        contact = _extract_contact_from_docs(retrieved_docs)
        if contact:
            if contact not in answer:
                answer += f"\n\n관련 문의는 **{contact}** 에 직접 여쭤보시면 가장 빠를 거예요! 😊"
        else:
            if hr_team not in answer:
                answer += f"\n\n이 부분은 **{hr_team}**에 직접 여쭤보시면 가장 정확한 답을 얻으실 수 있어요!"

    save_interaction(user_id, question, answer)
    related_docs = find_related_docs(question)

    return answer, source_names, related_docs, doc_ids


async def stream_rag_chain(user_id: str, question: str, user_name: str = "", company_code: str = "", company_name: str = "", hire_date: str = "", injected_history: List[BaseMessage] | None = None) -> AsyncGenerator[Tuple[str, str | None, List[dict] | None, List[int] | None], None]:
    """
    RAG 체인을 스트리밍으로 실행합니다.
    토큰 단위로 (chunk, None, None, None)을 yield하고, 마지막에 ("", source_names, related_docs, rag_doc_ids)를 yield합니다.
    """
    from routers.docs import find_related_docs

    chat_history = injected_history if injected_history is not None else get_chat_history(user_id)

    # 2-7: 숫자 선택 해석
    resolved = _resolve_selection(question, chat_history)
    if resolved:
        question = resolved

    # 2-7: 모호한 질문 선처리
    ambiguous = _check_ambiguous(question)
    if ambiguous:
        save_interaction(user_id, question, ambiguous)
        yield ambiguous, None, None, None
        yield "", "", [], []
        return

    yield "__STAGE__searching", None, None, None

    sub_questions = [q.strip() for q in re.split(
        r'[?？]\s*그리고|그리고\s*[?？]?|[?？]\s+|이랑\s+|이\s*궁금하고,?\s*', question
    ) if q.strip() and len(q.strip()) > 2]
    if len(sub_questions) < 2:
        sub_questions = [question]

    loop = asyncio.get_event_loop()
    retrieved_docs = []
    seen_contents = set()

    if len(sub_questions) == 1:
        retrieved_docs = await loop.run_in_executor(None, _search_sub_q, sub_questions[0], company_code)
    else:
        # 복합 질문: 각 서브 질문 검색만 병렬 실행 → 머지 → 리랭킹 1번
        results = await asyncio.gather(*[
            loop.run_in_executor(None, _search_sub_q_raw, sub_q, company_code)
            for sub_q in sub_questions
        ])
        for docs in results:
            for d in docs:
                key = d.page_content[:80]
                if key not in seen_contents:
                    seen_contents.add(key)
                    retrieved_docs.append(d)
        top_k = min(3 * len(sub_questions), 6)
        retrieved_docs = retrieved_docs[:top_k]
    source_names = _extract_sources(retrieved_docs)
    # 공통 법령 문서(company_code="")는 BE 다운로드 권한 없으므로 회사 문서 ID만 반환
    template_ids, template_titles = _match_template_docs(company_code, question)
    rag_doc_ids = list({int(d.metadata["doc_id"]) for d in retrieved_docs if d.metadata.get("doc_id") and d.metadata.get("company_code", "") == company_code} | set(template_ids))

    # 원문 직접 출력 (LLM 우회) — 할루시네이션 방지
    if _is_direct_legal_question(question):
        answer = _format_legal_answer(retrieved_docs, question, company_code)
        save_interaction(user_id, question, answer)
        related_docs = find_related_docs(question)
        yield answer, None, None, None
        yield "", source_names, related_docs, rag_doc_ids
        return

    formatted_context = _format_docs(retrieved_docs)
    if template_titles:
        formatted_context = f"📎 아래 양식 파일이 첨부됩니다: {', '.join(template_titles)}\n\n" + formatted_context
    if _is_legal_question(question):
        formatted_context = (
            "⚠️ 아래는 법령 원문입니다. 각 항목(1., 2., 3., 4. ...)의 문장을 절대 바꾸거나 해석하지 말고 원문 그대로 전달하세요.\n\n"
            + formatted_context
        )
    user_style = _detect_user_style(chat_history, question)
    company_name = company_name or _get_company_name(company_code)
    hr_team, _ = _get_hr_contact(company_code)

    from datetime import date as _date
    today_date = _date.today().strftime("%Y년 %m월 %d일")
    hire_info = ""
    if hire_date:
        try:
            days = (_date.today() - _date.fromisoformat(hire_date)).days + 1
            hire_info = f"\n사용자 입사 {days}일차입니다. (입사일: {hire_date})"
        except Exception:
            pass

    _PROFILE_KEYWORDS = ["팀장", "내 부서", "우리 팀", "내 팀", "나의 팀장", "누구야"]
    if any(kw in question for kw in _PROFILE_KEYWORDS):
        from memory.profile_store import format_profile_context, get_profile
        profile_ctx = format_profile_context(get_profile(user_id))
        if profile_ctx:
            formatted_context = f"[사용자 프로필]\n{profile_ctx}\n\n{formatted_context}"

    yield "__STAGE__generating", None, None, None

    full_answer = ""
    async for chunk in _get_chain().astream({
        "question": question,
        "context": formatted_context,
        "chat_history": chat_history,
        "user_style": user_style,
        "user_name": user_name,
        "company_name": company_name,
        "hr_team": hr_team,
        "it_contact": _get_it_contact(company_code),
        "company_specific_rules": _get_company_specific_rules(company_code),
        "today_date": today_date,
        "hire_info": hire_info,
    }):
        full_answer += chunk
        yield chunk, None, None, None

    # 2-6: 근로기준법 fallback
    if _needs_labor_law_fallback(question, full_answer):
        labor_fallback = _get_labor_law_fallback(company_code)
        yield labor_fallback, None, None, None
        full_answer += labor_fallback

    # 이름 교정 + 중복 제거: 스트리밍 완료 후 교정본 전송
    fixed = _fix_names(full_answer)
    fixed = await loop.run_in_executor(None, _dedup_answer, fixed)
    if fixed != full_answer:
        yield "\x00" + fixed, None, None, None  # \x00 prefix → 프론트에서 전체 교체 신호

    save_interaction(user_id, question, fixed)

    # Case A: 회사 문서 없음 + 공통 법령 문서만 검색된 경우 안내 문구 추가
    if (company_code and retrieved_docs and not _is_unanswered(fixed, retrieved_docs)
            and all(d.metadata.get("company_code", "") == "" for d in retrieved_docs)):
        case_a_msg = f"\n\n참고로 이 답변은 공통 법령 문서를 기준으로 안내드렸어요. 회사별 세부 운영 방식은 다를 수 있으니, 실제 적용 전에는 {hr_team}에 한 번 확인해 주세요."
        yield case_a_msg, None, None, None
        fixed += case_a_msg

    # 미답변 감지 → 문서 헤더에서 담당자 정보 추출하여 삽입 + 백그라운드 Slack 알림 (LLM이 이미 언급한 경우 중복 방지)
    if _is_unanswered(full_answer, retrieved_docs):
        contact = _extract_contact_from_docs(retrieved_docs)
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
        asyncio.create_task(_fire_unanswered_alert(user_id, question, company_code))

    save_interaction(user_id, question, fixed)
    related_docs = find_related_docs(question)

    yield "", source_names, related_docs, rag_doc_ids
