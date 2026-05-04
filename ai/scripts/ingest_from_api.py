"""
백엔드 API 기반 문서 인덱싱 스크립트 (ST-026)
────────────────────────────────────────────
백엔드 documents API에서 문서 목록을 가져와
ChromaDB에 인덱싱합니다.

- 이미 인덱싱된 문서는 중복 저장하지 않음
- 신규 문서만 선별하여 인덱싱
- company_code 메타데이터 포함 (ST-027 격리 지원)

사용법:
    # 전체 문서 인덱싱
    python scripts/ingest_from_api.py

    # 특정 회사 문서만
    python scripts/ingest_from_api.py --company_code WB1001

    # 백엔드 API URL 지정
    python scripts/ingest_from_api.py --api_url http://localhost:8080
"""

import argparse
import hashlib
import io
import os
import sys
import tempfile

import requests
from dotenv import load_dotenv
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.documents import Document

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
load_dotenv()

from core.embeddings import get_embeddings
from core.vectorstore import get_vectorstore, COLLECTION_NAME

CHUNK_SIZE = 500
CHUNK_OVERLAP = 50

# 인덱싱 완료된 문서 ID 추적 파일
_INDEXED_IDS_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "data", "indexed_doc_ids.txt"
)


def _load_indexed_ids() -> set:
    """이미 인덱싱된 문서 ID 목록 로드"""
    if not os.path.exists(_INDEXED_IDS_PATH):
        return set()
    with open(_INDEXED_IDS_PATH, "r", encoding="utf-8") as f:
        return {line.strip() for line in f if line.strip()}


def _save_indexed_id(doc_id: str) -> None:
    """인덱싱 완료된 문서 ID 저장"""
    os.makedirs(os.path.dirname(_INDEXED_IDS_PATH), exist_ok=True)
    with open(_INDEXED_IDS_PATH, "a", encoding="utf-8") as f:
        f.write(doc_id + "\n")


def _fetch_document_list(api_url: str, token: str, company_code: str = "") -> list:
    """백엔드 API에서 문서 목록 조회"""
    headers = {"X-API-Key": token}
    params = {"size": 100, "page": 0}

    all_docs = []
    while True:
        resp = requests.get(f"{api_url}/api/v1/documents", headers=headers, params=params, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        content = data.get("content", [])
        all_docs.extend(content)

        if data.get("last", True):
            break
        params["page"] += 1

    return all_docs


def _download_file(api_url: str, doc_id: str, token: str) -> bytes:
    """문서 파일 다운로드 (다운로드 URL 조회 후 파일 수신)"""
    import time
    headers = {"X-API-Key": token}
    url_resp = requests.get(f"{api_url}/api/v1/documents/{doc_id}/download", headers=headers, timeout=30)
    url_resp.raise_for_status()
    data = url_resp.json()
    download_url = data["downloadUrl"] if isinstance(data, dict) else data[0]["downloadUrl"]
    # 상대경로(내부 API)는 X-API-Key 헤더 필요, 외부 pre-signed URL은 헤더 불필요
    is_relative = download_url.startswith("/")
    if is_relative:
        download_url = f"{api_url}{download_url}"
    dl_headers = headers if is_relative else {}
    resp = requests.get(download_url, headers=dl_headers, timeout=60)
    resp.raise_for_status()
    return resp.content


def _extract_text(file_bytes: bytes, filename: str) -> str:
    """파일에서 텍스트 추출 (PDF / Markdown / TXT)"""
    ext = os.path.splitext(filename)[-1].lower()

    if ext == ".pdf":
        if not file_bytes.startswith(b"%PDF-"):
            return file_bytes.decode("utf-8", errors="ignore")
        import pypdf
        reader = pypdf.PdfReader(io.BytesIO(file_bytes))
        return "\n".join(page.extract_text() or "" for page in reader.pages)

    if ext in (".md", ".txt"):
        return file_bytes.decode("utf-8", errors="ignore")

    return file_bytes.decode("utf-8", errors="ignore")


def _split_text(text: str, metadata: dict) -> list[Document]:
    """텍스트를 청크로 분할"""
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", ".", " ", ""],
    )
    docs = splitter.create_documents([text], metadatas=[metadata])

    # 청크 앞에 문서 제목 추가
    title = metadata.get("title", "")
    for doc in docs:
        if title and title not in doc.page_content[:80]:
            doc.page_content = f"[{title}]\n{doc.page_content}"

    return docs


