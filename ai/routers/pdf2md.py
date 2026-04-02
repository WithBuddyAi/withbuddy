"""
PDF ↔ Markdown 변환 라우터
────────────────────────────────────────────
POST /api/pdf-to-md  :  PDF → Markdown 텍스트 반환
POST /api/md-to-pdf  :  Markdown 텍스트 → PDF 반환
pymupdf(fitz) 를 사용합니다.
"""

import io
import re

from fastapi import APIRouter, File, HTTPException, Request, UploadFile
from fastapi.responses import PlainTextResponse, Response

router = APIRouter(prefix="/api", tags=["pdf2md"])


# ── 변환 헬퍼 ──────────────────────────────────────────────────────────


def _convert_with_fitz(data: bytes) -> str:
    """pymupdf: 폰트 크기 기반 제목 감지, 단락 구조 보존"""
    import fitz  # type: ignore

    doc = fitz.open(stream=data, filetype="pdf")
    lines: list[str] = []

    for page_num, page in enumerate(doc):
        if page_num > 0:
            lines.append("\n---\n")  # 페이지 구분선

        blocks = page.get_text("dict")["blocks"]
        prev_bottom = None

        for block in blocks:
            if block.get("type") != 0:  # 텍스트 블록만 처리
                continue

            block_lines = block.get("lines", [])
            for line in block_lines:
                spans = line.get("spans", [])
                if not spans:
                    continue

                text = "".join(s["text"] for s in spans).strip()
                if not text:
                    continue

                max_size: float = max(s.get("size", 10) for s in spans)
                is_bold = any(s.get("flags", 0) & 16 for s in spans)

                # 이전 블록과 세로 간격이 크면 빈 줄 삽입
                top = line.get("bbox", [0, 0, 0, 0])[1]
                if prev_bottom is not None and (top - prev_bottom) > max_size * 1.5:
                    lines.append("")
                prev_bottom = line.get("bbox", [0, 0, 0, 0])[3]

                if max_size >= 20:
                    lines.append(f"# {text}")
                elif max_size >= 16 or (max_size >= 14 and is_bold):
                    lines.append(f"## {text}")
                elif max_size >= 13 or is_bold:
                    lines.append(f"### {text}")
                else:
                    lines.append(text)

    # 연속된 빈 줄 정리
    result = re.sub(r"\n{3,}", "\n\n", "\n".join(lines))
    return result.strip()


def _convert_with_pypdf(data: bytes) -> str:
    """pypdf 폴백: 텍스트 추출 후 짧은 줄을 제목 후보로 처리"""
    from pypdf import PdfReader  # type: ignore

    reader = PdfReader(io.BytesIO(data))
    page_texts: list[str] = []

    for page in reader.pages:
        raw = (page.extract_text() or "").strip()
        if not raw:
            continue

        processed: list[str] = []
        for line in raw.splitlines():
            stripped = line.strip()
            if not stripped:
                processed.append("")
                continue
            # 짧고 문장 종결 어미가 없는 줄 → 제목 후보
            ends_sentence = stripped.endswith((".", ",", "다.", "요.", "며,", "고,", "다,"))
            if len(stripped) <= 40 and not ends_sentence and not stripped[0].isdigit():
                processed.append(f"## {stripped}")
            else:
                processed.append(stripped)
        page_texts.append("\n".join(processed))

    result = "\n\n---\n\n".join(page_texts)
    return re.sub(r"\n{3,}", "\n\n", result).strip()


def _pdf_bytes_to_md(data: bytes) -> str:
    try:
        return _convert_with_fitz(data)
    except ImportError:
        return _convert_with_pypdf(data)


# ── 엔드포인트 ──────────────────────────────────────────────────────────


