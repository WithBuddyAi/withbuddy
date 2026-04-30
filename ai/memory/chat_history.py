"""
대화 히스토리 관리 모듈
────────────────────────────────────────────
Redis 기반 영속화. 서버 재시작·다중 인스턴스 환경에서도 히스토리 유지.
Key 규칙: withbuddy:history:{user_id}
TTL    : 7일 (마지막 대화로부터 갱신)
"""

from typing import List

from langchain_community.chat_message_histories import RedisChatMessageHistory
from langchain_core.messages import BaseMessage

from core.config import HISTORY_MAX_TURNS, HISTORY_TTL, REDIS_URL


def _get_redis_history(user_id: str) -> RedisChatMessageHistory:
    return RedisChatMessageHistory(
        session_id=user_id,
        url=REDIS_URL,
        key_prefix="withbuddy:history:",
        ttl=HISTORY_TTL,
    )


def get_memory(user_id: str) -> RedisChatMessageHistory:
    return _get_redis_history(user_id)


def get_chat_history(user_id: str) -> List[BaseMessage]:
    return _get_redis_history(user_id).messages[-HISTORY_MAX_TURNS * 2:]


def save_interaction(user_id: str, human_message: str, ai_message: str) -> None:
    history = _get_redis_history(user_id)
    history.add_user_message(human_message)
    history.add_ai_message(ai_message)
    messages = history.messages
    if len(messages) > HISTORY_MAX_TURNS * 2:
        history.clear()
        for msg in messages[-(HISTORY_MAX_TURNS * 2):]:
            if msg.type == "human":
                history.add_user_message(msg.content)
            else:
                history.add_ai_message(msg.content)


def clear_memory(user_id: str) -> None:
    _get_redis_history(user_id).clear()


def get_history_as_text(user_id: str) -> str:
    messages = get_chat_history(user_id)
    if not messages:
        return "대화 내역이 없습니다."
    return "\n".join(
        f"{'사용자' if msg.type == 'human' else 'AI'}: {msg.content}"
        for msg in messages
    )
