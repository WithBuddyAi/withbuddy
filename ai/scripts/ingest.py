"""
문서 수집 및 벡터 저장소 구축 스크립트
────────────────────────────────────────────
회사 문서(txt, md, pdf, docx)를 로드하고 청크로 분할한 뒤
ChromaDB에 임베딩하여 저장합니다.
서버 실행 전에 반드시 한 번 실행해야 합니다.

사용법:
    # 기본 경로(./docs)에서 문서 수집
    python scripts/ingest.py

    # 경로 직접 지정
    python scripts/ingest.py --docs_dir ./my_docs --chroma_dir ./chroma_db
"""

import argparse
import os
import sys

# 프로젝트 루트(ai/)를 Python 경로에 추가
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_chroma import Chroma
from langchain_community.document_loaders import (
    DirectoryLoader,
    PyPDFLoader,
    TextLoader,
)
from langchain_core.documents import Document

from core.embeddings import get_embeddings

load_dotenv()

# ── 청킹 설정 ──────────────────────────────
CHUNK_SIZE = 500        # 청크 최대 글자 수
CHUNK_OVERLAP = 50      # 청크 간 겹치는 글자 수 (문맥 보존)

# ChromaDB 컬렉션 이름 (vectorstore.py와 동일해야 함)
COLLECTION_NAME = "onboarding_docs"


# ── 문서 로드 ──────────────────────────────

def load_documents(docs_dir: str) -> list[Document]:
    """
    지정된 디렉토리에서 지원 형식의 문서를 모두 로드합니다.
    지원 형식: .txt, .md, .pdf

    Args:
        docs_dir: 문서가 저장된 디렉토리 경로

    Returns:
        list[Document]: 로드된 LangChain Document 목록
    """
    documents = []

    # 텍스트/마크다운 파일 로드
    for glob_pattern in ("**/*.txt", "**/*.md"):
        try:
            loader = DirectoryLoader(
                docs_dir,
                glob=glob_pattern,
                loader_cls=TextLoader,
                loader_kwargs={"encoding": "utf-8"},
                silent_errors=True,
            )
            docs = loader.load()
            documents.extend(docs)
            print(f"  [{glob_pattern}] {len(docs)}개 로드")
        except Exception as e:
            print(f"  [{glob_pattern}] 로드 오류: {e}")

    # PDF 파일 로드
    try:
        pdf_loader = DirectoryLoader(
            docs_dir,
            glob="**/*.pdf",
            loader_cls=PyPDFLoader,
            silent_errors=True,
        )
        pdf_docs = pdf_loader.load()
        documents.extend(pdf_docs)
        print(f"  [**/*.pdf] {len(pdf_docs)}개 로드")
    except Exception as e:
        print(f"  [**/*.pdf] 로드 오류: {e}")

    return documents


# ── 문서 청킹 ──────────────────────────────

def split_documents(documents: list[Document]) -> list[Document]:
    """
    로드된 문서를 지정된 크기의 청크로 분할합니다.
    한국어 특성을 고려해 문단·줄·문장 순으로 분할합니다.

    Args:
        documents: 원본 Document 리스트

    Returns:
        list[Document]: 청킹된 Document 리스트
    """
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", ".", " ", ""],  # 한국어 우선 분리 기준
    )
    chunks = splitter.split_documents(documents)

    # 모든 청크 앞에 문서 제목을 붙입니다.
    # 이렇게 하면 "취업규칙 제10조" 검색 시 취업규칙.pdf 청크가 상위에 노출됩니다.
    for chunk in chunks:
        source = chunk.metadata.get("source", "")
        # 경로에서 파일명만 추출하고 확장자 제거, '+' → 공백
        doc_title = os.path.splitext(os.path.basename(source))[0].replace("+", " ").strip()
        # 청크 앞 50자에 이미 제목이 있으면 중복 추가 안 함
        if doc_title and doc_title not in chunk.page_content[:80]:
            chunk.page_content = f"[{doc_title}]\n{chunk.page_content}"

    return chunks


# ── ChromaDB 저장 ──────────────────────────

def ingest_to_chroma(
    chunks: list[Document],
    chroma_dir: str = "./chroma_db",
) -> Chroma:
    """
    청크를 임베딩하여 ChromaDB에 저장합니다.
    기존 데이터가 있으면 새 문서를 추가합니다.

    Args:
        chunks: 임베딩할 Document 청크 리스트
        chroma_dir: ChromaDB 저장 경로

    Returns:
        Chroma: 저장된 ChromaDB 인스턴스
    """
    embeddings = get_embeddings()

    vectorstore = Chroma.from_documents(
        documents=chunks,
        embedding=embeddings,
        persist_directory=chroma_dir,
        collection_name=COLLECTION_NAME,
    )

    return vectorstore


# ── 샘플 문서 생성 ─────────────────────────

