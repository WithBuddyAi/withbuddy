"""
Slack 스케줄 관리 API
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from tasks.scheduler import get_schedule_time, reschedule, _send_weekly_no_result_summary

router = APIRouter(prefix="/slack", tags=["slack"])


class ScheduleRequest(BaseModel):
    hour: int   = Field(..., ge=0, le=23, description="시 (0~23)")
    minute: int = Field(..., ge=0, le=59, description="분 (0~59)")


class ScheduleResponse(BaseModel):
    hour: int
    minute: int
    message: str



@router.get("/schedule", response_model=ScheduleResponse)
def get_schedule():
    """현재 자동 발송 시간을 조회합니다."""
    t = get_schedule_time()
    return ScheduleResponse(
        hour=t["hour"],
        minute=t["minute"],
        message=f"현재 설정: 평일 {t['hour']:02d}:{t['minute']:02d}",
    )


@router.post("/schedule", response_model=ScheduleResponse)
def update_schedule(req: ScheduleRequest):
    """자동 발송 시간을 변경합니다 (평일 기준, KST)."""
    try:
        reschedule(req.hour, req.minute)
        return ScheduleResponse(
            hour=req.hour,
            minute=req.minute,
            message=f"스케줄 변경 완료: 평일 {req.hour:02d}:{req.minute:02d}",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/trigger-summary")
async def trigger_summary():
    """미답변 질문 요약 리포트를 즉시 Slack으로 발송합니다."""
    try:
        await _send_weekly_no_result_summary()
        return {"message": "요약 리포트 발송 완료"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
