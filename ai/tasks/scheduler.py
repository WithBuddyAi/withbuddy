"""
APScheduler 기반 자동 리포트 스케줄러
────────────────────────────────────────────
평일(월~금) 오후 5시에 전체 수습사원 진척도 리포트를
자동으로 Slack 전송합니다.
"""

import logging

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from tasks.slack_notifier import send_all_reports, send_mybuddy_checkin_all

logger = logging.getLogger(__name__)

_scheduler = AsyncIOScheduler(timezone="Asia/Seoul")


def start_scheduler() -> None:
    """스케줄러 시작 — FastAPI 앱 시작 시 호출"""
    _scheduler.add_job(
        send_all_reports,
        CronTrigger(
            day_of_week="mon-fri",  # 평일만
            hour=17,                # 오후 5시
            minute=0,
            timezone="Asia/Seoul",
        ),
        id="daily_slack_report",
        replace_existing=True,
        misfire_grace_time=300,     # 5분 이내 지연 허용
    )
    _scheduler.add_job(
        send_mybuddy_checkin_all,
        CronTrigger(
            day_of_week="mon-fri",  # 평일만
            hour=9,                 # 오전 9시
            minute=0,
            timezone="Asia/Seoul",
        ),
        id="mybuddy_morning_checkin",
        replace_existing=True,
        misfire_grace_time=300,
    )
    _scheduler.start()
    logger.info("스케줄러 시작 — 평일 17:00 리포트 / 09:00 My Buddy 체크인 활성화")


def stop_scheduler() -> None:
    """스케줄러 종료 — FastAPI 앱 종료 시 호출"""
    if _scheduler.running:
        _scheduler.shutdown(wait=False)
        logger.info("스케줄러 종료")


def reschedule(hour: int, minute: int) -> None:
    """자동 발송 시간 변경 (평일 기준)"""
    _scheduler.reschedule_job(
        "daily_slack_report",
        trigger=CronTrigger(
            day_of_week="mon-fri",
            hour=hour,
            minute=minute,
            timezone="Asia/Seoul",
        ),
    )
    logger.info("스케줄 변경 — 평일 %02d:%02d", hour, minute)


def get_schedule_time() -> dict:
    """현재 설정된 자동 발송 시간 반환"""
    job = _scheduler.get_job("daily_slack_report")
    if not job:
        return {"hour": 17, "minute": 0}
    trigger = job.trigger
    hour   = int(str(trigger.fields[5]))   # hour field
    minute = int(str(trigger.fields[6]))   # minute field
    return {"hour": hour, "minute": minute}
