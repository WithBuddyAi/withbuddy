"""
관리자 API 라우터
────────────────────────────────────────────
회사 커스텀 정보 및 팀 구성 관리
문서 업로드 및 RAG 자동 인덱싱

GET  /admin/company-info        : 회사 정보 조회
PUT  /admin/company-info        : 회사 정보 수정
GET  /admin/team-config         : 팀 구성 조회
PUT  /admin/team-config         : 팀 구성 수정
POST /admin/documents           : 문서 업로드 + RAG 자동 인덱싱
GET  /admin/documents           : 인덱싱된 문서 목록 조회
DELETE /admin/documents/{filename} : 문서 삭제 + RAG에서 제거
"""

import json
import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests as _requests
from fastapi import APIRouter, File, Form, Header, HTTPException, UploadFile
from langchain_core.documents import Document
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter
from pydantic import BaseModel, Field

from core.vectorstore import get_vectorstore, invalidate_bm25_cache

logger = logging.getLogger(__name__)

_BACKEND_INTERNAL_URL = os.getenv("BACKEND_INTERNAL_URL", "http://10.0.0.81:8080")
_INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY", "")

_COMPANY_INFO_PATH = Path(__file__).parent.parent / "data" / "company_info.json"


def get_company_info() -> dict:
    if _COMPANY_INFO_PATH.exists():
        return json.loads(_COMPANY_INFO_PATH.read_text(encoding="utf-8"))
    return {}


def save_company_info(updates: dict) -> dict:
    current = get_company_info()
    current.update(updates)
    _COMPANY_INFO_PATH.parent.mkdir(exist_ok=True)
    _COMPANY_INFO_PATH.write_text(json.dumps(current, ensure_ascii=False, indent=2), encoding="utf-8")
    return current

router = APIRouter(prefix="/admin", tags=["admin"])

_TEAM_CONFIG_PATH = Path(__file__).parent.parent / "data" / "team_config.json"
_DOCS_DIR = Path(__file__).parent.parent / "docs"

# 허용 파일 확장자
_ALLOWED_EXT = {".pdf", ".md", ".txt"}

_CHUNK_SIZE = 500
_CHUNK_OVERLAP = 50


def _split_text(text: str) -> list[str]:
    chunks = []
    start = 0
    while start < len(text):
        end = start + _CHUNK_SIZE
        chunks.append(text[start:end])
        start += _CHUNK_SIZE - _CHUNK_OVERLAP
    return [c for c in chunks if c.strip()]


def _split_documents(text: str, filename: str, metadata: dict) -> list[Document]:
    """헤더 기준(.md) 또는 고정 크기(기타) 청킹 → Document 리스트 반환"""
    title = metadata.get("title", "")
    char_splitter = RecursiveCharacterTextSplitter(
        chunk_size=_CHUNK_SIZE,
        chunk_overlap=_CHUNK_OVERLAP,
        separators=["\n\n", "\n", ".", " ", ""],
    )

    if filename.lower().endswith(".md"):
        md_splitter = MarkdownHeaderTextSplitter(
            headers_to_split_on=[("#", "h1"), ("##", "h2"), ("###", "h3")],
            strip_headers=False,
        )
        sections = md_splitter.split_text(text)
        docs: list[Document] = []
        for sec in sections:
            content = sec.page_content.strip()
            if not content:
                continue
            sec_meta = {**metadata, **sec.metadata}
            sub = char_splitter.create_documents([content], metadatas=[sec_meta]) if len(content) > _CHUNK_SIZE else [Document(page_content=content, metadata=sec_meta)]
            for doc in sub:
                if title and title not in doc.page_content[:80]:
                    doc.page_content = f"[{title}]\n{doc.page_content}"
            docs.extend(sub)
        if docs:
            return docs

    docs = char_splitter.create_documents([text], metadatas=[metadata])
    for doc in docs:
        if title and title not in doc.page_content[:80]:
            doc.page_content = f"[{title}]\n{doc.page_content}"
    return docs


# ── 텍스트 추출 헬퍼 ────────────────────────────────────────────

def _extract_text_from_pdf(data: bytes) -> str:
    try:
        import fitz
        doc = fitz.open(stream=data, filetype="pdf")
        return "\n".join(page.get_text() for page in doc).strip()
    except ImportError:
        from pypdf import PdfReader
        import io
        reader = PdfReader(io.BytesIO(data))
        return "\n".join(p.extract_text() or "" for p in reader.pages).strip()


