"""
입사 전 안내 에이전트
────────────────────────────────────────────
- 개인화된 환영 레터 자동 생성
- 팀 소개 카드 반환
- 사용자 메시지 의도에 따라 적절한 기능 실행
"""

import json
from pathlib import Path

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from core.llm import get_llm
from memory.company_info_store import format_company_info_context

_TEAM_CONFIG_PATH = Path(__file__).parent.parent / "data" / "team_config.json"

# ── 환영 레터 프롬프트 ────────────────────────────────────────

_WELCOME_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 수습사원을 따뜻하게 맞이하는 온보딩 담당자입니다.
입사 예정자를 위한 따뜻하고 설레는 환영 레터를 마크다운 형식으로 작성하세요.

[수습사원 정보]
{profile_context}

[회사 정보]
{company_info_context}

[레터 구성 — 반드시 아래 순서로]
1. 따뜻한 환영 인사 (이름이 있으면 포함)
2. 회사 소개 (긍정적이고 설레도록, 함께라는 느낌 강조)
3. 첫 날 안내 (근무시간, 점심시간, 준비물)
4. 주요 업무 툴 사전 안내 (있는 경우)
5. 팀원들의 응원 메시지
6. 마무리 응원

이모지를 자연스럽게 활용하여 따뜻한 분위기를 만드세요."""),
    ("human", "위 정보를 바탕으로 환영 레터를 작성해주세요."),
])

_welcome_chain = None


def _get_welcome_chain():
    global _welcome_chain
    if _welcome_chain is None:
        _welcome_chain = _WELCOME_PROMPT | get_llm() | StrOutputParser()
    return _welcome_chain


def _load_team_config() -> dict:
    if _TEAM_CONFIG_PATH.exists():
        try:
            return json.loads(_TEAM_CONFIG_PATH.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {"teams": []}


def generate_welcome_letter(profile: dict, company_info: dict) -> str:
    """개인화된 환영 레터 생성."""
    from memory.profile_store import format_profile_context
    profile_ctx = format_profile_context(profile) or "이름 미입력"
    company_ctx = format_company_info_context(company_info) or "회사 정보 미입력"
    return _get_welcome_chain().invoke({
        "profile_context": profile_ctx,
        "company_info_context": company_ctx,
    })


def get_team_cards() -> list:
    """
    팀 소개 카드 목록 반환.
    team_config.json의 teams 배열에서 팀장 + 멤버 카드를 생성합니다.
    members 필드가 있으면 우선 사용, 없으면 trainees 사용.
    """
    config = _load_team_config()
    cards = []
    for team in config.get("teams", []):
        team_name = team.get("team_name", "팀")
        if team.get("leader_name"):
            cards.append({
                "name": team["leader_name"],
                "is_leader": True,
                "department": team.get("leader_department", ""),
                "mbti": team.get("leader_mbti"),
                "photo_url": team.get("leader_photo_url"),
                "favorite_restaurant": team.get("leader_restaurant"),
                "intro": team.get("leader_intro", f"{team_name} 팀장입니다. 잘 부탁드립니다!"),
                "team": team_name,
            })
        members = team.get("members") or team.get("trainees", [])
        for m in members:
            cards.append({
                "name": m.get("name", ""),
                "is_leader": False,
                "department": m.get("department") or m.get("role", ""),
                "mbti": m.get("mbti"),
                "photo_url": m.get("photo_url"),
                "favorite_restaurant": m.get("favorite_restaurant"),
                "intro": m.get("intro", "함께 잘 부탁드립니다! 😊"),
                "team": team_name,
            })
    return cards


def run_preboarding_agent(message: str, profile: dict, company_info: dict) -> tuple:
    """
    입사 전 안내 에이전트 실행.
    메시지 키워드로 환영 레터 vs 팀 소개 카드 분기.

    Returns:
        (answer_text, metadata_dict)
    """
    msg_lower = message.lower()
    team_keywords = ["팀", "소개", "카드", "멤버", "구성원", "팀원", "동료"]

    if any(kw in msg_lower for kw in team_keywords):
        cards = get_team_cards()
        answer = "팀 소개 카드를 준비했어요! 함께할 팀원들을 미리 만나보세요 😊"
        return answer, {"type": "team_cards", "cards": cards}

    letter = generate_welcome_letter(profile, company_info)
    return letter, {"type": "welcome_letter"}
