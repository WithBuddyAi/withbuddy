"""
온보딩 체크리스트 생성 체인 모듈
────────────────────────────────────────────
부서명을 입력받아 해당 부서 수습사원에게 맞는
온보딩 체크리스트를 Claude LLM으로 생성합니다.
LCEL 파이프라인으로 구성되어 있습니다.
"""

from langchain_core.output_parsers import StrOutputParser

from core.llm import get_llm
from utils.prompts import CHECKLIST_PROMPT

_chain = None


def run_checklist_chain(department: str = "일반") -> str:
    """
    온보딩 체크리스트 생성 체인 실행

    Args:
        department: 부서명 (기본값: "일반")

    Returns:
        str: 마크다운 체크리스트 형식의 온보딩 항목 목록
    """
    global _chain
    if _chain is None:
        _chain = CHECKLIST_PROMPT | get_llm() | StrOutputParser()

    return _chain.invoke({"department": department})
