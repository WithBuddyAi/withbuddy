"""
주간 리포트 요약 체인 모듈
────────────────────────────────────────────
사용자의 이번 주 대화 내역을 분석하여 온보딩 주간 리포트를 생성합니다.
인메모리 ConversationBufferMemory에 저장된 대화를 텍스트로 변환한 뒤
Claude LLM에 전달하는 LCEL 파이프라인입니다.
"""

from langchain_core.output_parsers import StrOutputParser

from core.llm import get_llm
from memory.chat_history import get_history_as_text
from utils.prompts import REPORT_PROMPT

_chain = None


def run_summary_chain(user_id: str) -> str:
    """
    주간 리포트 생성 체인 실행

    Args:
        user_id: 사용자 식별자

    Returns:
        str: 마크다운 형식의 주간 온보딩 리포트
    """
    global _chain
    if _chain is None:
        _chain = REPORT_PROMPT | get_llm() | StrOutputParser()

    return _chain.invoke({"chat_history": get_history_as_text(user_id)})
