"""
Slack 알림 모듈
────────────────────────────────────────────
미답변 질문 실시간 알림 / 미답변 질문 요약 리포트 발송.
채널 라우팅: core/slack_config.py (company_code → 채널 ID)
"""

import logging

from core.slack_client import get_slack_client
from core.slack_config import get_channel

logger = logging.getLogger(__name__)


async def notify_unanswered_question(user_id: str, question: str, qid: str, user_name: str = "", company_code: str = "") -> None:
    """AI가 답변하지 못한 질문을 Slack 알림 — company_code 기반 채널 라우팅"""
    try:
        client = get_slack_client()
    except ValueError as e:
        logger.error("Slack 클라이언트 초기화 실패: %s", e)
        return

    blocks = [
        {"type": "header", "text": {"type": "plain_text", "text": "❓ 미답변 질문 알림", "emoji": True}},
        {"type": "section", "fields": [
            {"type": "mrkdwn", "text": f"*이름:* {user_name or '알 수 없음'}"},
            {"type": "mrkdwn", "text": f"*회사:* {company_code or '-'}"},
        ]},
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*질문 내용:*\n> {question}"}},
        {"type": "divider"},
        {
            "type": "actions",
            "elements": [
                {
                    "type": "button",
                    "text": {"type": "plain_text", "text": "📝 Slack에서 바로 답변하기", "emoji": True},
                    "style": "primary",
                    "action_id": "open_answer_modal",
                    "value": qid,
                }
            ],
        },
        {"type": "context", "elements": [
            {"type": "mrkdwn", "text": "버튼을 누르면 Slack에서 바로 답변할 수 있어요. 답변은 즉시 AI 지식에 반영됩니다."}
        ]},
    ]

    channel = get_channel(company_code)
    try:
        client.chat_postMessage(
            channel=channel,
            blocks=blocks,
            text=f"❓ [{company_code}] {user_name or user_id}의 질문: {question}",
        )
    except Exception as e:
        logger.error("미답변 알림 전송 실패 → %s: %s", channel, e)


async def send_no_result_summary(company_code: str, summary: str, actions: list, question_count: int) -> None:
    """no_result 질문 요약 결과를 Slack 채널에 발송"""
    try:
        client = get_slack_client()
    except ValueError as e:
        logger.error("Slack 클라이언트 초기화 실패: %s", e)
        return

    action_text = "\n".join(f"{i+1}. {a}" for i, a in enumerate(actions))
    blocks = [
        {"type": "header", "text": {"type": "plain_text", "text": "📊 미답변 질문 요약 리포트", "emoji": True}},
        {"type": "section", "fields": [
            {"type": "mrkdwn", "text": f"*회사 코드:* {company_code}"},
            {"type": "mrkdwn", "text": f"*분석 질문 수:* {question_count}개"},
        ]},
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*요약*\n{summary}"}},
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*문서 보강 제안*\n{action_text}"}},
        {"type": "divider"},
        {"type": "context", "elements": [
            {"type": "mrkdwn", "text": "관리자 대시보드에서 문서를 보강해 주세요."}
        ]},
    ]

    channel = get_channel(company_code)
    try:
        client.chat_postMessage(
            channel=channel,
            blocks=blocks,
            text=f"📊 미답변 질문 요약 ({company_code}) — {question_count}개 질문 분석 완료",
        )
        logger.info("no_result 요약 발송 완료 → %s", channel)
    except Exception as e:
        logger.error("no_result 요약 발송 실패 → %s: %s", channel, e)




