"""
개인 맞춤 설정 API 라우터
────────────────────────────────────────────
GET  /profile/{user_id}  : 사용자 프로필 조회
POST /profile/{user_id}  : 프로필 저장/업데이트
DELETE /profile/{user_id}: 프로필 삭제
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List

from memory.profile_store import get_profile, save_profile, delete_profile

router = APIRouter(prefix="/profile", tags=["profile"])


class ProfileRequest(BaseModel):
    name: Optional[str] = Field(None, description="이름")
    department: Optional[str] = Field(None, description="부서")
    job_role: Optional[str] = Field(None, description="직무")
    interests: Optional[List[str]] = Field(None, description="관심사 목록")
    comm_style: Optional[str] = Field(None, description="선호 커뮤니케이션 방식 (예: 메신저 선호, 메일 선호)")
    start_date: Optional[str] = Field(None, description="입사일 (YYYY-MM-DD)")
    mbti: Optional[str] = Field(None, description="MBTI")
    notes: Optional[str] = Field(None, description="기억해야 할 개인 메모")
    photo_url: Optional[str] = Field(None, description="프로필 사진 URL 또는 data URL")
    favorite_restaurant: Optional[str] = Field(None, description="좋아하는 맛집")
    intro: Optional[str] = Field(None, description="한 줄 소개")


@router.get("/{user_id}")
async def get_user_profile(user_id: str):
    """사용자 프로필 조회."""
    profile = get_profile(user_id)
    return {"user_id": user_id, "profile": profile}


@router.post("/{user_id}")
async def upsert_user_profile(user_id: str, req: ProfileRequest):
    """
    프로필 저장/업데이트.
    기존 필드는 유지하고 전달된 필드만 업데이트합니다.
    """
    try:
        updates = {k: v for k, v in req.model_dump().items() if v is not None}
        saved = save_profile(user_id, updates)
        return {"user_id": user_id, "profile": saved, "message": "프로필이 저장되었습니다."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/{user_id}")
async def remove_user_profile(user_id: str):
    """프로필 삭제."""
    deleted = delete_profile(user_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="프로필이 존재하지 않습니다.")
    return {"user_id": user_id, "message": "프로필이 삭제되었습니다."}
