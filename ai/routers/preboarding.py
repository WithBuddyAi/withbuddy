"""
입사 전 안내 API 라우터
────────────────────────────────────────────
POST /preboarding/welcome-letter/{user_id} : 개인화 환영 레터 생성
GET  /preboarding/team-cards               : 팀 소개 카드 목록
POST /preboarding/communication/improve    : 메시지 말투 개선 + 채널 추천
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from agents.communication_agent import run_communication_agent
from agents.preboarding_agent import generate_welcome_letter, get_team_cards
from memory.company_info_store import get_company_info
from memory.profile_store import get_profile

router = APIRouter(prefix="/preboarding", tags=["preboarding"])


class CommunicationRequest(BaseModel):
    user_id: str = Field(..., description="사용자 ID (프로필 개인화에 활용)")
    message: str = Field(..., description="개선이 필요한 원본 메시지")


@router.post("/welcome-letter/{user_id}")
async def create_welcome_letter(user_id: str):
    """
    개인화된 환영 레터 생성.
    사용자 프로필과 회사 정보를 기반으로 맞춤형 레터를 작성합니다.
    """
    try:
        profile = get_profile(user_id)
        company_info = get_company_info()
        letter = generate_welcome_letter(profile, company_info)
        return {"user_id": user_id, "letter": letter}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/team-cards")
async def list_team_cards():
    """
    팀 소개 카드 목록 반환.
    team_config.json에서 팀장 + 멤버 카드를 생성합니다.
    """
    try:
        cards = get_team_cards()
        return {"cards": cards, "total": len(cards)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/communication/improve")
async def improve_communication(req: CommunicationRequest):
    """
    메시지 말투 개선 + 커뮤니케이션 채널 추천.
    개인 프로필을 반영한 개인화된 커뮤니케이션 코칭을 제공합니다.
    """
    try:
        from memory.profile_store import format_profile_context
        profile = get_profile(req.user_id)
        profile_context = format_profile_context(profile)
        result = run_communication_agent(req.message, profile_context)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