def _extract_text_from_docx(data: bytes) -> str:
    import io
    import docx2txt
    return docx2txt.process(io.BytesIO(data))


def _extract_text(filename: str, data: bytes) -> str:
    ext = Path(filename).suffix.lower()
    if ext == ".pdf":
        return _extract_text_from_pdf(data)
    if ext in (".docx", ".doc"):
        return _extract_text_from_docx(data)
    return data.decode("utf-8", errors="ignore")


# ── 스키마 ──────────────────────────────────────────────────────

class CompanyInfoRequest(BaseModel):
    lunch_time: Optional[str] = Field(None, description="점심시간 (예: 12:00 - 13:00)")
    payday: Optional[str] = Field(None, description="급여일 (예: 매월 25일)")
    vacation_policy: Optional[str] = Field(None, description="연차 정책")
    welfare: Optional[str] = Field(None, description="복지혜택 설명")
    work_hours: Optional[str] = Field(None, description="근무시간 (예: 09:00 - 18:00)")
    dress_code: Optional[str] = Field(None, description="복장 규정")
    tools: Optional[List[str]] = Field(None, description="주요 업무 툴 목록")
    office_address: Optional[str] = Field(None, description="사무실 주소")
    custom_rules: Optional[Dict[str, Any]] = Field(None, description="커스텀 규정 (key-value)")


class TeamMemberSchema(BaseModel):
    user_id: Optional[str] = None
    name: str
    role: Optional[str] = "멤버"
    department: Optional[str] = None
    mbti: Optional[str] = None
    photo_url: Optional[str] = None
    favorite_restaurant: Optional[str] = None
    intro: Optional[str] = None


class TeamSchema(BaseModel):
    team_name: Optional[str] = None
    leader_name: Optional[str] = None
    leader_slack_id: Optional[str] = None
    leader_department: Optional[str] = None
    leader_mbti: Optional[str] = None
    leader_photo_url: Optional[str] = None
    leader_restaurant: Optional[str] = None
    leader_intro: Optional[str] = None
    members: Optional[List[TeamMemberSchema]] = None
    trainees: Optional[List[Dict]] = None


class TeamConfigRequest(BaseModel):
    teams: List[TeamSchema]


# ── 회사 정보 엔드포인트 ────────────────────────────────────────

@router.get("/company-info")
async def get_company_info_endpoint():
    return get_company_info()


@router.put("/company-info")
async def update_company_info(req: CompanyInfoRequest):
    try:
        updates = {k: v for k, v in req.model_dump().items() if v is not None}
        saved = save_company_info(updates)
        return {"message": "회사 정보가 업데이트되었습니다.", "data": saved}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ── 팀 구성 엔드포인트 ─────────────────────────────────────────

@router.get("/team-config")
async def get_team_config():
    if _TEAM_CONFIG_PATH.exists():
        return json.loads(_TEAM_CONFIG_PATH.read_text(encoding="utf-8"))
    return {"teams": []}


@router.put("/team-config")
async def update_team_config(req: TeamConfigRequest):
    try:
        data = req.model_dump()
        _TEAM_CONFIG_PATH.write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return {"message": "팀 구성이 업데이트되었습니다.", "data": data}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/team-config/member/{name}")
