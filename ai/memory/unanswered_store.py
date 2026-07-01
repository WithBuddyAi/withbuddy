"""
미답변 질문 저장소
────────────────────────────────────────────
AI가 답변을 찾지 못한 질문을 JSON 파일로 관리합니다.
리더가 직접 답변을 입력하면 AI 지식으로 저장됩니다.
"""

import json
import uuid
from datetime import datetime
from pathlib import Path

_PATH = Path(__file__).parent.parent / "data" / "unanswered.json"


def _load() -> list:
    if not _PATH.exists():
        return []
    return json.loads(_PATH.read_text(encoding="utf-8"))


def _save(items: list) -> None:
    _PATH.parent.mkdir(parents=True, exist_ok=True)
    _PATH.write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding="utf-8")


def add_unanswered(user_id: str, question: str, company_code: str = "") -> str:
    """미답변 질문 추가. 생성된 ID 반환."""
    items = _load()
    qid = str(uuid.uuid4())[:8]
    items.append({
        "id": qid,
        "user_id": user_id,
        "company_code": company_code,
        "question": question,
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "status": "pending",
        "answer": None,
        "answered_at": None,
    })
    _save(items)
    return qid


def get_all() -> list:
    return _load()


def get_pending() -> list:
    return [i for i in _load() if i["status"] == "pending"]


def delete_question(qid: str) -> bool:
    """질문 삭제. 성공 시 True 반환."""
    items = _load()
    new_items = [i for i in items if i["id"] != qid]
    if len(new_items) == len(items):
        return False
    _save(new_items)
    return True


def save_chunk_ids(qid: str, chunk_ids: list) -> None:
    """ChromaDB 청크 ID 저장 — 답변 수정/삭제 시 사용."""
    items = _load()
    for item in items:
        if item["id"] == qid:
            item["chunk_ids"] = chunk_ids
            _save(items)
            return


def save_embeddings_batch(updates: dict) -> None:
    """여러 질문의 임베딩 일괄 저장. {qid: embedding_vector}"""
    items = _load()
    changed = False
    for item in items:
        if item["id"] in updates:
            item["embedding"] = updates[item["id"]]
            changed = True
    if changed:
        _save(items)


def answer_question(qid: str, answer: str) -> dict | None:
    """질문에 답변 저장. 성공 시 해당 항목 반환."""
    items = _load()
    for item in items:
        if item["id"] == qid:
            item["status"] = "answered"
            item["answer"] = answer
            item["answered_at"] = datetime.now().isoformat(timespec="seconds")
            _save(items)
            return item
    return None
