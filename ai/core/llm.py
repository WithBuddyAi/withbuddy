"""
LLM 초기화 모듈
────────────────────────────────────────────
Anthropic Claude API를 사용하는 LLM 인스턴스를 생성합니다.
싱글톤 패턴(@lru_cache)으로 앱 실행 중 한 번만 생성합니다.

폴백: OPENAI_API_KEY 환경변수가 있으면 Anthropic 서버 장애 시
      GPT-4.1-mini로 자동 전환합니다.
"""

import os
from functools import lru_cache

import anthropic as _anthropic
from langchain_anthropic import ChatAnthropic
from langchain_core.runnables import Runnable

_ANTHROPIC_SERVER_ERRORS = (
    _anthropic.InternalServerError,
    _anthropic.APIConnectionError,
    _anthropic.APITimeoutError,
)


def _make_openai_fallback(max_tokens: int, temperature: float = 0.0):
    """GPT-4.1-mini 폴백 LLM 생성. OPENAI_API_KEY 없으면 None 반환."""
    openai_key = os.getenv("OPENAI_API_KEY")
    if not openai_key:
        return None
    try:
        from langchain_openai import ChatOpenAI
        return ChatOpenAI(
            model="gpt-4.1-mini",
            api_key=openai_key,
            temperature=temperature,
            max_tokens=max_tokens,
        )
    except ImportError:
        return None


@lru_cache(maxsize=1)
def get_intent_llm() -> Runnable:
    """Intent 분류 전용 경량 LLM (max_tokens=20, 단어 하나만 반환)"""
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        raise ValueError("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.")
    primary = ChatAnthropic(
        model="claude-haiku-4-5-20251001",
        anthropic_api_key=api_key,
        temperature=0.0,
        max_tokens=20,
        max_retries=4,
    )
    fallback = _make_openai_fallback(max_tokens=20)
    if fallback:
        return primary.with_fallbacks([fallback], exceptions_to_handle=_ANTHROPIC_SERVER_ERRORS)
    return primary


@lru_cache(maxsize=1)
def get_combined_llm() -> Runnable:
    """Intent + Clarifying 통합 분류 LLM (temperature=0.0, JSON 출력)"""
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        raise ValueError("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.")
    primary = ChatAnthropic(
        model="claude-haiku-4-5-20251001",
        anthropic_api_key=api_key,
        temperature=0.0,
        max_tokens=300,
        max_retries=4,
    )
    fallback = _make_openai_fallback(max_tokens=300)
    if fallback:
        return primary.with_fallbacks([fallback], exceptions_to_handle=_ANTHROPIC_SERVER_ERRORS)
    return primary


@lru_cache(maxsize=1)
def get_llm() -> Runnable:
    """
    Claude LLM 인스턴스 반환 (싱글톤)

    Returns:
        Runnable: Claude Haiku LLM (장애 시 GPT-4.1-mini 자동 폴백)

    Raises:
        ValueError: ANTHROPIC_API_KEY 환경변수가 설정되지 않은 경우
    """
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        raise ValueError(
            "ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다. "
            ".env 파일을 확인해주세요."
        )
    primary = ChatAnthropic(
        model="claude-haiku-4-5-20251001",
        anthropic_api_key=api_key,
        temperature=0.3,
        max_tokens=2048,
        max_retries=4,
    )
    fallback = _make_openai_fallback(max_tokens=2048, temperature=0.3)
    if fallback:
        return primary.with_fallbacks([fallback], exceptions_to_handle=_ANTHROPIC_SERVER_ERRORS)
    return primary
