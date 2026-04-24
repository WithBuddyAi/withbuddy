"""
미답변 질문 슬랙 리포트 스크립트
────────────────────────────────────────────
unanswered.json에 쌓인 pending 질문들을 분석해서
담당자별로 분류 후 슬랙으로 알림을 보냅니다.

실행:
    cd ai && python scripts/unanswered_report.py

주기 실행 (서버 cron):
    0 9 * * 1 cd /app/ai && python scripts/unanswered_report.py
"""

import json
import os
import sys
from pathlib import Path

# ai/ 루트를 경로에 추가
sys.path.insert(0, str(Path(__file__).parent.parent))

from dotenv import load_dotenv
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

load_dotenv()

from core.llm import get_llm
from memory.unanswered_store import get_pending

# ── 회사별 담당자 슬랙 ID ─────────────────────────────────────
# 실제 슬랙 멤버 ID로 교체 필요 (@mention용)
_SLACK_IDS: dict[str, dict] = {
    "WB0001": {
        "경영지원팀": {"name": "김지수", "slack_id": ""},   # 실제 ID 입력
        "IT담당": {"name": "박민준", "slack_id": ""},
    },
    "WB0002": {
        "운영팀(HR)": {"name": "김현아", "slack_id": ""},
        "운영팀(IT)": {"name": "박소연", "slack_id": ""},
        "크리에이티브팀": {"name": "박서준", "slack_id": ""},
        "퍼포먼스마케팅팀": {"name": "이도윤", "slack_id": ""},
    },
}

_SLACK_CHANNEL = os.getenv("SLACK_REPORT_CHANNEL", "#ai-미답변-리포트")

# ── LLM 분류 프롬프트 ─────────────────────────────────────────
_CLASSIFY_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """다음은 AI가 답변하지 못한 질문 목록입니다.
각 질문을 아래 담당자 중 가장 적합한 곳으로 분류하세요.

담당자 목록:
{contacts}

결과를 JSON 배열로 반환하세요:
[{{"id": "질문ID", "question": "질문내용", "department": "부서명"}}, ...]

반드시 JSON만 반환, 설명 없이."""),
    ("human", "질문 목록:\n{questions}"),
])


def _classify_questions(questions: list, company_code: str) -> list:
    """질문 목록을 LLM으로 담당자별 분류."""
    contacts_info = "\n".join(
        f"- {dept}: {info['name']}"
        for dept, info in _SLACK_IDS.get(company_code, {}).items()
    )
    if not contacts_info:
        contacts_info = "- 경영지원팀: 담당자"

    questions_text = "\n".join(
        f"[{q['id']}] {q['question']}" for q in questions
    )

    chain = _CLASSIFY_PROMPT | get_llm() | StrOutputParser()
    raw = chain.invoke({"contacts": contacts_info, "questions": questions_text})

    try:
        # JSON 블록 추출
        import re
        match = re.search(r'\[.*\]', raw, re.DOTALL)
        if match:
            return json.loads(match.group())
    except Exception:
        pass

    # 파싱 실패 시 첫 번째 담당자로 전부 배정
    first_dept = next(iter(_SLACK_IDS.get(company_code, {"경영지원팀": {}})))
    return [{"id": q["id"], "question": q["question"], "department": first_dept} for q in questions]


def _send_slack_message(text: str):
    """슬랙 채널에 메시지 전송."""
    import urllib.request
    token = os.getenv("SLACK_BOT_TOKEN", "")
    if not token:
        print("[slack] SLACK_BOT_TOKEN 없음 — 콘솔 출력만 합니다.")
        print(text)
        return

    payload = json.dumps({
        "channel": _SLACK_CHANNEL,
        "text": text,
        "mrkdwn": True,
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://slack.com/api/chat.postMessage",
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    with urllib.request.urlopen(req) as res:
        result = json.loads(res.read())
        if not result.get("ok"):
            print(f"[slack] 전송 실패: {result.get('error')}")
        else:
            print(f"[slack] 전송 완료: {_SLACK_CHANNEL}")


def run_report(company_code: str = "WB0001"):
    """미답변 질문 리포트 생성 및 슬랙 전송."""
    pending = get_pending()
    # company_code 필터 (user_id 기반으로 저장돼 있어 전체 공통 처리)
    if not pending:
        print("미답변 질문 없음.")
        return

    print(f"미답변 질문 {len(pending)}개 분류 중...")
    classified = _classify_questions(pending, company_code)

    # 담당자별 그룹핑
    groups: dict[str, list] = {}
    for item in classified:
        dept = item.get("department", "기타")
        groups.setdefault(dept, []).append(item["question"])

    # 슬랙 메시지 조립
    lines = [f"*📋 With Buddy 미답변 질문 리포트* — {len(pending)}건\n"]
    contacts = _SLACK_IDS.get(company_code, {})

    for dept, questions in groups.items():
        info = contacts.get(dept, {})
        name = info.get("name", dept)
        slack_id = info.get("slack_id", "")
        mention = f"<@{slack_id}>" if slack_id else f"*{name}*"

        lines.append(f"{mention} ({dept}) — {len(questions)}건")
        for q in questions:
            lines.append(f"  • {q}")
        lines.append("")

    lines.append(f"_답변 입력: https://ai.itsdev.kr/docs#/knowledge/save_answer_knowledge_answer_post_")

    message = "\n".join(lines)
    _send_slack_message(message)


if __name__ == "__main__":
    company_code = sys.argv[1] if len(sys.argv) > 1 else "WB0001"
    run_report(company_code)
