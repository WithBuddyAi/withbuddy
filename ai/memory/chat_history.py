"""
대화 히스토리 관리 모듈
────────────────────────────────────────────
사용자별 멀티턴 대화를 관리합니다.
JSON 파일로 영속화하여 서버 재시작 후에도 이전 대화가 유지됩니다.
data/history/{user_id}.json 에 저장됩니다.
"""

import json
from pathlib import Path
from typing import Dict, List

from langchain_core.chat_history import InMemoryChatMessageHistory
from langchain_core.messages import BaseMessage

# 히스토리 저장 디렉토리
_HISTORY_DIR = Path(__file__).parent.parent / "data" / "history"
_HISTORY_DIR.mkdir(parents=True, exist_ok=True)

# 인메모리 캐시: 파일을 매번 읽지 않기 위해 한 번 로드 후 캐시
_memory_store: Dict[str, InMemoryChatMessageHistory] = {}

_MAX_TURNS = 5  # 최대 유지 대화 쌍 수


def _history_path(user_id: str) -> Path:
    return _HISTORY_DIR / f"{user_id}.json"


def _load_from_file(user_id: str) -> InMemoryChatMessageHistory:
    mem = InMemoryChatMessageHistory()
    path = _history_path(user_id)
    if path.exists():
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            for item in data:
                if item.get("role") == "human":
                    mem.add_user_message(item["content"])
                elif item.get("role") == "ai":
                    mem.add_ai_message(item["content"])
        except Exception:
            pass
    return mem


def _save_to_file(user_id: str, mem: InMemoryChatMessageHistory) -> None:
    data = [
        {"role": "human" if msg.type == "human" else "ai", "content": msg.content}
        for msg in mem.messages
    ]
    _history_path(user_id).write_text(
        json.dumps(data, ensure_ascii=False), encoding="utf-8"
    )


def get_memory(user_id: str) -> InMemoryChatMessageHistory:
    if user_id not in _memory_store:
        _memory_store[user_id] = _load_from_file(user_id)
    return _memory_store[user_id]


def get_chat_history(user_id: str) -> List[BaseMessage]:
    return get_memory(user_id).messages


def save_interaction(user_id: str, human_message: str, ai_message: str) -> None:
    mem = get_memory(user_id)
    mem.add_user_message(human_message)
    mem.add_ai_message(ai_message)

    # 오래된 메시지 정리
    max_messages = _MAX_TURNS * 2
    if len(mem.messages) > max_messages:
        mem.messages = mem.messages[-max_messages:]

    _save_to_file(user_id, mem)


def clear_memory(user_id: str) -> None:
    if user_id in _memory_store:
        _memory_store[user_id].clear()
    path = _history_path(user_id)
    if path.exists():
        path.unlink()


def get_history_as_text(user_id: str) -> str:
    messages = get_chat_history(user_id)
    if not messages:
        return "대화 내역이 없습니다."
    lines = []
    for msg in messages:
        role = "사용자" if msg.type == "human" else "AI"
        lines.append(f"{role}: {msg.content}")
    return "\n".join(lines)
