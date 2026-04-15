"""
문서함 API 라우터
────────────────────────────────────────────
docs/ 폴더의 인사·행정 문서를 목록 조회 및 내용 반환합니다.

GET  /docs/list        : 문서 목록 반환
POST /docs/view        : 특정 문서 내용 반환 (마크다운 텍스트)
GET  /docs/download/{filename} : 파일 직접 다운로드
"""

import re
from datetime import datetime
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

# 한국어 형태소 분석기 (선택적 로드 — 없으면 단순 분리 fallback)
try:
    from kiwipiepy import Kiwi
    _kiwi: Optional[Kiwi] = Kiwi()
except Exception:
    _kiwi = None


# 파일명/질문 매칭 시 제외할 일반 단어 (신청서·방법 등은 모든 파일에 공통 등장)
_STOPWORDS = {"신청", "신청서", "방법", "처리", "결과", "서류", "안내", "가이드", "규정", "지침", "작성"}


def _extract_nouns(text: str) -> set[str]:
    """텍스트에서 명사(NNG/NNP/NNB) 추출. kiwipiepy 없으면 공백 분리로 fallback."""
    if _kiwi is None:
        words = {w for w in re.split(r"[\s_\-]+", text.lower()) if len(w) >= 2}
        return words - _STOPWORDS
    tokens = _kiwi.tokenize(text)
    return {tok.form for tok in tokens if tok.tag.startswith("NN") and len(tok.form) >= 2} - _STOPWORDS

router = APIRouter(prefix="/files", tags=["docs"])

# docs 폴더 경로 (main.py 기준)
_DOCS_DIR = Path(__file__).parent.parent / "docs"

# 허용 확장자 및 레이블
_ALLOWED_EXT = {
    ".md":   ("마크다운", "📄"),
    ".txt":  ("텍스트",   "📝"),
    ".pdf":  ("PDF",     "📕"),
    ".docx": ("Word",    "📘"),
    ".xlsx": ("Excel",   "📗"),
    ".hwp":  ("한글",    "📃"),
    ".pptx": ("PPT",     "📙"),
}


class DocMeta(BaseModel):
    filename: str
    label: str
    icon: str
    size_kb: float
    updated: str
    viewable: bool   # 텍스트 계열이면 True (내용 조회 가능)


class DocListResponse(BaseModel):
    docs: list[DocMeta]


class DocViewRequest(BaseModel):
    filename: str = Field(..., description="문서 파일명 (확장자 포함)")


class DocViewResponse(BaseModel):
    filename: str
    content: str       # 텍스트·마크다운 원문
    is_markdown: bool


@router.get("/list", response_model=DocListResponse)
def list_docs():
    """docs/ 폴더 내 문서 목록 반환"""
    if not _DOCS_DIR.exists():
        return DocListResponse(docs=[])

    result = []
    for f in sorted(_DOCS_DIR.iterdir()):
        if not f.is_file():
            continue
        ext = f.suffix.lower()
        if ext not in _ALLOWED_EXT:
            continue
        label, icon = _ALLOWED_EXT[ext]
        stat = f.stat()
        result.append(DocMeta(
            filename=f.name,
            label=label,
            icon=icon,
            size_kb=round(stat.st_size / 1024, 1),
            updated=datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d"),
            viewable=ext in (".md", ".txt"),
        ))
    return DocListResponse(docs=result)


@router.post("/view", response_model=DocViewResponse)
def view_doc(request: DocViewRequest):
    """텍스트·마크다운 문서 내용 반환"""
    path = _DOCS_DIR / request.filename
    # 경로 탈출 방지
    if not path.resolve().is_relative_to(_DOCS_DIR.resolve()):
        raise HTTPException(status_code=403, detail="접근 불가")
    if not path.exists():
        raise HTTPException(status_code=404, detail="문서를 찾을 수 없습니다")
    ext = path.suffix.lower()
    if ext not in (".md", ".txt"):
        raise HTTPException(status_code=400, detail="텍스트 형식이 아닌 파일은 직접 다운로드하세요")
    content = path.read_text(encoding="utf-8")
    return DocViewResponse(
        filename=request.filename,
        content=content,
        is_markdown=(ext == ".md"),
    )


