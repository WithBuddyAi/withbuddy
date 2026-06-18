"""
RAG 검색 로직 분리 모듈 (SCRUM-330)
────────────────────────────────────────────
질문 전처리, 문서 검색, 포맷팅 전담.
BM25 · Self-Verifier 등 검색 전략 교체 시 이 파일만 수정하면 됩니다.

Public interface:
    retrieve(question, company_code, chat_history) -> RetrievalResult
    async_retrieve(question, company_code, chat_history) -> RetrievalResult
"""

import asyncio
import re
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import List

from langchain_core.documents import Document
from langchain_core.messages import BaseMessage

from core.vectorstore import search_with_company_fallback, search_legal_docs


# ── 회사별 설정 ───────────────────────────────────────────────

try:
    from contacts_config import (
        COMPANY_NAMES as _COMPANY_NAMES,
        COMPANY_HR_CONTACTS as _COMPANY_HR_CONTACTS,
        COMPANY_IT_CONTACTS as _COMPANY_IT_CONTACTS,
        COMPANY_SPECIFIC_RULES as _COMPANY_SPECIFIC_RULES,
    )
except ImportError:
    _COMPANY_NAMES = {}
    _COMPANY_HR_CONTACTS = {}
    _COMPANY_IT_CONTACTS = {}
    _COMPANY_SPECIFIC_RULES = {}


def get_company_name(company_code: str) -> str:
    return _COMPANY_NAMES.get(company_code, "우리 회사")


def get_hr_contact(company_code: str) -> tuple[str, str]:
    info = _COMPANY_HR_CONTACTS.get(company_code, {"team": "담당 부서", "contact": "담당자"})
    return info["team"], info["contact"]


def get_it_contact(company_code: str) -> str:
    return _COMPANY_IT_CONTACTS.get(company_code, "담당 IT 담당자에게 문의해 주세요.")


def get_company_specific_rules(company_code: str) -> str:
    return _COMPANY_SPECIFIC_RULES.get(company_code, "")


# ── 질문 분류 상수 ─────────────────────────────────────────────

_DIRECT_LEGAL_KEYWORDS = ["배우자출산휴가", "배우자 출산휴가"]

_LEGAL_KEYWORDS = [
    "최저임금", "최저시급", "퇴직금", "퇴직급여", "육아휴직", "산재", "임금체불",
    "근로계약", "해고", "근로기준법", "노동법", "연장근로", "야간수당",
    "주휴수당", "소정근로시간", "월 환산", "퇴직연금", "평균임금", "통상임금",
    "출산휴가", "배우자출산휴가", "육아기단축근무", "가족돌봄휴직",
]
_ARTICLE_PATTERN_LEGAL = re.compile(r"제?\d+조")
_ARTICLE_PATTERN = re.compile(r'제\s*\d+\s*[조절장]')


def is_legal_question(question: str) -> bool:
    return any(kw in question for kw in _LEGAL_KEYWORDS) or bool(_ARTICLE_PATTERN_LEGAL.search(question))


def is_direct_legal_question(question: str) -> bool:
    return any(kw in question for kw in _DIRECT_LEGAL_KEYWORDS)


def get_k_for_question(question: str) -> int:
    if _ARTICLE_PATTERN.search(question):
        return 5
    return 4


# ── 모호한 질문 처리 ───────────────────────────────────────────

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
_SELECT_MAP: dict[str, int] = {"1": 0, "①": 0, "2": 1, "②": 1, "3": 2, "③": 2, "4": 3, "④": 3}
_OPT_PATTERN = re.compile(r'[①②③④]\s+(.+)')


def resolve_selection(question: str, history: List[BaseMessage]) -> str | None:
    q = question.strip()
    idx = _SELECT_MAP.get(q)
    if idx is None:
        return None
    last_ai = next((m.content for m in reversed(history) if m.type == "ai"), "")
    opts = _OPT_PATTERN.findall(last_ai)
    if opts and idx < len(opts):
        topic_match = re.match(r'(.+?)에 대해', last_ai)
        topic = topic_match.group(1).strip() if topic_match else ""
        return f"{topic} {opts[idx]}".strip()
    return None


def check_ambiguous(question: str) -> str | None:
    q = question.strip().rstrip("?？")
    options = _AMBIGUOUS_TOPICS.get(q)
    if options:
        opts = "\n".join(f"{_NUMS[i]} {opt}" for i, opt in enumerate(options))
        return f"{q}에 대해 궁금하신 거 맞죠? 😊 어떤 부분이 궁금하신가요?\n{opts}"
    return None


