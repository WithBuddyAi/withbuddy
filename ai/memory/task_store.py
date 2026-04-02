"""
My Buddy 태스크 저장소
────────────────────────────────────────────
수습사원별 온보딩 태스크를 JSON 파일로 관리합니다.
data/tasks/{user_id}.json 에 저장됩니다.
"""

import json
import uuid
from datetime import date
from pathlib import Path

_TASKS_DIR = Path(__file__).parent.parent / "data" / "tasks"
_TASKS_DIR.mkdir(parents=True, exist_ok=True)


def _path(user_id: str) -> Path:
    return _TASKS_DIR / f"{user_id}.json"


def _load(user_id: str) -> list:
    p = _path(user_id)
    if not p.exists():
        return []
    return json.loads(p.read_text(encoding="utf-8"))


def _save(user_id: str, tasks: list) -> None:
    _path(user_id).write_text(
        json.dumps(tasks, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def get_all_tasks(user_id: str) -> list:
    return _load(user_id)


def get_today_pending(user_id: str) -> list:
    """오늘 날짜에 해당하고 아직 미완료인 태스크 반환"""
    today = date.today().isoformat()
    return [t for t in _load(user_id) if t["due_date"] == today and not t["done"]]


def add_task(user_id: str, title: str, due_date: str, description: str = "") -> dict:
    """태스크 추가. 생성된 태스크 반환."""
    tasks = _load(user_id)
    task = {
        "id": str(uuid.uuid4())[:8],
        "title": title,
        "description": description,
        "due_date": due_date,
        "done": False,
        "created_at": date.today().isoformat(),
    }
    tasks.append(task)
    _save(user_id, tasks)
    return task


def mark_done(user_id: str, task_id: str, done: bool = True) -> dict | None:
    """태스크 완료/미완료 처리. 해당 태스크 반환."""
    tasks = _load(user_id)
    for task in tasks:
        if task["id"] == task_id:
            task["done"] = done
            _save(user_id, tasks)
            return task
    return None
