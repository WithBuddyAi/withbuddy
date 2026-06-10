"""
AI 응답 분석 데이터 저장 모듈
────────────────────────────────────────────
매 응답마다 messageType / latencyMs / 질문 미리보기를 Redis에 누적합니다.
일별 집계 후 Slack 리포트 생성에 활용됩니다.
"""

import logging
from datetime import date, datetime, timezone
from typing import Optional

from core.be_client import cache_get, cache_set

logger = logging.getLogger(__name__)

_NAMESPACE = "analytics"
_TTL = 86400  # BE cache 최대 1일 제한


def _key(company_code: str, day: str) -> str:
    cc = company_code.strip() or "ALL"
    return f"daily:{cc}:{day}"


def record(
    company_code: str,
    message_type: str,
    latency_ms: int,
    question: str = "",
) -> None:
    """응답 1건을 일별 analytics에 누적합니다."""
    try:
        day = date.today().isoformat()
        key = _key(company_code, day)
        entries: list = cache_get(_NAMESPACE, key) or []
        entries.append({
            "ts": int(datetime.now(timezone.utc).timestamp()),
            "mt": message_type,
            "ms": latency_ms,
            "q":  question[:100],
        })
        cache_set(_NAMESPACE, key, entries, _TTL)
    except Exception as e:
        logger.warning("analytics record 실패(무시): %s", e)


def get_stats(company_code: str, day: Optional[str] = None) -> dict:
    """특정 날짜의 집계 통계를 반환합니다."""
    day = day or date.today().isoformat()
    try:
        entries: list = cache_get(_NAMESPACE, _key(company_code, day)) or []
    except Exception:
        entries = []

    total = len(entries)
    by_type: dict[str, int] = {}
    latencies: list[int] = []
    no_result_qs: list[str] = []

    for e in entries:
        mt = e.get("mt", "unknown")
        by_type[mt] = by_type.get(mt, 0) + 1
        ms = e.get("ms")
        if ms:
            latencies.append(ms)
        if mt == "no_result" and e.get("q"):
            no_result_qs.append(e["q"])

    return {
        "date": day,
        "company_code": company_code or "ALL",
        "total": total,
        "by_type": by_type,
        "avg_latency_ms": int(sum(latencies) / len(latencies)) if latencies else 0,
        "no_result_questions": no_result_qs[:10],
    }