def create_sample_docs(docs_dir: str) -> None:
    """
    docs 디렉토리가 비어있을 때 테스트용 샘플 문서를 생성합니다.

    Args:
        docs_dir: 샘플 문서를 저장할 디렉토리
    """
    sample_content = """# 회사 온보딩 가이드

## 연차 신청 방법
연차는 사내 HR 시스템(hr.company.com)에서 신청할 수 있습니다.

1. HR 시스템 로그인
2. 근태관리 > 연차신청 메뉴 선택
3. 날짜 및 사유 입력 후 제출
4. 팀장 승인 후 확정

연차는 입사일로부터 1년 기준 15일이 부여됩니다.
반차(0.5일) 단위로도 신청 가능합니다.

## IT 장비 신청
IT 장비가 필요한 경우 IT팀에 문의하세요.
- 담당자: 이민준 (it@company.com, 내선 1234)
- 신청 방법: 사내 포털 > IT 서비스 > 장비 신청
- 처리 기간: 3~5 영업일

## 경비 처리 방법
업무 관련 경비는 재무팀을 통해 처리합니다.
1. 영수증 보관
2. 경비 처리 시스템 입력 (finance.company.com)
3. 팀장 승인
4. 재무팀 확인 후 익월 급여일 지급

경비 처리 문의: 최준혁 (finance@company.com, 내선 3456)

## 사내 시스템 계정 안내
- 이메일: Microsoft Outlook
- 협업 도구: Microsoft Teams
- 문서 관리: SharePoint
- HR 시스템: hr.company.com
- 재무 시스템: finance.company.com

계정 관련 문의: IT팀 이민준 (내선 1234)

## 복리후생 안내
- 점심 식대: 월 10만원 지급
- 교육비: 연 100만원 자기계발비 지원
- 건강검진: 연 1회 지원
- 경조사 지원: 사내 규정에 따라 지원

문의: 인사팀 김지수 (hr@company.com, 내선 2345)

## 사무용품 신청
사무용품이 필요한 경우 총무팀에 문의하세요.
- 담당자: 박서연 (admin@company.com, 내선 4567)
- 신청 방법: 사내 포털 > 총무 서비스 > 용품 신청

## 계약서 검토 요청
계약서 검토가 필요한 경우 법무팀에 문의하세요.
- 담당자: 김철수 (legal@company.com, 내선 5678)
- 검토 기간: 5~7 영업일 (계약서 복잡도에 따라 상이)
"""

    os.makedirs(docs_dir, exist_ok=True)
    sample_path = os.path.join(docs_dir, "onboarding_guide.md")
    with open(sample_path, "w", encoding="utf-8") as f:
        f.write(sample_content)
    print(f"\n샘플 문서 생성 완료: {sample_path}")


# ── 메인 실행 ──────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="회사 문서를 ChromaDB에 수집합니다."
    )
    parser.add_argument(
        "--docs_dir",
        type=str,
        default="./docs",
        help="문서 디렉토리 경로 (기본값: ./docs)",
    )
    parser.add_argument(
        "--chroma_dir",
        type=str,
        default="./chroma_db",
        help="ChromaDB 저장 경로 (기본값: ./chroma_db)",
    )
    parser.add_argument(
        "--company_id",
        type=str,
        default="",
        help="회사 고유 ID (다중 테넌트 격리용, 예: company_A)",
    )
    args = parser.parse_args()

    print("=" * 50)
    print("  문서 수집 시작")
    print("=" * 50)

    # docs 디렉토리가 없거나 비어있으면 샘플 문서 생성
    if not os.path.exists(args.docs_dir) or not os.listdir(args.docs_dir):
        print(f"\n'{args.docs_dir}' 디렉토리가 없거나 비어 있습니다.")
        print("테스트용 샘플 문서를 생성합니다.")
        create_sample_docs(args.docs_dir)

    # 1단계: 문서 로드
    print(f"\n[1/3] 문서 로드 중... ({args.docs_dir})")
    documents = load_documents(args.docs_dir)
    print(f"  → 총 {len(documents)}개 문서 로드 완료")

    if not documents:
        print("\n로드된 문서가 없습니다. docs 폴더에 문서를 추가해주세요.")
        sys.exit(1)

    # 2단계: 청킹
    print(f"\n[2/3] 문서 청킹 중... (chunk_size={CHUNK_SIZE})")
    chunks = split_documents(documents)
    print(f"  → 총 {len(chunks)}개 청크 생성")

    # company_id가 지정된 경우 각 청크 메타데이터에 추가
    if args.company_id:
        for chunk in chunks:
            chunk.metadata["company_id"] = args.company_id
        print(f"  → company_id='{args.company_id}' 메타데이터 적용")

    # 3단계: ChromaDB 저장
    print(f"\n[3/3] ChromaDB에 저장 중... ({args.chroma_dir})")
    ingest_to_chroma(chunks, args.chroma_dir)
    print("  → 저장 완료")

    print("\n" + "=" * 50)
    print("  문서 수집 완료! 이제 서버를 실행할 수 있습니다.")
    print("  uvicorn main:app --reload")
    print("=" * 50)


if __name__ == "__main__":
    main()
