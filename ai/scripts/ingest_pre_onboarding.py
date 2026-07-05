"""
PRE 온보딩 문서 수동 인덱싱 스크립트 (SCRUM-572)
────────────────────────────────────────────
BE SCRUM-577(문서 업로드 pre_onboarding_tag 지원) 완료 전까지
임시로 PRE 온보딩 문서를 직접 ChromaDB에 인덱싱합니다.

메타데이터:
    pre_onboarding_tag: true  ← PRE 유저 필터링 핵심
    company_code: techco_001
    document_type: PRE_ONBOARDING_QA

사용법:
    python scripts/ingest_pre_onboarding.py --file scripts/techco_PRE_ONBOARDING_QA_v1_0.md
    python scripts/ingest_pre_onboarding.py --file scripts/techco_PRE_ONBOARDING_QA_v1_0.md --company_code techco_001
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from langchain_core.documents import Document
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter

from core.vectorstore import get_vectorstore, invalidate_bm25_cache, invalidate_search_cache

CHUNK_SIZE = 500
CHUNK_OVERLAP = 50


def _split_markdown(text: str, metadata: dict) -> list[Document]:
    md_splitter = MarkdownHeaderTextSplitter(
        headers_to_split_on=[("#", "h1"), ("##", "h2"), ("###", "h3")],
        strip_headers=False,
    )
    sections = md_splitter.split_text(text)

    char_splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", ".", " ", ""],
    )

    title = metadata.get("title", "")
    docs: list[Document] = []

    for section in sections:
        content = section.page_content.strip()
        if not content:
            continue
        section_meta = {**metadata, **section.metadata}

        if len(content) > CHUNK_SIZE:
            sub_docs = char_splitter.create_documents([content], metadatas=[section_meta])
        else:
            sub_docs = [Document(page_content=content, metadata=section_meta)]

        for doc in sub_docs:
            if title and title not in doc.page_content[:80]:
                doc.page_content = f"[{title}]\n{doc.page_content}"
        docs.extend(sub_docs)

    if not docs:
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=CHUNK_SIZE,
            chunk_overlap=CHUNK_OVERLAP,
            separators=["\n\n", "\n", ".", " ", ""],
        )
        docs = splitter.create_documents([text], metadatas=[metadata])
        for doc in docs:
            if title and title not in doc.page_content[:80]:
                doc.page_content = f"[{title}]\n{doc.page_content}"

    return docs


def run(file_path: str, company_code: str) -> None:
    print("=" * 50)
    print("  PRE 온보딩 문서 인덱싱 시작")
    print("=" * 50)

    if not os.path.exists(file_path):
        print(f"오류: 파일을 찾을 수 없습니다 → {file_path}")
        sys.exit(1)

    filename = os.path.basename(file_path)
    print(f"\n파일: {filename}")
    print(f"company_code: {company_code}")
    print(f"pre_onboarding_tag: true")

    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()

    if not text.strip():
        print("오류: 파일 내용이 비어 있습니다.")
        sys.exit(1)

    metadata = {
        "source": filename,
        "title": "테크 주식회사 PRE 온보딩 Q&A",
        "company_code": company_code,
        "document_type": "PRE_ONBOARDING_QA",
        "pre_onboarding_tag": True,
    }

    chunks = _split_markdown(text, metadata)
    print(f"\n청크 수: {len(chunks)}개")

    vs = get_vectorstore()
    import time
    for i, chunk in enumerate(chunks):
        for attempt in range(3):
            try:
                vs.add_documents([chunk])
                break
            except Exception as e:
                if "429" in str(e) and attempt < 2:
                    print(f"  ⏳ Rate limit, {10 * (attempt + 1)}초 대기...")
                    time.sleep(10 * (attempt + 1))
                else:
                    raise
        time.sleep(0.5)
        print(f"  [{i+1}/{len(chunks)}] 인덱싱 완료")

    invalidate_bm25_cache(company_code)
    invalidate_search_cache(company_code)

    print(f"\n✓ 총 {len(chunks)}청크 인덱싱 완료")
    print("=" * 50)


def main() -> None:
    parser = argparse.ArgumentParser(description="PRE 온보딩 문서 수동 인덱싱")
    parser.add_argument("--file", required=True, help="인덱싱할 MD 파일 경로")
    parser.add_argument("--company_code", default="techco_001", help="회사 코드 (기본값: techco_001)")
    args = parser.parse_args()

    run(file_path=args.file, company_code=args.company_code)


if __name__ == "__main__":
    main()
