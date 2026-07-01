"""
Slack 채널 라우팅 설정
────────────────────────────────────────────
회사 코드 → Slack 채널 ID 매핑.
채널 ID는 환경변수로 관리하며, 미설정 시 SLACK_CHANNEL_INTERNAL(준수 DM)로 fallback.
"""

import os

_INTERNAL = os.getenv("SLACK_CHANNEL_INTERNAL", "U0AKJE4UXV3")

SLACK_CHANNELS: dict[str, str] = {
    "WB0001": os.getenv("SLACK_CHANNEL_WB0001", _INTERNAL),
    "WB0002": os.getenv("SLACK_CHANNEL_WB0002", _INTERNAL),
    "WB0003": os.getenv("SLACK_CHANNEL_WB0003", _INTERNAL),
}


def get_channel(company_code: str) -> str:
    """회사 코드 → Slack 채널 ID. 미등록 회사는 INTERNAL로 fallback."""
    return SLACK_CHANNELS.get(company_code, _INTERNAL)


def all_company_codes() -> list[str]:
    """등록된 회사 코드 목록."""
    return list(SLACK_CHANNELS.keys())