def run(api_url: str, token: str, company_code: str = "") -> None:
    print("=" * 50)
    print("  백엔드 API 기반 문서 인덱싱 시작")
    print("=" * 50)

    # 이미 인덱싱된 ID 로드
    indexed_ids = _load_indexed_ids()
    print(f"\n기존 인덱싱된 문서: {len(indexed_ids)}개")

    # 문서 목록 조회
    print(f"\n[1/3] 문서 목록 조회 중... ({api_url})")
    doc_list = _fetch_document_list(api_url, token, company_code)
    print(f"  → 총 {len(doc_list)}개 문서 확인")

    # 신규 문서만 필터링
    new_docs = [d for d in doc_list if str(d["documentId"]) not in indexed_ids]
    print(f"  → 신규 문서: {len(new_docs)}개")

    if not new_docs:
        print("\n인덱싱할 신규 문서가 없습니다.")
        return

    # 인덱싱
    print(f"\n[2/3] 문서 다운로드 및 청킹 중...")
    vs = get_vectorstore()
    splitter_total = 0

    for doc_meta in new_docs:
        doc_id = str(doc_meta["documentId"])
        title = doc_meta.get("title", doc_id)
        original_filename = doc_meta.get("fileName", "")
        ext = os.path.splitext(original_filename)[-1].lower() if original_filename else ".pdf"
        if not ext:
            ext = ".pdf"

        print(f"  처리 중: {title}")

        try:
            file_bytes = _download_file(api_url, doc_id, token)
            filename = f"{title}{ext}"
            text = _extract_text(file_bytes, filename)

            if not text.strip():
                print(f"  ⚠ 텍스트 추출 실패, 건너뜀: {title}")
                continue

            metadata = {
                "source": filename,
                "title": title,
                "doc_id": doc_id,
                "document_type": doc_meta.get("documentType", ""),
                "department": doc_meta.get("department", ""),
            }
            if company_code:
                metadata["company_code"] = company_code

            chunks = _split_text(text, metadata)
            for attempt in range(3):
                try:
                    import time
                    vs.add_documents(chunks)
                    break
                except Exception as embed_err:
                    if "429" in str(embed_err) and attempt < 2:
                        print(f"  ⏳ Rate limit, {10 * (attempt + 1)}초 대기 후 재시도...")
                        time.sleep(10 * (attempt + 1))
                    else:
                        raise
            splitter_total += len(chunks)
            _save_indexed_id(doc_id)
            print(f"  ✓ {title} ({len(chunks)}청크)")
            import time; time.sleep(2)  # RPM 초과 방지

        except Exception as e:
            print(f"  ✗ 오류 ({title}): {e}")
            continue

    print(f"\n[3/3] 완료")
    print(f"  → 총 {splitter_total}개 청크 인덱싱")
    print("\n" + "=" * 50)
    print("  인덱싱 완료!")
    print("=" * 50)


def main() -> None:
    parser = argparse.ArgumentParser(description="백엔드 API 기반 문서 인덱싱")
    parser.add_argument("--api_url", type=str, default=os.getenv("BACKEND_API_URL", "http://localhost:8080"),
                        help="백엔드 API URL")
    parser.add_argument("--token", type=str, default=os.getenv("BACKEND_API_TOKEN", ""),
                        help="백엔드 API 인증 토큰")
    parser.add_argument("--company_code", type=str, default="",
                        help="회사 고유 ID (비워두면 공통 문서로 처리)")
    args = parser.parse_args()

    if not args.token:
        print("오류: --token 또는 BACKEND_API_TOKEN 환경변수가 필요합니다.")
        sys.exit(1)

    run(api_url=args.api_url, token=args.token, company_code=args.company_code)


if __name__ == "__main__":
    main()
