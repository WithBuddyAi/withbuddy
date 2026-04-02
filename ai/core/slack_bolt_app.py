"""
Slack Bolt 비동기 앱 — 인터랙션(버튼·모달) 처리
────────────────────────────────────────────
미답변 질문 알림에서 "📝 Slack에서 바로 답변하기" 버튼을 클릭하면
모달이 열려 답변을 입력할 수 있습니다.
제출된 답변은 unanswered.json + ChromaDB에 즉시 저장됩니다.
"""

import logging
import os

from slack_bolt.async_app import AsyncApp

logger = logging.getLogger(__name__)

bolt_app = AsyncApp(
    token=os.environ.get("SLACK_BOT_TOKEN", ""),
    signing_secret=os.environ.get("SLACK_SIGNING_SECRET", "dummy"),  # Socket Mode에서는 미사용
)


# ── 버튼 클릭 → 모달 열기 ──────────────────────

@bolt_app.action("open_answer_modal")
async def handle_answer_button(ack, body, client):
    """미답변 알림의 '답변하기' 버튼 클릭 처리"""
    await ack()

    qid = body["actions"][0]["value"]

    # 질문 내용 조회
    from memory.unanswered_store import get_all
    questions = get_all()
    q = next((x for x in questions if x["id"] == qid), None)

    if not q:
        user_id = body["user"]["id"]
        await client.chat_postMessage(
            channel=user_id,
            text="❌ 이미 답변된 질문이거나 찾을 수 없습니다.",
        )
        return

    if q.get("status") == "answered":
        user_id = body["user"]["id"]
        await client.chat_postMessage(
            channel=user_id,
            text=f"✅ 이미 답변된 질문입니다.\n*답변:* {q.get('answer', '')}",
        )
        return

    await client.views_open(
        trigger_id=body["trigger_id"],
        view={
            "type": "modal",
            "callback_id": "submit_answer",
            "private_metadata": qid,
            "title": {"type": "plain_text", "text": "📝 답변하기", "emoji": True},
            "submit": {"type": "plain_text", "text": "저장하기", "emoji": True},
            "close":  {"type": "plain_text", "text": "취소"},
            "blocks": [
                {
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": f"*사번 {q['user_id']}의 질문:*\n\n> {q['question']}",
                    },
                },
                {"type": "divider"},
                {
                    "type": "input",
                    "block_id": "answer_block",
                    "label": {"type": "plain_text", "text": "답변 내용"},
                    "element": {
                        "type": "plain_text_input",
                        "action_id": "answer_input",
                        "multiline": True,
                        "min_length": 5,
                        "placeholder": {
                            "type": "plain_text",
                            "text": "수습사원에게 전달할 답변을 입력해주세요...",
                        },
                    },
                },
                {
                    "type": "context",
                    "elements": [
                        {
                            "type": "mrkdwn",
                            "text": "💡 저장된 답변은 즉시 AI 지식(ChromaDB)에 반영됩니다.",
                        }
                    ],
                },
            ],
        },
    )
    logger.info("답변 모달 열림: qid=%s", qid)


# ── 모달 제출 → 답변 저장 + ChromaDB 반영 ──────

@bolt_app.view("submit_answer")
async def handle_answer_submit(ack, body, client):
    """모달 제출 처리 — 답변 저장 + AI 지식 즉시 반영"""
    await ack()

    qid    = body["view"]["private_metadata"]
    answer = body["view"]["state"]["values"]["answer_block"]["answer_input"]["value"]
    user_id = body["user"]["id"]

    # 1) unanswered.json 업데이트
    from memory.unanswered_store import answer_question
    item = answer_question(qid, answer)

    if not item:
        await client.chat_postMessage(
            channel=user_id,
            text="❌ 답변 저장 실패: 질문을 찾을 수 없습니다.",
        )
        return

    question = item["question"]

    # 2) qa_knowledge.md + ChromaDB 즉시 반영
    try:
        from pathlib import Path
        from langchain_core.documents import Document
        from langchain_text_splitters import RecursiveCharacterTextSplitter
        from core.vectorstore import get_vectorstore

        qa_path = Path(__file__).parent.parent / "docs" / "qa_knowledge.md"
        qa_path.parent.mkdir(parents=True, exist_ok=True)
        if not qa_path.exists():
            qa_path.write_text(
                "# 사수 직접 답변 지식 모음\n\n"
                "이 파일은 AI가 답변하지 못한 질문에 대해 사수가 직접 입력한 답변을 저장합니다.\n",
                encoding="utf-8",
            )
        with open(qa_path, "a", encoding="utf-8") as f:
            f.write(f"\n\n## Q: {question}\n\n**A:** {answer}\n")

        doc = Document(
            page_content=f"질문: {question}\n답변: {answer}",
            metadata={"source": "qa_knowledge.md"},
        )
        chunks = RecursiveCharacterTextSplitter(
            chunk_size=500, chunk_overlap=50
        ).split_documents([doc])
        get_vectorstore().add_documents(chunks)

        logger.info("AI 지식 반영 완료: qid=%s", qid)
    except Exception as e:
        logger.error("ChromaDB 저장 실패: %s", e)

    # 3) 리더에게 완료 메시지
    await client.chat_postMessage(
        channel=user_id,
        blocks=[
            {
                "type": "header",
                "text": {"type": "plain_text", "text": "✅ 답변이 저장되었습니다!", "emoji": True},
            },
            {
                "type": "section",
                "fields": [
                    {"type": "mrkdwn", "text": f"*질문:*\n> {question}"},
                    {"type": "mrkdwn", "text": f"*답변:*\n{answer}"},
                ],
            },
            {
                "type": "context",
                "elements": [
                    {"type": "mrkdwn", "text": "💡 이 답변은 AI 지식에 즉시 반영되었습니다. 다음부터는 AI가 자동으로 답변합니다."}
                ],
            },
        ],
        text=f"✅ 답변 저장 완료: {question}",
    )
    logger.info("답변 저장 완료: qid=%s", qid)
