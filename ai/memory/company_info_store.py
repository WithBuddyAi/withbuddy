"""
회사 커스텀 정보 저장소
────────────────────────────────────────────
관리자가 편집 가능한 사내 정보를 관리합니다.
data/company_info.json 에 저장됩니다.
"""

import json
from pathlib import Path

_PATH = Path(__file__).parent.parent / "data" / "company_info.json"

_DEFAULTS: dict = {
    "lunch_time": "12:00 - 13:00",
    "payday": "매월 25일",
    "vacation_policy": "연간 15일 (입사 1년 미만: 월 1일 부여)",
    "welfare": "",
    "work_hours": "09:00 - 18:00 (점심 1시간 포함)",
    "dress_code": "자유 (비즈니스 캐주얼 권장)",
    "tools": [],
    "office_address": "",
    "custom_rules": {},
}


def get_company_info() -> dict:
    """회사 정보 반환. 파일 없으면 기본값 반환."""
    if _PATH.exists():
        try:
            return json.loads(_PATH.read_text(encoding="utf-8"))
        except Exception:
            pass
    return dict(_DEFAULTS)


def save_company_info(updates: dict) -> dict:
    """회사 정보 업데이트 (기존 필드 유지)."""
    info = get_company_info()
    info.update(updates)
    _PATH.parent.mkdir(parents=True, exist_ok=True)
    _PATH.write_text(json.dumps(info, ensure_ascii=False, indent=2), encoding="utf-8")
    return info


def format_company_info_context(info: dict) -> str:
    """
    회사 정보 dict를 LLM 컨텍스트 문자열로 변환.
    사내 정보 질문 응답 시 컨텍스트로 주입합니다.
    """
    if not info:
        return ""
    parts = []
    if info.get("lunch_time"):
        parts.append(f"점심시간: {info['lunch_time']}")
    if info.get("payday"):
        parts.append(f"급여일: {info['payday']}")
    if info.get("vacation_policy"):
        parts.append(f"연차 정책: {info['vacation_policy']}")
    if info.get("welfare"):
        parts.append(f"복지혜택: {info['welfare']}")
    if info.get("work_hours"):
        parts.append(f"근무시간: {info['work_hours']}")
    if info.get("dress_code"):
        parts.append(f"복장 규정: {info['dress_code']}")
    if info.get("tools"):
        tools = info["tools"]
        if isinstance(tools, list):
            tools = ", ".join(tools)
        parts.append(f"주요 업무 툴: {tools}")
    if info.get("office_address"):
        parts.append(f"사무실 주소: {info['office_address']}")
    for k, v in (info.get("custom_rules") or {}).items():
        parts.append(f"{k}: {v}")
    return "\n".join(parts)
