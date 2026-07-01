"""
APScheduler 기반 자동 리포트 스케줄러
────────────────────────────────────────────
평일(월~금) 오후 5시에 전체 수습사원 진척도 리포트를
자동으로 Slack 전송합니다.
"""

import asyncio
import logging

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from tasks.slack_notifier import send_no_result_summary

logger = logging.getLogger(__name__)


async def _send_weekly_no_result_summary() -> None:
    """매주 월요일 오전 — 회사별 미답변 질문 요약 Slack 발송 (최근 7일, WB9999 제외)"""
    from collections import defaultdict
    from datetime import datetime, timedelta, timezone
    from memory.unanswered_store import get_pending
    from routers.knowledge import _run_summary

    pending = get_pending()
    if not pending:
        return

    cutoff = datetime.now(timezone.utc) - timedelta(days=7)
    by_company: dict[str, list[str]] = defaultdict(list)
    for item in pending:
        cc = item.get("company_code") or ""
        if cc == "WB9999":
            continue
        ts_str = item.get("timestamp", "")
        try:
            ts = datetime.fromisoformat(ts_str).replace(tzinfo=timezone.utc) if ts_str else cutoff
        except ValueError:
            ts = cutoff
        if ts < cutoff:
            continue
        by_company[cc or "공통"].append(item["question"])

    for company_code, questions in by_company.items():
        try:
            summary, actions = await asyncio.to_thread(_run_summary, questions)
            await send_no_result_summary(company_code, summary, actions, len(questions))
        except Exception as e:
            logger.error("주간 no_result 요약 실패 (%s): %s", company_code, e)


async def _keepalive_llm() -> None:
    """LLM 콜드 스타트 방지 — 1시간마다 최소 호출로 연결 유지"""
    try:
        from core.llm import get_llm
        await asyncio.to_thread(get_llm().invoke, "ping")
        logger.debug("LLM keep-alive 완료")
    except Exception as e:
        logger.warning("LLM keep-alive 실패(무시): %s", e)

_scheduler = AsyncIOScheduler(timezone="Asia/Seoul")


def start_scheduler() -> None:
    """스케줄러 시작 — FastAPI 앱 시작 시 호출"""
    _scheduler.add_job(
        _keepalive_llm,
        "interval",
        minutes=60,
        id="llm_keepalive",
        replace_existing=True,
    )
    _scheduler.add_job(
        _send_weekly_no_result_summary,
        CronTrigger(
            day_of_week="mon",
            hour=9,
            minute=0,
            timezone="Asia/Seoul",
        ),
        id="weekly_no_result_summary",
        replace_existing=True,
        misfire_grace_time=300,
    )
    _scheduler.start()
    logger.info("스케줄러 시작 — 평일 17:30 analytics / 월 09:00 미답변 요약 / 60분 LLM keep-alive 활성화")


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
