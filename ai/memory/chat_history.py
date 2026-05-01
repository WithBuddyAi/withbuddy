"""
대화 히스토리 관리 모듈
────────────────────────────────────────────
BE Internal Cache API를 통해 Redis에 영속화합니다.
로컬 인메모리 캐시로 BE 요청을 최소화합니다.
"""

from typing import Dict, List

from langchain_core.chat_history import InMemoryChatMessageHistory
from langchain_core.messages import BaseMessage

from core.be_client import cache_del, cache_get, cache_set

_MAX_TURNS = 5
_TTL = 86400  # 24시간

_local: Dict[str, InMemoryChatMessageHistory] = {}


def _cache_key(user_id: str) -> str:
    return f"history:{user_id}"


def _load(user_id: str) -> InMemoryChatMessageHistory:
    mem = InMemoryChatMessageHistory()
    data = cache_get("chat", _cache_key(user_id))
    if data and isinstance(data, list):
        for item in data:
            if item.get("role") == "human":
                mem.add_user_message(item["content"])
            elif item.get("role") == "ai":
                mem.add_ai_message(item["content"])
    return mem


def _persist(user_id: str, mem: InMemoryChatMessageHistory) -> None:
    data = [
        {"role": "human" if msg.type == "human" else "ai", "content": msg.content}
        for msg in mem.messages
    ]
    cache_set("chat", _cache_key(user_id), data, _TTL)


def get_memory(user_id: str) -> InMemoryChatMessageHistory:
    if user_id not in _local:
        _local[user_id] = _load(user_id)
    return _local[user_id]


def get_chat_history(user_id: str) -> List[BaseMessage]:
    return get_memory(user_id).messages


def save_interaction(user_id: str, human_message: str, ai_message: str) -> None:
    mem = get_memory(user_id)
    mem.add_user_message(human_message)
    mem.add_ai_message(ai_message)

    max_messages = _MAX_TURNS * 2
    if len(mem.messages) > max_messages:
        mem.messages = mem.messages[-max_messages:]

    _persist(user_id, mem)


def clear_memory(user_id: str) -> None:
    _local.pop(user_id, None)
    cache_del("chat", _cache_key(user_id))


def get_history_as_text(user_id: str) -> str:
    messages = get_chat_history(user_id)
    if not messages:
        return "대화 내역이 없습니다."
    lines = []
    for msg in messages:
        role = "사용자" if msg.type == "human" else "AI"
        lines.append(f"{role}: {msg.content}")
    return "\n".join(lines)
