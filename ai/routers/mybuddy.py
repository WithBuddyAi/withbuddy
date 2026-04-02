"""
My Buddy API 라우터
────────────────────────────────────────────
GET  /mybuddy/today/{user_id}           : 오늘 미완료 태스크 + 체크인 메시지 반환
POST /mybuddy/task/{user_id}/{task_id}  : 태스크 완료/미완료 처리
POST /mybuddy/task/{user_id}            : 태스크 추가 (리더/관리자용)
GET  /mybuddy/tasks/{user_id}           : 전체 태스크 목록
"""

from fastapi import APIRouter
from pydantic import BaseModel, Field

from memory.task_store import (
    get_today_pending,
    get_all_tasks,
    add_task,
    mark_done,
)

router = APIRouter(prefix="/mybuddy", tags=["mybuddy"])


# ── 스키마 ──────────────────────────────────

class TaskCreateRequest(BaseModel):
    title: str = Field(..., description="태스크 제목", example="IT 장비 신청")
    due_date: str = Field(..., description="수행 날짜 (YYYY-MM-DD)", example="2026-03-14")
    description: str = Field("", description="태스크 상세 설명")


class DoneRequest(BaseModel):
    done: bool = Field(True, description="완료 여부")


# ── 엔드포인트 ──────────────────────────────

@router.get("/today/{user_id}")
async def get_today_checkin(user_id: str):
    """
    웹 접속 시 호출. 오늘 미완료 태스크와 My Buddy 체크인 메시지를 반환합니다.
    태스크가 없으면 message는 None.
    """
    pending = get_today_pending(user_id)

    if not pending:
        return {"tasks": [], "message": None}

    # 태스크 목록 → 체크인 메시지 생성
    task_lines = "\n".join(f"• {t['title']}" for t in pending)
    message = (
        f"안녕하세요! My Buddy예요 👋\n"
        f"오늘 해야 할 항목이 {len(pending)}개 있어요!\n\n"
        f"{task_lines}\n\n"
        f"완료한 항목은 체크해주세요 ✅"
    )

    return {"tasks": pending, "message": message}


@router.post("/task/{user_id}/{task_id}")
async def update_task_done(user_id: str, task_id: str, req: DoneRequest):
    """태스크 완료/미완료 처리"""
    task = mark_done(user_id, task_id, req.done)
    if not task:
        return {"ok": False, "error": "태스크를 찾을 수 없습니다."}
    return {"ok": True, "task": task}


@router.post("/task/{user_id}")
async def create_task(user_id: str, req: TaskCreateRequest):
    """태스크 추가 (리더가 수습사원 태스크를 등록할 때 사용)"""
    task = add_task(user_id, req.title, req.due_date, req.description)
    return {"ok": True, "task": task}


@router.get("/tasks/{user_id}")
async def list_tasks(user_id: str):
    """전체 태스크 목록 반환"""
    return {"tasks": get_all_tasks(user_id)}