# ── 문서 포맷팅 ────────────────────────────────────────────────

_MAX_CHUNK_CHARS = 600
_MAX_CONTEXT_CHARS = 3000

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


def format_legal_answer(docs: List[Document], question: str, company_code: str = "") -> str:
    _, hr_contact = get_hr_contact(company_code)
    if not docs:
        return (
            f"음, 제가 가진 법령 자료에서는 해당 내용을 찾지 못했어요 🤔\n"
            f"정확한 내용은 {hr_contact}께 확인해보세요!"
        )

    filter_keywords = [kw for kw in _DIRECT_LEGAL_KEYWORDS if kw in question]
    if filter_keywords:
        filtered = [d for d in docs if any(kw in d.page_content for kw in filter_keywords)]
        if filtered:
            docs = filtered

    _NEXT_ARTICLE_PATTERN = re.compile(r'\n\[제\d+조', re.MULTILINE)

    seen_content = set()
    by_source: dict[str, list[str]] = {}
    for doc in docs:
        content = doc.page_content.strip()
        content = re.sub(r'^\[[^\]]+\]\n', '', content)
        content = re.sub(r'^.+\(약칭:.+\)\s*\n', '', content)
        content = re.sub(r'^\[시행 \d{4}\.\s*\d+\.\s*\d+\.\]\s*\n', '', content)
        content = content.strip()
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


def format_docs(docs: List[Document]) -> tuple[str, List[Document]]:
    """LLM에 전달할 문서 텍스트와 실제 포함된 docs를 함께 반환."""
    if not docs:
        return "관련 문서를 찾을 수 없습니다.", []

    parts = []
    included = []
    total = 0
    for doc in docs:
        content = doc.page_content[:_MAX_CHUNK_CHARS]
        source = doc.metadata.get("source", "알 수 없음")
        entry = f"[출처: {source}]\n{content}"
        if total + len(entry) > _MAX_CONTEXT_CHARS:
            break
        parts.append(entry)
        included.append(doc)
        total += len(entry)

    return "\n\n".join(parts), included


def extract_sources(docs: List[Document], max_sources: int = 2) -> str:
    seen: list[str] = []
    for doc in docs:
        src = doc.metadata.get("source", "알 수 없음")
        if src not in seen:
            seen.append(src)
        if len(seen) >= max_sources:
            break
    return ", ".join(seen) if seen else "알 수 없음"


# ── 템플릿 문서 매칭 ───────────────────────────────────────────

_TEMPLATE_STOPWORDS = {"신청", "신청서", "방법", "처리", "서류", "안내", "가이드", "작성", "주세요", "알려줘"}
_KO_PARTICLE_RE = re.compile(r"(은|는|이|가|을|를|의|에서|에게|에|으로|로|과|와|도|만|부터|까지)$")


def match_template_docs(company_code: str, question: str) -> tuple[list[int], list[str]]:
    if not company_code:
        return [], []
    try:
        from core.be_client import get_template_docs
        templates = get_template_docs(company_code)
        if not templates:
            return [], []
        raw_words = {w for w in re.split(r"[\s_\-?？]+", question) if len(w) >= 2}
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


# ── 서브 질문 검색 ─────────────────────────────────────────────

def _search_sub_q(sub_q: str, company_code: str) -> List[Document]:
    k = get_k_for_question(sub_q)
    if is_legal_question(sub_q):
        docs = search_legal_docs(sub_q, k=k * 2)
    else:
        docs = search_with_company_fallback(sub_q, k=k * 2, company_code=company_code)
    return docs[:k]


def _search_sub_q_raw(sub_q: str, company_code: str) -> List[Document]:
    k = get_k_for_question(sub_q)
    if is_legal_question(sub_q):
        return search_legal_docs(sub_q, k=k * 2)
    return search_with_company_fallback(sub_q, k=k * 2, company_code=company_code)


_SUB_Q_SPLIT_PATTERN = re.compile(
    r'[?？]\s*그리고|그리고\s*[?？]?|[?？]\s+|이랑\s+|이\s*궁금하고,?\s*'
)


def _split_sub_questions(question: str) -> List[str]:
    parts = [q.strip() for q in _SUB_Q_SPLIT_PATTERN.split(question) if q.strip() and len(q.strip()) > 2]
    return parts if len(parts) >= 2 else [question]