@router.post("/pdf-to-md", response_class=PlainTextResponse)
async def pdf_to_md(file: UploadFile = File(...)) -> str:
    """PDF 파일을 Markdown 텍스트로 변환합니다."""
    if not (file.filename or "").lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="PDF 파일만 업로드할 수 있습니다.")

    data = await file.read()
    if len(data) > 50 * 1024 * 1024:  # 50 MB 제한
        raise HTTPException(status_code=413, detail="파일 크기는 50 MB 이하여야 합니다.")

    try:
        md = _pdf_bytes_to_md(data)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"변환 실패: {e}")

    if not md.strip():
        raise HTTPException(status_code=422, detail="PDF에서 텍스트를 추출할 수 없습니다. 스캔 PDF이거나 텍스트 레이어가 없는 파일일 수 있습니다.")

    return md


# ── Markdown → PDF ──────────────────────────────────────────────────────


def _simple_md_to_html(md_text: str) -> str:
    """마크다운을 fitz.Story 에 먹일 간단한 HTML 로 변환"""
    html_parts: list[str] = []
    in_ul = False

    for line in md_text.split("\n"):
        stripped = line.strip()

        # 불릿 리스트 감지
        is_bullet = stripped.startswith(("- ", "• ", "* "))
        if not is_bullet and in_ul:
            html_parts.append("</ul>")
            in_ul = False

        if stripped.startswith("# ") and not stripped.startswith("## "):
            html_parts.append(f"<h1>{stripped[2:].strip()}</h1>")
        elif stripped.startswith("## ") and not stripped.startswith("### "):
            html_parts.append(f"<h2>{stripped[3:].strip()}</h2>")
        elif stripped.startswith("### "):
            html_parts.append(f"<h3>{stripped[4:].strip()}</h3>")
        elif stripped in ("---", "***", "___"):
            html_parts.append("<hr/>")
        elif is_bullet:
            if not in_ul:
                html_parts.append("<ul>")
                in_ul = True
            html_parts.append(f"<li>{stripped[2:].strip()}</li>")
        elif stripped == "":
            html_parts.append("<p>&nbsp;</p>")
        else:
            # **bold** → <b>
            text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", stripped)
            html_parts.append(f"<p>{text}</p>")

    if in_ul:
        html_parts.append("</ul>")

    return "\n".join(html_parts)


def _md_to_pdf_bytes(md_text: str) -> bytes:
    import fitz  # type: ignore  # pymupdf

    html_body = _simple_md_to_html(md_text)
    user_css = (
        "body { font-size: 11pt; line-height: 1.7; font-family: sans-serif; }"
        "h1 { font-size: 20pt; color: #1a237e; margin: 16pt 0 6pt; }"
        "h2 { font-size: 15pt; color: #283593; margin: 12pt 0 4pt; }"
        "h3 { font-size: 12pt; color: #3949ab; margin: 8pt 0 3pt; }"
        "p  { margin: 3pt 0; }"
        "li { margin: 2pt 0; }"
        "hr { border: 0.5pt solid #bbb; margin: 10pt 0; }"
    )

    story = fitz.Story(html=html_body, user_css=user_css)

    buf = io.BytesIO()
    writer = fitz.DocumentWriter(buf)
    mediabox = fitz.paper_rect("a4")
    where = mediabox + (50, 50, -50, -50)  # 50pt 마진

    more = True
    while more:
        device = writer.begin_page(mediabox)
        more, _ = story.place(where)
        story.draw(device)
        writer.end_page()
    writer.close()

    return buf.getvalue()


@router.post("/md-to-pdf")
async def md_to_pdf(request: Request) -> Response:
    """Markdown 텍스트를 PDF로 변환합니다. (요청 바디: plain text)"""
    md_text = (await request.body()).decode("utf-8")
    if not md_text.strip():
        raise HTTPException(status_code=400, detail="마크다운 내용이 비어있습니다.")

    try:
        pdf_bytes = _md_to_pdf_bytes(md_text)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"PDF 변환 실패: {e}")

    return Response(content=pdf_bytes, media_type="application/pdf")
