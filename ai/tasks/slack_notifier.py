"""
Slack 진척도 리포트 전송 모듈
────────────────────────────────────────────
team_config.json 기반으로 수습사원별 진척도를 생성하고
해당 리더(사수)의 Slack DM으로 전송합니다.
"""

import json
import logging
from pathlib import Path
from typing import Optional

from chains.summary_chain import run_summary_chain
from core.slack_client import get_slack_client
from memory.chat_history import get_chat_history

logger = logging.getLogger(__name__)

_TEAM_CONFIG_PATH = Path(__file__).parent.parent / "data" / "team_config.json"

# 통계용 키워드 (leader.py와 동일)
_KEYWORDS = [
    "연차", "휴가", "IT", "장비", "계정", "경비", "급여", "복리후생",
    "사무용품", "계약", "규정", "교육", "온보딩", "시스템", "AI",
]


def _load_teams() -> list:
    if not _TEAM_CONFIG_PATH.exists():
        logger.warning("team_config.json 파일이 없습니다: %s", _TEAM_CONFIG_PATH)
        return []
    return json.loads(_TEAM_CONFIG_PATH.read_text(encoding="utf-8")).get("teams", [])


def _build_slack_blocks(name: str, user_id: str,
                         question_count: int, topics: list, report: str) -> list:
    """Slack Block Kit 메시지 구성"""
    topic_str = ", ".join(topics) if topics else "해당 없음"
    activity = "🟢 활발" if question_count >= 5 else ("🟡 보통" if question_count >= 2 else "🔴 저조")

    return [
        {
            "type": "header",
            "text": {
                "type": "plain_text",
                "text": "📊 수습사원 온보딩 진척도 리포트",
                "emoji": True,
            },
        },
        {
            "type": "section",
            "fields": [
                {"type": "mrkdwn", "text": f"*이름:* {name}"},
                {"type": "mrkdwn", "text": f"*사번:* {user_id}"},
                {"type": "mrkdwn", "text": f"*총 질문 수:* {question_count}건"},
                {"type": "mrkdwn", "text": f"*활동 상태:* {activity}"},
                {"type": "mrkdwn", "text": f"*주요 토픽:* {topic_str}"},
            ],
        },
        {"type": "divider"},
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": f"*🤖 AI 분석 리포트*\n{report}",
            },
        },
        {
            "type": "context",
            "elements": [
                {
                    "type": "mrkdwn",
                    "text": "With Buddy가 자동으로 생성한 리포트입니다.",
                }
            ],
        },
    ]


def _send_to_leader(leader_slack_id: str, name: str, user_id: str,
                    notify_channel: Optional[str] = None) -> None:
    """특정 수습사원의 리포트를 생성하고 리더 Slack으로 전송"""
    # 대화 통계 계산
    messages = get_chat_history(user_id)
    human_msgs = [m.content for m in messages if m.type == "human"]
    all_text = " ".join(human_msgs)
    topics = [kw for kw in _KEYWORDS if kw in all_text]
    question_count = len(human_msgs)

    # AI 리포트 생성 (대화가 없으면 간략 메시지)
    if question_count == 0:
        report = "아직 AI 에이전트 사용 기록이 없습니다. 수습사원에게 온보딩 질문을 유도해보세요."
    else:
        report = run_summary_chain(user_id)

    # Slack 메시지 전송
    # notify_channel(채널 ID)이 있으면 채널에 @멘션 → iOS 푸시 알림 정상 작동
    # 없으면 DM으로 폴백 (iOS App DM은 푸시 안 올 수 있음)
    channel = notify_channel if notify_channel else leader_slack_id
    mention = f"<@{leader_slack_id}> " if notify_channel else f"<@{leader_slack_id}> "
    blocks = _build_slack_blocks(name, user_id, question_count, topics, report)
    get_slack_client().chat_postMessage(
        channel=channel,
        blocks=blocks,
        text=f"{mention}[온보딩 리포트] {name}({user_id}) 진척도 알림",
    )
    logger.info("슬랙 리포트 전송 완료: %s(%s) → %s", name, user_id, leader_slack_id)