@router.post("/ingest")
async def ingest_doc(file: UploadFile = File(...)):
    """
    파일을 docs/에 저장하고 ChromaDB에 즉시 인덱싱 (리더 전용)
    - PDF / MD / TXT : 텍스트 추출 후 벡터 학습
    - 그 외 (docx, xlsx 등) : 파일 저장만 (다운로드 제공용)
    """
    try:
        filename = file.filename or "unknown"
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"filename error: {e}")

    ext = Path(filename).suffix.lower()
    if ext not in _ALLOWED_EXT:
        raise HTTPException(status_code=400, detail=f"지원하지 않는 형식입니다. 허용: {', '.join(_ALLOWED_EXT)}")

    try:
        data = await file.read()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"file read error: {e}")

    if len(data) > 50 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="파일 크기는 50MB 이하여야 합니다.")

    # 1. docs/ 에 저장
    _DOCS_DIR.mkdir(parents=True, exist_ok=True)
    (_DOCS_DIR / filename).write_bytes(data)

    # 2. 텍스트 추출
    text = None
    if ext == ".pdf":
        try:
            from routers.pdf2md import _pdf_bytes_to_md
            text = _pdf_bytes_to_md(data)
        except Exception:
            pass
    elif ext in (".md", ".txt"):
        text = data.decode("utf-8", errors="ignore")

    # 3. ChromaDB 인덱싱
    chunks_count = 0
    ingest_error = None
    if text and text.strip():
        try:
            from core.vectorstore import get_vectorstore
            from langchain_core.documents import Document
            from langchain_text_splitters import RecursiveCharacterTextSplitter
            doc = Document(page_content=text, metadata={"source": filename})
            splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
            chunks = splitter.split_documents([doc])
            get_vectorstore().add_documents(chunks)
            chunks_count = len(chunks)
        except Exception as e:
            ingest_error = str(e)

    vectorized = chunks_count > 0
    return {
        "filename": filename,
        "vectorized": vectorized,
        "chunks": chunks_count,
        "message": f"저장 완료, {chunks_count}개 청크로 학습됐어요 ✅" if vectorized else (f"저장 완료 (학습 오류: {ingest_error})" if ingest_error else "파일 저장 완료 (이 형식은 텍스트 추출이 어려워 학습은 생략됐어요)"),
    }


@router.get("/download/{filename}")
def download_doc(filename: str):
    """파일 다운로드"""
    path = _DOCS_DIR / filename
    if not path.resolve().is_relative_to(_DOCS_DIR.resolve()):
        raise HTTPException(status_code=403, detail="접근 불가")
    if not path.exists():
        raise HTTPException(status_code=404, detail="문서를 찾을 수 없습니다")
    return FileResponse(path, filename=filename)


def find_related_docs(query: str) -> list[dict]:
    """
    질문 텍스트와 관련된 온보딩 양식·가이드 문서를 파일명 키워드 매칭으로 찾아 반환합니다.
    채팅 답변 후 관련 양식·가이드를 자동 추천하는 데 사용합니다.
    PDF 사규 파일은 제외합니다 (RAG 출처로만 표시).
    최대 3개까지 반환합니다.
    """
    if not _DOCS_DIR.exists():
        return []

    # PDF는 사규 문서로 RAG 출처에 표시되므로 추천 대상에서 제외
    _RELATED_EXTS = {".md", ".txt", ".docx", ".xlsx", ".hwp", ".pptx"}

    query_nouns = _extract_nouns(query)  # 질문 명사 1회만 추출
    result = []

    for f in sorted(_DOCS_DIR.iterdir()):
        if not f.is_file():
            continue
        ext = f.suffix.lower()
        if ext not in _RELATED_EXTS:
            continue

        # 파일명과 질문 각각에서 명사 추출 후 부분 문자열 포함 매칭
        # (예: "연차" ∈ "연차휴가", "경비" ∈ "경비지출")
        stem_nouns = _extract_nouns(f.stem.replace("_", " ").replace("-", " "))
        matched = any(
            qn in fn or fn in qn
            for qn in query_nouns
            for fn in stem_nouns
        )
        if matched:
            label, icon = _ALLOWED_EXT[ext]
            result.append({
                "filename": f.name,
                "label": label,
                "icon": icon,
                "viewable": ext in (".md", ".txt"),
            })

    return result[:3]
