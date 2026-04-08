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
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from langchain_core.documents import Document
from pydantic import BaseModel, Field

from core.vectorstore import get_vectorstore

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


def _extract_text(filename: str, data: bytes) -> str:
    ext = Path(filename).suffix.lower()
    if ext == ".pdf":
        return _extract_text_from_pdf(data)
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
async def upload_document(file: UploadFile = File(...), company_id: str = Form("")):
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
            metadata={"source": filename, "chunk_index": i, **({"company_id": company_id} if company_id else {})},
        )
        for i, chunk in enumerate(chunks)
    ]

    vs = get_vectorstore()
    vs.add_documents(docs)

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

    return {"message": f"'{filename}' 삭제 완료"}
