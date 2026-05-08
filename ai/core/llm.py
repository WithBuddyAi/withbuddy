"""
LLM 초기화 모듈
────────────────────────────────────────────
Anthropic Claude API를 사용하는 LLM 인스턴스를 생성합니다.
싱글톤 패턴(@lru_cache)으로 앱 실행 중 한 번만 생성합니다.
"""

import os
from functools import lru_cache

from langchain_anthropic import ChatAnthropic
from utils.circuit_breaker import CostTrackerCallback


@lru_cache(maxsize=1)
def get_intent_llm() -> ChatAnthropic:
    """Intent 분류 전용 경량 LLM (max_tokens=20, 단어 하나만 반환)"""
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        raise ValueError("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.")
    return ChatAnthropic(
        model="claude-haiku-4-5-20251001",
        anthropic_api_key=api_key,
        temperature=0.0,
        max_tokens=20,
        callbacks=[CostTrackerCallback()],
    )


@lru_cache(maxsize=1)
def get_llm() -> ChatAnthropic:
    """
    Claude LLM 인스턴스 반환 (싱글톤)

    Returns:
        ChatAnthropic: Claude Sonnet LLM 인스턴스

    Raises:
        ValueError: ANTHROPIC_API_KEY 환경변수가 설정되지 않은 경우
    """
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        raise ValueError(
            "ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다. "
            ".env 파일을 확인해주세요."
        )

    return ChatAnthropic(
        model="claude-haiku-4-5-20251001",  # 비용 효율적인 Haiku 모델
        anthropic_api_key=api_key,
        temperature=0.3,                   # Q&A는 일관성 우선
        max_tokens=1024,                   # 온보딩 Q&A에 충분한 길이
        callbacks=[CostTrackerCallback()],
    )
