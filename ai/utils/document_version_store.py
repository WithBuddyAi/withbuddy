"""
문서 버전 관리 (자동 인덱싱 연동)
────────────────────────────────────────────
content_hash 기반 변경 감지 + 버전 이력 저장.
저장 위치: data/document_versions.json
"""

import hashlib
import json
import logging
from datetime import datetime
from pathlib import Path

logger = logging.getLogger(__name__)

_VERSIONS_PATH = Path(__file__).parent.parent / "data" / "document_versions.json"


def _load() -> dict:
    if not _VERSIONS_PATH.exists():
        return {}
    try:
        return json.loads(_VERSIONS_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _save(data: dict) -> None:
    _VERSIONS_PATH.parent.mkdir(parents=True, exist_ok=True)
    _VERSIONS_PATH.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def compute_hash(file_bytes: bytes) -> str:
    return hashlib.sha256(file_bytes).hexdigest()


def get_version(doc_id: int) -> dict | None:
    """저장된 버전 정보 조회. 없으면 None."""
    return _load().get(str(doc_id))


def is_changed(doc_id: int, content_hash: str) -> bool:
    """hash가 달라졌으면 True (최초 업로드도 True)."""
    entry = get_version(doc_id)
    if entry is None:
        return True
    return entry.get("content_hash") != content_hash


def save_version(
    doc_id: int,
    company_code: str,
    content_hash: str,
    chunks_indexed: int,
) -> int:
    """버전 저장 후 새 version 번호 반환. 최초면 1."""
    data = _load()
    key = str(doc_id)
    prev = data.get(key, {})
    new_version = prev.get("version", 0) + 1
    data[key] = {
        "doc_id": doc_id,
        "company_code": company_code,
        "content_hash": content_hash,
        "version": new_version,
        "chunks_indexed": chunks_indexed,
        "updated_at": datetime.now().isoformat(timespec="seconds"),
    }
    try:
        _save(data)
    except Exception as e:
        logger.warning("document_version_store 저장 실패 (doc_id=%s): %s", doc_id, e)
    return new_version
