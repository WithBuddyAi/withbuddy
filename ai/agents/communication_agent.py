"""
커뮤니케이션 보조 에이전트
────────────────────────────────────────────
수습사원의 메시지를 분석하여:
  1. 자연스럽고 정중한 문장으로 개선
  2. 질문 대상 추천 (인사팀, 팀장, 동료 등)
  3. 적절한 커뮤니케이션 채널 제안 (메일/메신저/대면)
  4. 질문 전 체크리스트 제공
"""

import json

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from core.llm import get_llm

_COMM_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 수습사원의 직장 내 커뮤니케이션을 도와주는 전문 코치입니다.

[사용자 정보]
{profile_context}

[이전 대화 맥락 — 주제가 불명확하면 여기서 파악하세요]
{chat_history}

위 대화 맥락을 반드시 참고하여, 수습사원이 하려는 말/질문을 파악한 뒤 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.

{{
  "improved_message": "개선된 메시지 (자연스럽고 정중한 표현으로, 실제 사용할 수 있는 완성된 문장)",
  "target": "문의 대상 (예: 팀장님, 인사팀, IT팀, 동료 등 구체적으로)",
  "channel": "추천 채널 (메일 / 메신저(Slack) / 대면 중 하나)",
  "reason": "이 채널을 추천하는 이유 (한 문장)",
  "checklist": ["질문 전 확인할 사항1", "확인할 사항2", "확인할 사항3"],
  "tone_tips": "말투나 타이밍 관련 추가 조언 (한 문장)"
}}

[채널 선택 기준]
- 메일: 공식적·기록이 필요한 요청 (연차신청, 계약서, 비용 정산, 공식 문의)
- 메신저(Slack): 간단한 확인, 빠른 회신 필요, 비공식적 질문, 가벼운 조율
- 대면: 민감한 주제 (급여 협상, 고충, 평가), 복잡한 설명 필요, 긴급한 사항"""),
    ("human", "{message}"),
])

_chain = None


def _get_chain():
    global _chain
    if _chain is None:
        _chain = _COMM_PROMPT | get_llm() | StrOutputParser()
    return _chain


def run_communication_agent(message: str, profile_context: str = "", chat_history: str = "") -> dict:
    """
    커뮤니케이션 보조 에이전트 실행.

    Args:
        message: 사용자가 전달하려는 원본 메시지
        profile_context: 개인화를 위한 사용자 프로필 컨텍스트
        chat_history: 이전 대화 내역 (맥락 파악용)

    Returns:
        dict: improved_message, target, channel, reason, checklist, tone_tips
    """
    raw = _get_chain().invoke({
        "message": message,
        "profile_context": profile_context or "정보 없음",
        "chat_history": chat_history or "이전 대화 없음",
    }).strip()

    # ```json ... ``` 블록 제거 후 파싱
    try:
        if "```" in raw:
            parts = raw.split("```")
            for part in parts:
                if part.startswith("json"):
                    part = part[4:]
                try:
                    return json.loads(part.strip())
                except json.JSONDecodeError:
                    continue
        return json.loads(raw)
    except json.JSONDecodeError:
        return {
            "improved_message": message,
            "target": "담당자",
            "channel": "메신저(Slack)",
            "reason": "간단한 확인 사항은 메신저로 빠르게 소통하는 것이 좋습니다.",
            "checklist": ["관련 자료를 먼저 확인하세요", "질문 내용을 간결하게 정리하세요"],
            "tone_tips": "정중하고 명확하게 전달하세요.",
        }
