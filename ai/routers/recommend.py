"""
담당자 추천 API 라우터
────────────────────────────────────────────
POST /recommend 엔드포인트를 처리합니다.
질문 내용을 분석하여 적합한 담당 부서와 담당자를 추천합니다.
LLM 응답에서 JSON을 파싱하여 구조화된 결과를 반환합니다.
"""

import json
import re

from fastapi import APIRouter, HTTPException
from langchain_core.output_parsers import StrOutputParser
from pydantic import BaseModel, Field

from chains.checklist_chain import run_checklist_chain
from core.llm import get_llm
from utils.prompts import RECOMMEND_PROMPT

_recommend_chains: dict = {}

_COMPANY_CONTACTS: dict[str, str] = {
    "WB0001": (
        "- 경영지원팀: 김지수 (인사, 연차/휴가, 급여, 복리후생, 근태, 채용, 총무, 경비, 사무용품, 법인카드, 계약, 사내 규정, 온보딩)\n"
        "- IT담당: 박민준 (IT 장비, 계정 세팅, MFA, VPN, 소프트웨어 설치, IT 장애 대응)"
    ),
    "WB0002": (
        "- 운영팀(HR): 김현아 (입퇴사, 인사, 수습, 연차/휴가, 급여, 복리후생, 온보딩)\n"
        "- 운영팀(IT): 박소연 (계정, 장비, 권한, Slack, Notion, Adobe, IT 문의)\n"
        "- 크리에이티브팀: 박서준 (Figma, Canva, 디자인 툴, 크리에이티브 업무)\n"
        "- 퍼포먼스마케팅팀: 이도윤 (Meta Ads, Google Ads, GA4, 광고 계정)"
    ),
}

_COMPANY_DEFAULT_PERSON: dict[str, str] = {
    "WB0001": "김지수",
    "WB0002": "김현아",
}

_COMPANY_DEFAULT_CONTACT: dict[str, dict] = {
    "WB0001": {"department": "경영지원팀", "name": "김지수", "position": "", "connects": []},
    "WB0002": {"department": "운영팀(HR)", "name": "김현아", "position": "", "connects": []},
}

_COMPANY_CONTACTS_STRUCTURED: dict[str, list] = {
    "WB0001": [
        {"department": "경영지원팀", "name": "김지수", "position": "", "connects": []},
        {"department": "IT담당", "name": "박민준", "position": "", "connects": []},
    ],
    "WB0002": [
        {"department": "운영팀(HR)", "name": "김현아", "position": "", "connects": []},
        {"department": "운영팀(IT)", "name": "박소연", "position": "", "connects": []},
        {"department": "크리에이티브팀", "name": "박서준", "position": "", "connects": []},
        {"department": "퍼포먼스마케팅팀", "name": "이도윤", "position": "", "connects": []},
    ],
}


async def get_contact_for_question(company_code: str, message: str) -> dict:
    code = company_code or "WB0001"
    contacts = _COMPANY_CONTACTS_STRUCTURED.get(code, [])
    default = _COMPANY_DEFAULT_CONTACT.get(code, {"department": "담당 부서", "name": "담당자", "position": "", "connects": []})

    if not contacts:
        return default

    try:
        if code not in _recommend_chains:
            _recommend_chains[code] = RECOMMEND_PROMPT | get_llm() | StrOutputParser()
        contacts_info = _COMPANY_CONTACTS.get(code, _COMPANY_CONTACTS["WB0001"])
        raw = await _recommend_chains[code].ainvoke({"message": message, "contacts_info": contacts_info})
        parsed = _parse_recommendation(raw)
        person_name = parsed.get("person", "")
        for contact in contacts:
            if contact["name"] == person_name:
                return contact
    except Exception:
        pass

    return default

router = APIRouter(tags=["recommend"])


# ── 요청 / 응답 스키마 ──────────────────────

class RecommendRequest(BaseModel):
    user_id: int = Field(..., description="사용자 고유 ID", example=1)
    message: str = Field(..., description="문의 내용", example="이거 누구한테 물어봐요?")
    company_code: str = Field("", description="회사 고유 ID")


class RecommendResponse(BaseModel):
    department: str = Field(..., description="추천 담당 부서")
    person: str = Field(..., description="추천 담당자 이름")
    reason: str = Field(default="", description="추천 이유")


class ChecklistRequest(BaseModel):
    department: str = Field(..., description="부서명", example="개발팀")


class ChecklistResponse(BaseModel):
    checklist: str = Field(..., description="온보딩 체크리스트 (마크다운)")


# ── 헬퍼: LLM 응답에서 JSON 추출 ───────────

def _parse_recommendation(raw: str) -> dict:
    """
    LLM 응답 텍스트에서 JSON 객체를 추출합니다.
    마크다운 코드블록이 포함된 경우도 처리합니다.

    Args:
        raw: LLM이 생성한 원본 텍스트

    Returns:
        dict: {"department": ..., "person": ...} 형태의 딕셔너리
              파싱 실패 시 인사팀 기본값 반환
    """
    # 중괄호로 감싸진 JSON 블록 추출 (멀티라인 포함)
    json_match = re.search(r'\{[^{}]+\}', raw, re.DOTALL)
    if json_match:
        try:
            return json.loads(json_match.group())
        except json.JSONDecodeError:
            pass

    # 파싱 실패 → 기본값 반환
    return {"department": "담당 부서", "person": "담당자"}


# ── 엔드포인트 ──────────────────────────────

@router.post("/recommend", response_model=RecommendResponse)
async def recommend_person(request: RecommendRequest):
    """
    담당자 추천

    - 질문 내용을 분석하여 가장 적합한 부서와 담당자를 추천합니다.
    - 인사, IT, 총무, 재무, 법무팀 담당자 정보를 기반으로 판단합니다.
    """
    try:
        global _recommend_chains
        code = request.company_code or "WB0001"
        if code not in _recommend_chains:
            _recommend_chains[code] = RECOMMEND_PROMPT | get_llm() | StrOutputParser()

        contacts_info = _COMPANY_CONTACTS.get(code, _COMPANY_CONTACTS["WB0001"])
        default_person = _COMPANY_DEFAULT_PERSON.get(code, "담당자")

        raw_result = _recommend_chains[code].invoke({
            "message": request.message,
            "contacts_info": contacts_info,
        })

        # JSON 파싱으로 부서/담당자 추출
        parsed = _parse_recommendation(raw_result)

        return RecommendResponse(
            department=parsed.get("department", "담당 부서"),
            person=parsed.get("person", default_person),
            reason=parsed.get("reason", ""),
        )

    except ValueError as e:
        raise HTTPException(status_code=500, detail=f"설정 오류: {str(e)}")
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"담당자 추천 중 오류가 발생했습니다: {str(e)}",
        )


@router.post("/recommend/checklist", response_model=ChecklistResponse)
async def generate_checklist(request: ChecklistRequest):
    """
    부서별 온보딩 체크리스트 생성

    - 입력된 부서명에 맞는 수습사원 온보딩 체크리스트를 생성합니다.
    """
    try:
        checklist = run_checklist_chain(request.department)
        return ChecklistResponse(checklist=checklist)
    except ValueError as e:
        raise HTTPException(status_code=500, detail=f"설정 오류: {str(e)}")
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"체크리스트 생성 중 오류가 발생했습니다: {str(e)}",
        )
