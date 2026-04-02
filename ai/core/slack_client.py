"""
Slack Web API 클라이언트 모듈
────────────────────────────────────────────
slack_sdk의 WebClient를 싱글톤으로 관리합니다.
SLACK_BOT_TOKEN 환경변수가 필요합니다.
"""

import os
from functools import lru_cache

from slack_sdk import WebClient


@lru_cache(maxsize=1)
def get_slack_client() -> WebClient:
    """
    Slack WebClient 싱글톤 반환

    Raises:
        ValueError: SLACK_BOT_TOKEN 환경변수 미설정 시
    """
    token = os.getenv("SLACK_BOT_TOKEN")
    if not token:
        raise ValueError(
            "SLACK_BOT_TOKEN 환경변수가 설정되지 않았습니다. "
            ".env 파일을 확인해주세요."
        )
    return WebClient(token=token)