async def notify_unanswered_question(user_id: str, question: str, qid: str, user_name: str = "", company_code: str = "") -> None:
    """AI가 답변하지 못한 질문을 Slack 알림 — internal은 전체, company는 해당 회사만"""
    teams = _load_teams()
    if not teams:
        return
    try:
        client = get_slack_client()
    except ValueError as e:
        logger.error("Slack 클라이언트 초기화 실패: %s", e)
        return

    blocks = [
        {"type": "header", "text": {"type": "plain_text", "text": "❓ 미답변 질문 알림", "emoji": True}},
        {"type": "section", "fields": [
            {"type": "mrkdwn", "text": f"*이름:* {user_name or '알 수 없음'}  |  *ID:* {user_id}"},
            {"type": "mrkdwn", "text": f"*질문 ID:* `{qid}`"},
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

    notified: set = set()
    for team in teams:
        team_type = team.get("type", "internal")
        team_cc = team.get("company_code", "")
        # internal: 전체 수신 / company: 해당 company_code 질문만 수신
        if team_type == "company" and team_cc != company_code:
            continue
        leader_id = team.get("leader_slack_id", "")
        channel   = team.get("notify_channel") or leader_id
        if not channel or channel in notified:
            continue
        notified.add(channel)
        try:
            client.chat_postMessage(
                channel=channel,
                blocks=blocks,
                text=f"❓ {user_name or user_id}의 질문: {question}",
            )
        except Exception as e:
            logger.error("미답변 알림 전송 실패 → %s: %s", channel, e)


async def send_no_result_summary(company_code: str, summary: str, actions: list, question_count: int) -> None:
    """no_result 질문 요약 결과를 Slack 채널에 발송"""
    teams = _load_teams()
    if not teams:
        return
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

    notified: set = set()
    for team in teams:
        team_type = team.get("type", "internal")
        team_cc = team.get("company_code", "")
        # internal: 전체 요약 수신 / company: 해당 회사 요약만 수신
        if team_type == "company" and team_cc != company_code:
            continue
        leader_id = team.get("leader_slack_id", "")
        channel = team.get("notify_channel") or leader_id
        if not channel or channel in notified:
            continue
        notified.add(channel)
        try:
            client.chat_postMessage(
                channel=channel,
                blocks=blocks,
                text=f"📊 미답변 질문 요약 ({company_code}) — {question_count}개 질문 분석 완료",
            )
            logger.info("no_result 요약 발송 완료 → %s", channel)
        except Exception as e:
            logger.error("no_result 요약 발송 실패 → %s: %s", channel, e)


_MT_LABEL = {
    "rag":        "✅ 문서 답변",
    "no_result":  "❓ 미답변",
    "out_of_scope": "🚫 범위 외",
    "chitchat":   "💬 일상 대화",
    "sensitive":  "⚠️ 민감 질문",
}


async def send_analytics_report(company_code: str, day: Optional[str] = None) -> None:
    """일별 AI 응답 통계 + no_result 요약을 Slack에 발송"""
    from memory.analytics_store import get_stats
    from routers.knowledge import _run_summary
    import asyncio as _aio

    stats = get_stats(company_code, day)
    total = stats["total"]
    avg_ms = stats["avg_latency_ms"]
    by_type: dict = stats["by_type"]
    no_result_qs: list = stats["no_result_questions"]

    teams = _load_teams()
    if not teams:
        return
    try:
        client = get_slack_client()
    except ValueError as e:
        logger.error("Slack 클라이언트 초기화 실패: %s", e)
        return

    # 응답 유형 텍스트
    type_lines = []
    for mt, cnt in sorted(by_type.items(), key=lambda x: -x[1]):
        pct = int(cnt / total * 100) if total else 0
        label = _MT_LABEL.get(mt, mt)
        type_lines.append(f"{label}  {cnt}건({pct}%)")
    type_text = "\n".join(type_lines) if type_lines else "데이터 없음"

    avg_sec = f"{avg_ms / 1000:.1f}초" if avg_ms else "-"

    blocks = [
        {"type": "header", "text": {"type": "plain_text", "text": "📊 오늘의 AI 응답 리포트", "emoji": True}},
        {"type": "section", "fields": [
            {"type": "mrkdwn", "text": f"*총 질문*\n{total}건"},
            {"type": "mrkdwn", "text": f"*평균 응답시간*\n{avg_sec}"},
        ]},
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*응답 유형*\n{type_text}"}},
    ]

    # no_result 질문이 있으면 AI 요약 추가
    if no_result_qs:
        blocks.append({"type": "divider"})
        q_text = "\n".join(f"• {q}" for q in no_result_qs)
        blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*📋 문서 보강 후보*\n{q_text}"}})
        try:
            summary, actions = await _aio.to_thread(_run_summary, no_result_qs)
            blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*🤖 AI 분석*\n{summary}"}})
            if actions:
                action_text = "\n".join(f"{i+1}. {a}" for i, a in enumerate(actions))
                blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*추천 액션*\n{action_text}"}})
        except Exception as e:
            logger.error("no_result 요약 실패: %s", e)

    blocks.append({"type": "context", "elements": [
        {"type": "mrkdwn", "text": "With Buddy AI · 자동 생성"}
    ]})

    notified: set = set()
    for team in teams:
        team_type = team.get("type", "internal")
        team_cc = team.get("company_code", "")
        if team_type == "company" and team_cc != company_code:
            continue
        leader_id = team.get("leader_slack_id", "")
        channel = team.get("notify_channel") or leader_id
        if not channel or channel in notified:
            continue
        notified.add(channel)
        try:
            client.chat_postMessage(
                channel=channel,
                blocks=blocks,
                text=f"📊 AI 응답 리포트 ({company_code}) — 총 {total}건",
            )
            logger.info("analytics 리포트 발송 완료 → %s", channel)
        except Exception as e:
            logger.error("analytics 리포트 발송 실패 → %s: %s", channel, e)


async def send_all_reports() -> None:
    """전체 팀의 수습사원 리포트를 각 리더에게 전송 (스케줄러 호출용)"""
    teams = _load_teams()
    if not teams:
        return

    client_ok = True
    try:
        get_slack_client()
    except ValueError as e:
        logger.error("Slack 클라이언트 초기화 실패: %s", e)
        client_ok = False

    if not client_ok:
        return

    for team in teams:
        leader_name = team.get("leader_name", "리더")
        leader_slack_id = team.get("leader_slack_id", "")
        trainees = team.get("trainees") or []

        if not leader_slack_id:
            logger.warning("%s의 leader_slack_id가 설정되지 않았습니다.", leader_name)
            continue

        notify_channel = team.get("notify_channel", "")
        for trainee in trainees:
            user_id = str(trainee.get("user_id", ""))
            name = trainee.get("name", user_id)
            try:
                _send_to_leader(leader_slack_id, name, user_id,
                                notify_channel=notify_channel or None)
            except Exception as e:
                logger.error("전송 실패 [%s → %s]: %s", name, leader_slack_id, e)


async def send_mybuddy_checkin_all() -> None:
    """
    매일 오전 9시 — 수습사원별 오늘 미완료 태스크를 Slack DM으로 전송합니다.
    team_config.json의 trainee에 slack_id가 있는 경우에만 발송합니다.
    """
    from memory.task_store import get_today_pending

    teams = _load_teams()
    if not teams:
        return

    try:
        client = get_slack_client()
    except ValueError as e:
        logger.error("Slack 클라이언트 초기화 실패: %s", e)
        return

    for team in teams:
        for trainee in (team.get("trainees") or []):
            user_id = str(trainee.get("user_id", ""))
            name = trainee.get("name", user_id)
            trainee_slack_id = trainee.get("slack_id", "")

            if not trainee_slack_id:
                continue  # trainee slack_id 없으면 스킵

            pending = get_today_pending(user_id)
            if not pending:
                continue  # 오늘 태스크 없으면 스킵

            task_lines = "\n".join(f"• {t['title']}" for t in pending)
            blocks = [
                {
                    "type": "header",
                    "text": {"type": "plain_text", "text": "📋 My Buddy 오늘의 체크인", "emoji": True},
                },
                {
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": (
                            f"안녕하세요 {name}님! 👋\n"
                            f"오늘 완료해야 할 항목이 *{len(pending)}개* 있어요.\n\n"
                            f"{task_lines}\n\n"
                            f"With Buddy 웹에서 완료 체크해주세요! ✅"
                        ),
                    },
                },
            ]
            try:
                client.chat_postMessage(
                    channel=trainee_slack_id,
                    blocks=blocks,
                    text=f"[My Buddy] 오늘 할 일 {len(pending)}개가 있어요!",
                )
                logger.info("My Buddy 체크인 전송: %s(%s)", name, trainee_slack_id)
            except Exception as e:
                logger.error("My Buddy 체크인 전송 실패 [%s]: %s", name, e)


async def send_single_report(user_id: str) -> None:
    """특정 수습사원의 리포트만 해당 리더에게 전송 (수동 트리거용)"""
    teams = _load_teams()
    for team in teams:
        for trainee in (team.get("trainees") or []):
            if str(trainee.get("user_id")) == str(user_id):
                _send_to_leader(
                    leader_slack_id=team["leader_slack_id"],
                    name=trainee.get("name", user_id),
                    user_id=user_id,
                    notify_channel=team.get("notify_channel") or None,
                )
                return
    raise ValueError(f"user_id={user_id}에 해당하는 팀 설정을 찾을 수 없습니다.")
