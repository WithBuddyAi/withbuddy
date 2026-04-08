"""
리더(사수) API 라우터
────────────────────────────────────────────
사수가 담당 수습사원의 온보딩 진척도를 확인하고
리포트를 생성하는 엔드포인트를 제공합니다.
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from chains.summary_chain import run_summary_chain
from memory.chat_history import get_chat_history

router = APIRouter(tags=["leader"])


class StatsRequest(BaseModel):
    user_id: int = Field(..., description="수습사원 사번")


class StatsResponse(BaseModel):
    user_id: int
    question_count: int = Field(..., description="총 질문 수")
    recent_questions: list[str] = Field(..., description="최근 질문 목록 (최신순)")
    topics: list[str] = Field(..., description="주요 키워드 추출")


class LeaderReportRequest(BaseModel):
    user_id: int = Field(..., description="수습사원 사번")


class LeaderReportResponse(BaseModel):
    report: str


@router.post("/leader/stats", response_model=StatsResponse)
async def get_stats(request: StatsRequest):
    """수습사원의 대화 통계 반환"""
    try:
        history = get_chat_history(str(request.user_id))
        human_msgs = [m.content for m in history if m.type == "human"]

        # 간단한 키워드 추출 (자주 등장하는 명사 후보)
        keywords = ["연차", "휴가", "IT", "장비", "계정", "경비", "급여", "복리후생",
                    "사무용품", "계약", "규정", "교육", "온보딩", "시스템", "AI"]
        all_text = " ".join(human_msgs)
        found_topics = [kw for kw in keywords if kw in all_text]

        return StatsResponse(
            user_id=request.user_id,
            question_count=len(human_msgs),
            recent_questions=list(reversed(human_msgs))[:5],
            topics=found_topics,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/leader/report", response_model=LeaderReportResponse)
async def generate_report(request: LeaderReportRequest):
    """수습사원의 AI 진척도 리포트 생성"""
    try:
        report = run_summary_chain(str(request.user_id))
        return LeaderReportResponse(report=report)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
