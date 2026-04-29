"""
복합 질문 분리·분류·응답 조합 (스토리 2-8)
IN + OUT SCOPE 복합 질문을 파트별로 처리합니다.
"""
import json
import re

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from core.llm import get_llm

# rag_chain.py와 동일한 split 패턴
_SPLIT_PATTERN = re.compile(
    r'[?？]\s*그리고|그리고\s*[?？]?|[?？]\s+|이랑\s+|이\s*궁금하고,?\s*'
)

_CLASSIFY_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """HR·복지·행정 질문 분류기입니다.
주어진 파트들을 각각 아래 기준으로 분류하세요.

- in_scope: 회사 HR·복지·행정·IT 관련 (연차, 반차, 경조금, 복지카드, 문화상품권, 출입카드, 증명서 발급, 야근식대, 급여, 장비신청, 퇴직금 계산, 퇴직급여, 업무 툴 계정 세팅·초기 설정·VPN 연결, 건강검진 신청, 경조사 지원 등 회사 문서나 법령으로 답변 가능한 것)
- out_직무실무: 직무 실무·업무 방법론 (코드 리뷰 방법, 기획서 작성 팁, 보고서 작성 방식, 회의 진행법, 프로젝트 관리 방법론, 업무 툴 고급 활용법·사용 방법론 등 사수님이 답할 수 있는 것)
- out_전문가: 세무·투자 등 전문가 영역 (세금 계산, 연말정산 환급액, 증여세 비과세 한도, 투자 조언, 개인 재무 상담 등)
- sensitive: 법적 분쟁·윤리적 피해·감정적 위기 (부당해고 소송, 소송 방법, 성희롱 피해 진술, 번아웃, 퇴사하고 싶다 등 경영지원팀 안내가 필요한 것)

JSON 배열만 반환 (다른 텍스트 없음):
[{{"text": "파트 원문", "type": "분류결과"}}, ...]"""),
    ("human", "분류할 파트들:\n{parts}"),
])

_classify_chain = None


def _get_classify_chain():
    global _classify_chain
    if _classify_chain is None:
        _classify_chain = _CLASSIFY_PROMPT | get_llm() | StrOutputParser()
    return _classify_chain


def split_composite(question: str) -> list[str]:
    """복합 질문을 파트별로 분리. 단일 질문이면 빈 리스트 반환."""
    parts = [
        q.strip().rstrip("?？！ ")
        for q in _SPLIT_PATTERN.split(question)
        if q.strip() and len(q.strip()) > 2
    ]
    return parts if len(parts) >= 2 else []


def classify_parts(parts: list[str]) -> list[dict]:
    """
    파트들을 LLM으로 분류.
    Returns: [{"text": ..., "type": "in_scope"|"out_직무실무"|"out_전문가"|"sensitive"}, ...]
    """
    if not parts:
        return []
    parts_text = "\n".join(f"- {p}" for p in parts)
    raw = _get_classify_chain().invoke({"parts": parts_text}).strip()
    # JSON 블록 추출
    match = re.search(r'\[.*?\]', raw, re.DOTALL)
    if match:
        raw = match.group(0)
    try:
        result = json.loads(raw)
        if isinstance(result, list) and all("text" in r and "type" in r for r in result):
            return result
    except Exception:
        pass
    # 파싱 실패 시 모두 in_scope로 fallback
    return [{"text": p, "type": "in_scope"} for p in parts]


def make_out_text(part_text: str, part_type: str) -> str:
    if part_type == "out_직무실무":
        return (
            f"함께 문의하신 '{part_text}'는 팀마다 방식이 달라서 "
            "사수님이나 팀 선배님께 여쭤보시는 게 가장 정확해요! 😊"
        )
    if part_type == "out_전문가":
        return (
            f"함께 문의하신 '{part_text}'는 전문가 영역이라 "
            "정확히 답변드리기 어려워요. 전문가에게 확인해 보시는 게 가장 안전해요."
        )
    return ""
