"""
Slack 리포트 수동 트리거 API
────────────────────────────────────────────
테스트 또는 즉시 전송이 필요할 때 사용합니다.
스케줄러가 자동으로 평일 17:00에 호출하지만,
이 엔드포인트로 언제든 수동 전송할 수 있습니다.
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from tasks.scheduler import get_schedule_time, reschedule
from tasks.slack_notifier import send_all_reports, send_single_report

router = APIRouter(prefix="/slack", tags=["slack"])


class SendReportResponse(BaseModel):
    message: str


class ScheduleRequest(BaseModel):
    hour: int   = Field(..., ge=0, le=23, description="시 (0~23)")
    minute: int = Field(..., ge=0, le=59, description="분 (0~59)")


class ScheduleResponse(BaseModel):
    hour: int
    minute: int
    message: str


@router.post("/send-report", response_model=SendReportResponse)
async def trigger_all_reports():
    """
    전체 팀 수습사원 진척도 리포트를 Slack으로 즉시 전송합니다.
    team_config.json에 등록된 모든 리더에게 전송됩니다.
    """
    try:
        await send_all_reports()
        return SendReportResponse(message="전체 팀 리포트 전송이 완료되었습니다.")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/send-report/{user_id}", response_model=SendReportResponse)
async def trigger_single_report(user_id: str):
    """
    특정 수습사원(user_id)의 진척도 리포트만 해당 리더에게 Slack으로 즉시 전송합니다.
    """
    try:
        await send_single_report(user_id)
        return SendReportResponse(message=f"{user_id} 리포트 전송이 완료되었습니다.")
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


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
