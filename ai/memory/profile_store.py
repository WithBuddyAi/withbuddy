"""
사용자 프로필 저장소
────────────────────────────────────────────
사용자가 직접 입력한 개인 맞춤 설정을 관리합니다.
data/user_profiles/{user_id}.json 에 저장됩니다.
"""

import json
from pathlib import Path

_PROFILES_DIR = Path(__file__).parent.parent / "data" / "user_profiles"
_PROFILES_DIR.mkdir(parents=True, exist_ok=True)
_TEAM_CONFIG_PATH = Path(__file__).parent.parent / "data" / "team_config.json"


def _find_leader_by_department(department: str) -> str:
    """부서명으로 team_config에서 팀장 이름을 찾아 반환합니다."""
    try:
        teams = json.loads(_TEAM_CONFIG_PATH.read_text(encoding="utf-8")).get("teams", [])
        dept_lower = department.strip().lower()
        for team in teams:
            if dept_lower in team.get("team_name", "").lower() or dept_lower in team.get("leader_department", "").lower():
                return team.get("leader_name", "")
    except Exception:
        pass
    return ""


def _path(user_id: str) -> Path:
    return _PROFILES_DIR / f"{user_id}.json"


def get_profile(user_id: str) -> dict:
    """사용자 프로필 반환. 없으면 빈 dict."""
    p = _path(user_id)
    if p.exists():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def save_profile(user_id: str, updates: dict) -> dict:
    """프로필 저장 (기존 필드 유지하며 업데이트)."""
    existing = get_profile(user_id)
    existing.update(updates)
    _path(user_id).write_text(
        json.dumps(existing, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return existing


def delete_profile(user_id: str) -> bool:
    """프로필 삭제. 성공 여부 반환."""
    p = _path(user_id)
    if p.exists():
        p.unlink()
        return True
    return False


def format_profile_context(profile: dict) -> str:
    """
    프로필 dict를 LLM 컨텍스트 문자열로 변환.
    RAG 프롬프트에 주입하여 개인화된 응답을 유도합니다.
    """
    if not profile:
        return ""
    parts = []
    if profile.get("department"):
        dept = profile["department"]
        parts.append(f"부서: {dept}")
        leader = _find_leader_by_department(dept)
        if leader:
            parts.append(f"팀장: {leader} 님")
    if profile.get("job_role"):
        parts.append(f"직무: {profile['job_role']}")
    if profile.get("interests"):
        interests = profile["interests"]
        if isinstance(interests, list):
            interests = ", ".join(interests)
        parts.append(f"관심사: {interests}")
    if profile.get("comm_style"):
        parts.append(f"선호 커뮤니케이션 방식: {profile['comm_style']}")
    if profile.get("start_date"):
        parts.append(f"입사일: {profile['start_date']}")
    if profile.get("mbti"):
        parts.append(f"MBTI: {profile['mbti']}")
    if profile.get("notes"):
        parts.append(f"기억할 사항: {profile['notes']}")
    return "\n".join(parts)
