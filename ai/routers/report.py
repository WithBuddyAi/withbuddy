"""
리포트 API 라우터
────────────────────────────────────────────
POST /report 엔드포인트를 처리합니다.
사용자의 이번 주 대화 내역을 분석하여 주간 온보딩 리포트를 생성합니다.
"""

import io
from datetime import datetime

from fastapi import APIRouter, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

from chains.summary_chain import run_summary_chain
from memory.chat_history import get_chat_history

router = APIRouter(tags=["report"])

_KEYWORDS = [
    "연차", "휴가", "IT", "장비", "계정", "경비", "급여", "복리후생",
    "사무용품", "계약", "규정", "교육", "온보딩", "시스템", "AI",
]


def _build_report_pdf(user_id: str, user_name: str, report_md: str) -> bytes:
    """차트 + 마크다운 리포트를 PDF로 생성."""
    import fitz
    from utils.chart_generator import (
        generate_activity_gauge,
        generate_onboarding_progress,
        generate_topic_chart,
    )

    # 통계 계산
    messages = get_chat_history(user_id)
    human_msgs = [m.content for m in messages if m.type == "human"]
    all_text = " ".join(human_msgs)
    topics = [kw for kw in _KEYWORDS if kw in all_text]
    question_count = len(human_msgs)
    activity = "🟢 활발" if question_count >= 5 else ("🟡 보통" if question_count >= 2 else "🔴 저조")

    # 차트 이미지 생성
    chart_topic    = generate_topic_chart(topics)
    chart_gauge    = generate_activity_gauge(question_count)
    chart_progress = generate_onboarding_progress(question_count, topics)

    # PDF 생성
    buf = io.BytesIO()
    writer = fitz.DocumentWriter(buf)
    mediabox = fitz.paper_rect("a4")
    margin = 50

    # ── 1페이지: 헤더 + 차트 ──────────────────────────────
    page = writer.begin_page(mediabox)
    doc_tmp = fitz.open()
    page_tmp = doc_tmp.new_page(width=mediabox.width, height=mediabox.height)

    # 헤더
    now = datetime.now().strftime("%Y년 %m월 %d일")
    page_tmp.insert_text((margin, 60), "📊 온보딩 진척도 리포트", fontsize=20, color=(0.1, 0.14, 0.49))
    page_tmp.insert_text((margin, 85), f"{user_name or user_id} | {now} | 활동 상태: {activity}", fontsize=11, color=(0.4, 0.4, 0.4))
    page_tmp.draw_line((margin, 95), (mediabox.width - margin, 95), color=(0.7, 0.7, 0.7), width=0.5)

    # 차트 삽입 (게이지 + 진행도 나란히)
    def _insert_png(page_obj, png_bytes: bytes, rect: fitz.Rect):
        img_doc = fitz.open(stream=png_bytes, filetype="png")
        page_obj.show_pdf_page(rect, img_doc, 0)

    _insert_png(page_tmp, chart_gauge,    fitz.Rect(margin, 110, 310, 270))
    _insert_png(page_tmp, chart_progress, fitz.Rect(315,    110, mediabox.width - margin, 270))
    _insert_png(page_tmp, chart_topic,    fitz.Rect(margin, 280, mediabox.width - margin, 500))

    page_tmp.draw_contents(page)
    writer.end_page()

    # ── 2페이지: AI 리포트 텍스트 ──────────────────────────
    html_lines = [
        f"<h2>🤖 AI 분석 리포트</h2>",
        f"<p style='color:gray'>{user_name or user_id} | {now}</p>",
        "<hr/>",
    ]
    for line in report_md.splitlines():
        stripped = line.strip()
        if stripped.startswith("## "):
            html_lines.append(f"<h2>{stripped[3:]}</h2>")
        elif stripped.startswith("# "):
            html_lines.append(f"<h1>{stripped[2:]}</h1>")
        elif stripped.startswith("- "):
            html_lines.append(f"<li>{stripped[2:]}</li>")
        elif stripped == "":
            html_lines.append("<p>&nbsp;</p>")
        else:
            html_lines.append(f"<p>{stripped}</p>")

    css = (
        "body{font-family:sans-serif;font-size:10.5pt;line-height:1.7}"
        "h1{font-size:16pt;color:#1a237e}"
        "h2{font-size:13pt;color:#283593;margin-top:10pt}"
        "p{margin:2pt 0} li{margin:2pt 0}"
        "hr{border:0.5pt solid #ccc;margin:8pt 0}"
    )
    story = fitz.Story(html="\n".join(html_lines), user_css=css)
    where = mediabox + (margin, margin, -margin, -margin)
    more = True
    while more:
        page2 = writer.begin_page(mediabox)
        more, _ = story.place(where)
        story.draw(page2)
        writer.end_page()

    writer.close()
    return buf.getvalue()


# ── 요청 / 응답 스키마 ──────────────────────

class ReportRequest(BaseModel):
    user_id: int = Field(..., description="사용자 고유 ID", example=1)
    user_name: str = Field("", description="사용자 이름")


class ReportResponse(BaseModel):
    report: str = Field(..., description="주간 온보딩 리포트 (마크다운 형식)")


# ── 엔드포인트 ──────────────────────────────

@router.post("/report", response_model=ReportResponse)
async def generate_report(request: ReportRequest):
    """
    주간 온보딩 리포트 생성

    - 해당 사용자의 이번 주 대화 내역을 분석합니다.
    - 학습 진행 상황, 주요 질문 영역, 개선 사항을 포함한 리포트를 반환합니다.
    - 대화 내역이 없는 경우 빈 리포트를 반환합니다.
    """
    try:
        user_id_str = str(request.user_id)

        report = run_summary_chain(user_id_str)

        return ReportResponse(report=report)

    except ValueError as e:
        raise HTTPException(status_code=500, detail=f"설정 오류: {str(e)}")
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"리포트 생성 중 오류가 발생했습니다: {str(e)}",
        )


@router.post("/report/pdf")
async def generate_report_pdf(request: ReportRequest):
    """
    온보딩 리포트 PDF 생성 (차트 포함)
    - 토픽 분포 차트, 활동 게이지, 진행도 차트 포함
    - AI 분석 리포트 텍스트 포함
    """
    try:
        user_id_str = str(request.user_id)
        report = run_summary_chain(user_id_str)
        pdf_bytes = _build_report_pdf(user_id_str, request.user_name, report)
        filename = f"onboarding_report_{request.user_id}.pdf"
        return Response(
            content=pdf_bytes,
            media_type="application/pdf",
            headers={"Content-Disposition": f"attachment; filename={filename}"},
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"PDF 생성 실패: {str(e)}")