async def delete_team_member(name: str):
    try:
        if not _TEAM_CONFIG_PATH.exists():
            raise HTTPException(status_code=404, detail="팀 설정 없음")
        config = json.loads(_TEAM_CONFIG_PATH.read_text(encoding="utf-8"))
        removed = False
        for team in config.get("teams", []):
            before = len(team.get("members", []))
            team["members"] = [m for m in team.get("members", []) if m.get("name") != name]
            if len(team["members"]) < before:
                removed = True
        if not removed:
            raise HTTPException(status_code=404, detail=f"'{name}' 멤버를 찾을 수 없습니다.")
        _TEAM_CONFIG_PATH.write_text(
            json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return {"message": f"'{name}' 카드가 삭제되었습니다."}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


class MemberSyncRequest(BaseModel):
    name: str
    department: Optional[str] = None
    mbti: Optional[str] = None
    photo_url: Optional[str] = None
    favorite_restaurant: Optional[str] = None
    intro: Optional[str] = None


@router.post("/team-config/member")
async def upsert_team_member(req: MemberSyncRequest):
    try:
        if _TEAM_CONFIG_PATH.exists():
            config = json.loads(_TEAM_CONFIG_PATH.read_text(encoding="utf-8"))
        else:
            config = {"teams": [{"team_name": "팀", "members": []}]}

        if not config.get("teams"):
            config["teams"] = [{"team_name": "팀", "members": []}]

        updates = {k: v for k, v in req.model_dump().items() if v is not None}

        found = False
        for team in config["teams"]:
            if team.get("leader_name") == req.name:
                if "department" in updates: team["leader_department"] = updates["department"]
                if "mbti" in updates: team["leader_mbti"] = updates["mbti"]
                if "photo_url" in updates: team["leader_photo_url"] = updates["photo_url"]
                if "favorite_restaurant" in updates: team["leader_restaurant"] = updates["favorite_restaurant"]
                if "intro" in updates: team["leader_intro"] = updates["intro"]
                found = True
                break
            for m in team.get("members", []):
                if m.get("name") == req.name:
                    m.update({k: v for k, v in updates.items() if k != "name"})
                    found = True
                    break
            if found:
                break

        if not found:
            config["teams"][0].setdefault("members", []).append({
                "name": req.name,
                "role": "팀원",
                **{k: v for k, v in updates.items() if k != "name"},
            })

        _TEAM_CONFIG_PATH.write_text(
            json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return {"message": "팀 카드가 업데이트되었습니다."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ── 문서 업로드 + RAG 인덱싱 엔드포인트 ───────────────────────

@router.post("/documents")
async def upload_document(file: UploadFile = File(...), company_code: str = Form("")):
    """
    문서 업로드 후 RAG 파이프라인에 자동 인덱싱.
    지원 형식: PDF, Markdown(.md), 텍스트(.txt)
    """
    filename = file.filename or ""
    ext = Path(filename).suffix.lower()

    if ext not in _ALLOWED_EXT:
        raise HTTPException(
            status_code=400,
            detail=f"지원하지 않는 파일 형식입니다. 허용: {', '.join(_ALLOWED_EXT)}"
        )

    data = await file.read()
    if len(data) > 50 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="파일 크기는 50MB 이하여야 합니다.")

    # docs/ 폴더에 저장
    _DOCS_DIR.mkdir(exist_ok=True)
    save_path = _DOCS_DIR / filename
    save_path.write_bytes(data)

    # 텍스트 추출
    text = _extract_text(filename, data)
    if not text.strip():
        save_path.unlink(missing_ok=True)
        raise HTTPException(status_code=422, detail="파일에서 텍스트를 추출할 수 없습니다.")

    # 청킹 → ChromaDB 인덱싱
    chunks = _split_text(text)
    docs = [
        Document(
            page_content=chunk,
            metadata={"source": filename, "chunk_index": i, **({"company_code": company_code} if company_code else {})},
        )
        for i, chunk in enumerate(chunks)
    ]

    vs = get_vectorstore()
    vs.add_documents(docs)
    invalidate_bm25_cache(company_code)

    return {
        "message": f"'{filename}' 업로드 및 인덱싱 완료",
        "chunks_indexed": len(docs),
    }


@router.get("/documents")
async def list_documents():
    """docs/ 폴더에 저장된 문서 목록 반환."""
    if not _DOCS_DIR.exists():
        return {"documents": []}

    files = [
        {"filename": f.name, "size_kb": round(f.stat().st_size / 1024, 1)}
        for f in _DOCS_DIR.iterdir()
        if f.is_file() and f.suffix.lower() in _ALLOWED_EXT
    ]
    return {"documents": files}


class IngestRequest(BaseModel):
    documentId: int
    companyCode: str = ""


@router.post("/ingest")
async def ingest_from_backend(
    req: IngestRequest,
    x_internal_key: Optional[str] = Header(None, alias="X-Internal-Key"),
):
    """
    BE 문서 업로드 후 자동 인덱싱 (SCRUM-477).
    BE → AI 내부 호출 전용. X-Internal-Key 검증 필수.
    """
    if not _INTERNAL_API_KEY or x_internal_key != _INTERNAL_API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized")

    headers = {"X-API-Key": _INTERNAL_API_KEY}
    doc_id = req.documentId

    try:
        # 1. 문서 메타데이터 조회
        meta_resp = _requests.get(
            f"{_BACKEND_INTERNAL_URL}/api/v1/documents/{doc_id}",
            headers=headers,
            timeout=15,
        )
        meta_resp.raise_for_status()
        doc_meta = meta_resp.json()
        filename = doc_meta.get("fileName", f"document_{doc_id}.pdf")
        title = doc_meta.get("title", filename)

        # 2. 다운로드 URL 조회
        dl_url_resp = _requests.get(
            f"{_BACKEND_INTERNAL_URL}/api/v1/documents/{doc_id}/download",
            headers=headers,
            timeout=15,
        )
        dl_url_resp.raise_for_status()
        dl_data = dl_url_resp.json()
        download_url = dl_data["downloadUrl"] if isinstance(dl_data, dict) else dl_data[0]["downloadUrl"]
        if download_url.startswith("/"):
            download_url = f"{_BACKEND_INTERNAL_URL}{download_url}"
            file_resp = _requests.get(download_url, headers=headers, timeout=60)
        else:
            file_resp = _requests.get(download_url, timeout=60)
        file_resp.raise_for_status()
        file_bytes = file_resp.content

        # 3. 텍스트 추출
        text = _extract_text(filename, file_bytes)
        if not text.strip():
            raise HTTPException(status_code=422, detail="텍스트 추출 실패")

        # 4. 청킹 → 인덱싱 (기존 청크 먼저 삭제 후 재인덱싱)
        metadata = {
            "source": filename,
            "title": title,
            "doc_id": str(doc_id),
            "document_type": doc_meta.get("documentType", ""),
            "department": doc_meta.get("department", ""),
            "company_code": req.companyCode,
        }
        chunks = _split_documents(text, filename, metadata)

        vs = get_vectorstore()
        try:
            vs.delete(where={"doc_id": str(doc_id)})
        except Exception:
            pass  # 최초 인덱싱 시 삭제할 청크 없어도 정상
        for chunk in chunks:
            vs.add_documents([chunk])

        # 5. BM25 캐시 무효화
        invalidate_bm25_cache(req.companyCode)

        logger.info("자동 인덱싱 완료: documentId=%s, chunks=%d", doc_id, len(chunks))
        return {"success": True, "documentId": doc_id, "chunksIndexed": len(chunks)}

    except HTTPException:
        raise
    except Exception as e:
        logger.error("자동 인덱싱 실패: documentId=%s, error=%s", doc_id, e)
        raise HTTPException(status_code=500, detail=f"인덱싱 실패: {e}")


class DeindexRequest(BaseModel):
    documentId: int
    companyCode: str = ""


@router.delete("/ingest")
async def deindex_from_backend(
    req: DeindexRequest,
    x_internal_key: Optional[str] = Header(None, alias="X-Internal-Key"),
):
    """
    BE 문서 삭제 시 ChromaDB 청크 제거.
    BE → AI 내부 호출 전용. X-Internal-Key 검증 필수.
    """
    if not _INTERNAL_API_KEY or x_internal_key != _INTERNAL_API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized")

    doc_id = req.documentId
    try:
        vs = get_vectorstore()
        vs.delete(where={"doc_id": str(doc_id)})
        invalidate_bm25_cache(req.companyCode)
        logger.info("자동 인덱스 삭제 완료: documentId=%s", doc_id)
        return {"success": True, "documentId": doc_id}
    except Exception as e:
        logger.error("자동 인덱스 삭제 실패: documentId=%s, error=%s", doc_id, e)
        raise HTTPException(status_code=500, detail=f"인덱스 삭제 실패: {e}")


@router.delete("/documents/{filename}")
async def delete_document(filename: str):
    """
    문서 파일 삭제 + ChromaDB에서 해당 문서 청크 제거.
    """
    file_path = _DOCS_DIR / filename
    if not file_path.exists():
        raise HTTPException(status_code=404, detail=f"'{filename}' 파일을 찾을 수 없습니다.")

    # ChromaDB에서 해당 파일 청크 삭제
    vs = get_vectorstore()
    vs.delete(where={"source": filename})

    # 파일 삭제
    file_path.unlink()
    invalidate_bm25_cache()

    return {"message": f"'{filename}' 삭제 완료"}