def _do_search(question: str, company_code: str) -> tuple[List[Document], List[str]]:
    sub_questions = _split_sub_questions(question)
    docs: List[Document] = []
    seen: set[str] = set()

    if len(sub_questions) == 1:
        docs = _search_sub_q(sub_questions[0], company_code)
    else:
        with ThreadPoolExecutor() as executor:
            futures = [executor.submit(_search_sub_q_raw, sq, company_code) for sq in sub_questions]
            for f in futures:
                for d in f.result():
                    key = d.page_content[:80]
                    if key not in seen:
                        seen.add(key)
                        docs.append(d)
        docs = docs[:min(3 * len(sub_questions), 6)]

    return docs, sub_questions


# ── Public 인터페이스 ──────────────────────────────────────────

@dataclass
class RetrievalResult:
    question: str
    docs: List[Document]
    source_names: str
    template_ids: List[int]
    template_titles: List[str]
    formatted_context: str
    doc_ids: List[int]
    ambiguous_response: str | None = None
    direct_legal_answer: str | None = None


def retrieve(
    question: str,
    company_code: str,
    chat_history: List[BaseMessage],
) -> RetrievalResult:
    resolved = resolve_selection(question, chat_history)
    if resolved:
        question = resolved

    ambiguous = check_ambiguous(question)
    if ambiguous:
        return RetrievalResult(
            question=question, docs=[], source_names="",
            template_ids=[], template_titles=[], formatted_context="",
            doc_ids=[], ambiguous_response=ambiguous,
        )

    docs, _ = _do_search(question, company_code)
    template_ids, template_titles = match_template_docs(company_code, question)
    doc_ids = list(
        {int(d.metadata["doc_id"]) for d in docs
         if d.metadata.get("doc_id") and d.metadata.get("company_code", "") == company_code}
        | set(template_ids)
    )

    if is_direct_legal_question(question):
        return RetrievalResult(
            question=question, docs=docs, source_names=extract_sources(docs, max_sources=5),
            template_ids=template_ids, template_titles=template_titles,
            formatted_context="", doc_ids=doc_ids,
            direct_legal_answer=format_legal_answer(docs, question, company_code),
        )

    formatted_context, included_docs = _build_context(docs, template_titles, question)
    return RetrievalResult(
        question=question, docs=docs, source_names=extract_sources(included_docs),
        template_ids=template_ids, template_titles=template_titles,
        formatted_context=formatted_context, doc_ids=doc_ids,
    )


async def async_retrieve(
    question: str,
    company_code: str,
    chat_history: List[BaseMessage],
) -> RetrievalResult:
    resolved = resolve_selection(question, chat_history)
    if resolved:
        question = resolved

    ambiguous = check_ambiguous(question)
    if ambiguous:
        return RetrievalResult(
            question=question, docs=[], source_names="",
            template_ids=[], template_titles=[], formatted_context="",
            doc_ids=[], ambiguous_response=ambiguous,
        )

    loop = asyncio.get_event_loop()
    docs, _ = await loop.run_in_executor(None, _do_search, question, company_code)
    template_ids, template_titles = match_template_docs(company_code, question)
    doc_ids = list(
        {int(d.metadata["doc_id"]) for d in docs
         if d.metadata.get("doc_id") and d.metadata.get("company_code", "") == company_code}
        | set(template_ids)
    )

    if is_direct_legal_question(question):
        return RetrievalResult(
            question=question, docs=docs, source_names=extract_sources(docs, max_sources=5),
            template_ids=template_ids, template_titles=template_titles,
            formatted_context="", doc_ids=doc_ids,
            direct_legal_answer=format_legal_answer(docs, question, company_code),
        )

    formatted_context, included_docs = _build_context(docs, template_titles, question)
    return RetrievalResult(
        question=question, docs=docs, source_names=extract_sources(included_docs),
        template_ids=template_ids, template_titles=template_titles,
        formatted_context=formatted_context, doc_ids=doc_ids,
    )


def _build_context(docs: List[Document], template_titles: List[str], question: str) -> tuple[str, List[Document]]:
    ctx, included = format_docs(docs)
    if template_titles:
        ctx = f"📎 이 답변 하단 카드에 양식 파일이 첨부됩니다: {', '.join(template_titles)}\n\n" + ctx
    else:
        ctx = "⛔ 이 답변에는 첨부 파일이 없습니다. '파일이 첨부됐다', '카드에서 다운로드', '파일을 직접 제공할 수 없다' 같은 파일 관련 언급은 일절 하지 마세요.\n\n" + ctx
    if is_legal_question(question):
        ctx = (
            "⚠️ 아래는 법령 원문입니다. 각 항목(1., 2., 3., 4. ...)의 문장을 절대 바꾸거나 해석하지 말고 원문 그대로 전달하세요.\n\n"
            + ctx
        )
    return ctx, included
