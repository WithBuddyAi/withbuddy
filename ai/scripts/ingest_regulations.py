"""
사규 PDF 수집 및 벡터DB 적재 스크립트
────────────────────────────────────────────
사규 공개 게시판에서 사규 목록(메타데이터)을 크롤링하고,
PDF는 수동 다운로드 후 로컬 폴더에서 처리하여
기존 ChromaDB(onboarding_docs)에 추가합니다.

★ 기존 ingest.py, vectorstore.py, embeddings.py 는 수정하지 않습니다.
★ 기존 ChromaDB 컬렉션("onboarding_docs")에 사규 문서를 추가하는 방식입니다.
★ 기존 RAG 검색 흐름(rag_chain.py)은 변경 없이 사규 문서도 함께 검색됩니다.

⚠️ 외부 IP에서 PDF 직접 다운로드가 차단될 수 있습니다.
   아래 순서로 사용하세요:

[사용 방법]
  1단계: 사규 목록만 먼저 크롤링하여 CSV로 저장
      python scripts/ingest_regulations.py --list_only --save_csv ./data/regulations_list.csv

  2단계: 저장된 CSV의 URL로 수동으로 PDF를 다운로드 (브라우저 사용)
      - ./data/pdfs/ 폴더에 저장
      - 파일명은 자유롭게 지정 (어떤 이름도 가능)

  3단계: 다운로드된 PDF 폴더를 지정하여 적재 실행
      python scripts/ingest_regulations.py --pdf_dir ./data/pdfs --meta_csv ./data/regulations_list.csv

  [또는] PDF를 폴더에만 넣고 메타데이터 없이 실행 (개정일자 없이 적재)
      python scripts/ingest_regulations.py --pdf_dir ./data/pdfs
"""

import argparse
import csv
import os
import re
import sys
import time

import requests

# 프로젝트 루트(ai/)를 Python 경로에 추가 — 기존 ingest.py와 동일한 방식
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
from langchain_chroma import Chroma
from langchain_core.documents import Document

# 기존 embeddings.py의 get_embeddings() 함수를 재사용 (한국어 임베딩 모델)
from core.embeddings import get_embeddings

load_dotenv()

# ──────────────────────────────────────────────────────────────────
# 크롤링 대상 설정
# ──────────────────────────────────────────────────────────────────
BASE_URL    = "https://www.company.com"
BOARD_URL   = f"{BASE_URL}/board?mid=regulations"
TOTAL_PAGES = 5   # 총 5페이지, 49건

# ChromaDB 컬렉션 이름 — vectorstore.py, ingest.py와 반드시 동일해야 합니다
COLLECTION_NAME = "onboarding_docs"

# 조항 패턴: "제1조", "제12조의2", "제1조 (목적)", "제1조【목적】" 등 인식
ARTICLE_PATTERN = re.compile(
    r'(제\s*\d+\s*조(?:의\s*\d+)?'
    r'(?:\s*[(\[【]\s*[^)\]】\n]{1,30}\s*[)\]】])?)'
)

# 웹 요청 헤더
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "ko-KR,ko;q=0.9",
    "Referer": BOARD_URL,
}


# ── 사규 게시판 크롤링 (메타데이터만) ────────────────────────────

def scrape_kdn_regulations() -> list[dict]:
    """
    사규 공개 게시판(5페이지)을 크롤링하여
    사규 메타데이터 목록을 반환합니다.
    (PDF 다운로드는 IP 차단으로 불가 → 메타데이터만 수집)

    Returns:
        list[dict]: {사규명, 개정일자, 다운로드URL} 딕셔너리 목록
    """
    from bs4 import BeautifulSoup

    regulations = []

    for page in range(1, TOTAL_PAGES + 1):
        url = f"{BOARD_URL}&nPage={page}"
        print(f"  페이지 {page}/{TOTAL_PAGES} 크롤링 중...")

        try:
            resp = requests.get(url, headers=HEADERS, timeout=30)
            resp.raise_for_status()
        except Exception as e:
            print(f"  [오류] 페이지 {page} 로드 실패: {e}")
            continue

        soup = BeautifulSoup(resp.text, "html.parser")

        for row in soup.select("table tr"):
            cells = row.find_all("td")
            if len(cells) < 4:
                continue

            # 사규명 (두 번째 셀)
            doc_name = cells[1].get_text(strip=True)
            if not doc_name:
                continue

            # 개정일자 (네 번째 셀)
            revision_date = cells[3].get_text(strip=True)

            # 다운로드 URL 추출
            download_url = _extract_clean_download_url(row)

            regulations.append({
                "사규명":     doc_name,
                "개정일자":   revision_date,
                "다운로드URL": download_url or "",
            })

        time.sleep(0.5)

    print(f"  → 총 {len(regulations)}개 사규 메타데이터 수집 완료")
    return regulations


def _extract_clean_download_url(row) -> str | None:
    """
    테이블 행에서 PDF 다운로드 URL을 추출합니다.
    href 속성만 사용하고 onclick은 무시하여 중복 파라미터를 방지합니다.

    Args:
        row: BeautifulSoup <tr> 태그

    Returns:
        str | None: 완성된 다운로드 URL 또는 None
    """
    for a_tag in row.find_all("a", href=True):
        href = a_tag["href"]
        if "download" in href:
            # 상대 경로 → 절대 URL
            if href.startswith("/"):
                return BASE_URL + href
            if href.startswith("http"):
                return href

    return None


# ── CSV 저장/로드 ──────────────────────────────────────────────────

def save_regulations_csv(regulations: list[dict], csv_path: str) -> None:
    """
    수집한 사규 메타데이터를 CSV 파일로 저장합니다.
    사용자가 이 파일을 참고하여 PDF를 수동 다운로드할 수 있습니다.

    Args:
        regulations: 사규 메타데이터 목록
        csv_path: 저장할 CSV 파일 경로
    """
    os.makedirs(os.path.dirname(csv_path) if os.path.dirname(csv_path) else ".", exist_ok=True)

    with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["사규명", "개정일자", "다운로드URL"])
        writer.writeheader()
        writer.writerows(regulations)

    print(f"  → CSV 저장 완료: {csv_path}")
    print(f"  이 파일의 '다운로드URL' 컬럼 링크로 브라우저에서 PDF를 다운로드하세요.")


def load_regulations_csv(csv_path: str) -> list[dict]:
    """
    저장된 사규 메타데이터 CSV를 로드합니다.

    Args:
        csv_path: CSV 파일 경로

    Returns:
        list[dict]: 사규 메타데이터 목록
    """
    regulations = []
    with open(csv_path, encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            regulations.append(row)
    print(f"  → CSV에서 {len(regulations)}개 메타데이터 로드")
    return regulations


# ── PDF 텍스트 추출 ────────────────────────────────────────────────

def extract_text_from_pdf(pdf_path: str) -> str:
    """
    PDF 파일에서 전체 텍스트를 추출합니다.
    기존 requirements.txt에 이미 포함된 pypdf 라이브러리를 사용합니다.

    Args:
        pdf_path: PDF 파일 경로

    Returns:
        str: 추출된 텍스트 (전체 페이지 합산)
    """
    from pypdf import PdfReader

    reader = PdfReader(pdf_path)
    text = ""
    for page in reader.pages:
        page_text = page.extract_text()
        if page_text:
            text += page_text + "\n"
    return text


# ── 조항 단위 분할 ─────────────────────────────────────────────────

def split_by_articles(
    text: str,
    doc_name: str,
    revision_date: str,
) -> list[Document]:
    """
    추출된 텍스트를 조항(제N조) 단위로 분할하여 Document 목록으로 반환합니다.

    각 Document의 metadata (기존 RAG 방식 그대로):
        source        : 사규명 (기존 _extract_sources()가 이 키로 출처를 표시)
        article_number: 조항번호 (예: "제1조")
        article_title : 조항제목 (예: "목적")
        revision_date : 개정일자
        doc_type      : "사규" (일반 온보딩 문서와 구분용)

    Args:
        text: PDF에서 추출한 전체 텍스트
        doc_name: 사규명
        revision_date: 개정일자

    Returns:
        list[Document]: 조항 단위로 분할된 LangChain Document 목록
    """
    documents = []
    matches = list(ARTICLE_PATTERN.finditer(text))

    if not matches:
        # 조항 구조가 없으면 전체 텍스트를 하나의 Document로 처리
        print(f"    [경고] 조항 패턴(제N조)을 찾지 못했습니다. 전체를 하나의 문서로 처리합니다.")
        return [
            Document(
                page_content=text.strip(),
                metadata={
                    "source":         doc_name,
                    "article_number": "",
                    "article_title":  "",
                    "revision_date":  revision_date,
                    "doc_type":       "사규",
                },
            )
        ]

    for i, match in enumerate(matches):
        start = match.start()
        end   = matches[i + 1].start() if i + 1 < len(matches) else len(text)

        article_text = text[start:end].strip()
        if not article_text:
            continue

        article_header = match.group(0)

        # 조항번호 추출 (공백 제거)
        number_match   = re.search(r'제\s*\d+\s*조(?:의\s*\d+)?', article_header)
        article_number = number_match.group(0).replace(" ", "") if number_match else article_header

        # 괄호 안 제목 추출
        title_match   = re.search(r'[(\[【]\s*([^)\]】\n]{1,30})\s*[)\]】]', article_header)
        article_title = title_match.group(1).strip() if title_match else ""

        documents.append(
            Document(
                page_content=article_text,
                metadata={
                    # "source" 키는 기존 rag_chain.py의 _extract_sources()에서 출처 표시에 사용
                    "source":         doc_name,
                    "article_number": article_number,
                    "article_title":  article_title,
                    "revision_date":  revision_date,
                    "doc_type":       "사규",
                },
            )
        )

    return documents


# ── ChromaDB 추가 저장 ─────────────────────────────────────────────

def add_to_chroma(chunks: list[Document], chroma_dir: str = "./chroma_db") -> None:
    """
    Document 청크를 임베딩하여 기존 ChromaDB 컬렉션에 추가합니다.
    기존 ingest.py의 ingest_to_chroma()와 동일한 방식입니다.

    Args:
        chunks: 저장할 Document 청크 목록
        chroma_dir: ChromaDB 저장 경로 (기본값: ./chroma_db)
    """
    embeddings = get_embeddings()

    Chroma.from_documents(
        documents=chunks,
        embedding=embeddings,
        persist_directory=chroma_dir,
        collection_name=COLLECTION_NAME,  # 기존 컬렉션에 추가 적재
    )


# ── PDF 폴더 처리 ──────────────────────────────────────────────────

def process_pdf_folder(
    pdf_dir: str,
    meta_csv: str | None,
    chroma_dir: str,
) -> None:
    """
    로컬 PDF 폴더를 처리하여 ChromaDB에 적재합니다.

    메타데이터 CSV가 있으면 사규명과 개정일자를 CSV에서 가져오고,
    없으면 파일명을 사규명으로 사용합니다.

    Args:
        pdf_dir: PDF 파일이 저장된 폴더 경로
        meta_csv: 사규 메타데이터 CSV 경로 (없으면 None)
        chroma_dir: ChromaDB 저장 경로
    """
    # PDF 파일 목록 (중복 파일 제외: 파일명에 "(1)", "(2)" 등이 포함된 경우 건너뜀)
    import re as _re
    pdf_files = sorted([
        f for f in os.listdir(pdf_dir)
        if f.lower().endswith(".pdf")
        and not _re.search(r'\(\d+\)\.pdf$', f)   # "(1).pdf" 패턴 중복 파일 제외
    ])

    if not pdf_files:
        print(f"\n[오류] '{pdf_dir}' 폴더에 PDF 파일이 없습니다.")
        sys.exit(1)

    print(f"  → {len(pdf_files)}개 PDF 파일 발견")

    # 메타데이터 CSV 로드 (있으면)
    meta_map = {}  # {사규명: 개정일자} 매핑
    if meta_csv and os.path.exists(meta_csv):
        regulations = load_regulations_csv(meta_csv)
        for reg in regulations:
            meta_map[reg.get("사규명", "").strip()] = reg.get("개정일자", "").strip()
        print(f"  메타데이터 CSV 로드 완료 ({len(meta_map)}건 매핑)")
    else:
        print(f"  메타데이터 CSV 없음 → 파일명을 사규명으로 사용합니다.")

    all_documents = []

    print(f"\n[3/4] PDF 텍스트 추출 및 조항 분할 중...")

    for idx, filename in enumerate(pdf_files, start=1):
        pdf_path  = os.path.join(pdf_dir, filename)
        # 파일명에서 확장자 제거 → 사규명 후보
        # '+' 를 공백으로 변환 (브라우저 다운로드 시 URL 인코딩된 공백)
        file_stem = os.path.splitext(filename)[0].strip().replace("+", " ")

        # 메타데이터 CSV에서 일치하는 사규명 탐색
        # 정확 매칭 → 부분 매칭 순서로 시도
        doc_name      = file_stem
        revision_date = ""

        if meta_map:
            # 1) 정확 매칭
            if file_stem in meta_map:
                doc_name      = file_stem
                revision_date = meta_map[file_stem]
            else:
                # 2) 파일명이 사규명에 포함되거나, 사규명이 파일명에 포함되는 경우
                for reg_name, reg_date in meta_map.items():
                    if reg_name in file_stem or file_stem in reg_name:
                        doc_name      = reg_name
                        revision_date = reg_date
                        break

        print(f"\n  [{idx}/{len(pdf_files)}] {doc_name}" +
              (f" (개정: {revision_date})" if revision_date else ""))

        # 텍스트 추출
        try:
            text = extract_text_from_pdf(pdf_path)
            if not text.strip():
                print(f"    [경고] 텍스트가 비어있습니다. (스캔 PDF이거나 암호화된 파일)")
                continue
            print(f"    텍스트 추출 완료 ({len(text):,}자)")
        except Exception as e:
            print(f"    [오류] 텍스트 추출 실패: {e}")
            continue

        # 조항 단위 분할
        docs = split_by_articles(text, doc_name, revision_date)
        print(f"    조항 분할 완료 → {len(docs)}개 청크")

        all_documents.extend(docs)

    print(f"\n[3/4] 분할 결과: 총 {len(all_documents)}개 Document")

    if not all_documents:
        print("\n[오류] 처리된 문서가 없습니다.")
        sys.exit(1)

    # ChromaDB 적재
    print(f"\n[4/4] ChromaDB에 적재 중... ({chroma_dir})")
    print(f"  컬렉션: {COLLECTION_NAME}  (기존 컬렉션에 추가)")

    add_to_chroma(all_documents, chroma_dir)
    print(f"  → 적재 완료")


# ── 메인 실행 ──────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="사규 PDF를 ChromaDB(onboarding_docs)에 추가합니다.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # [1단계] 사규 목록 크롤링 후 CSV 저장
  python scripts/ingest_regulations.py --list_only --save_csv ./data/regulations_list.csv

  # [2단계] 브라우저로 PDF 다운로드 후 ./data/pdfs/ 에 저장

  # [3단계] PDF 폴더와 메타데이터 CSV를 함께 지정하여 적재
  python scripts/ingest_regulations.py --pdf_dir ./data/pdfs --meta_csv ./data/regulations_list.csv

  # 메타데이터 없이 PDF만 있을 때
  python scripts/ingest_regulations.py --pdf_dir ./data/pdfs
        """,
    )
    parser.add_argument(
        "--list_only",
        action="store_true",
        help="사규 목록만 크롤링하고 CSV로 저장합니다. PDF 적재는 하지 않습니다.",
    )
    parser.add_argument(
        "--save_csv",
        type=str,
        default="./data/regulations_list.csv",
        help="크롤링한 사규 목록을 저장할 CSV 파일 경로 (기본: ./data/regulations_list.csv)",
    )
    parser.add_argument(
        "--pdf_dir",
        type=str,
        default=None,
        help="수동으로 다운로드한 PDF가 있는 폴더 경로",
    )
    parser.add_argument(
        "--meta_csv",
        type=str,
        default=None,
        help="--list_only 로 저장한 사규 목록 CSV (개정일자 매핑용, 선택사항)",
    )
    parser.add_argument(
        "--chroma_dir",
        type=str,
        default="./chroma_db",
        help="ChromaDB 저장 경로 (기본값: ./chroma_db)",
    )
    args = parser.parse_args()

    print("=" * 55)
    print("  사규 PDF → ChromaDB 적재")
    print("=" * 55)

    # ── 모드 1: 목록만 크롤링 ────────────────────────────────────
    if args.list_only:
        print(f"\n[1/1] 사규 목록 크롤링 중...")
        regulations = scrape_kdn_regulations()

        if not regulations:
            print("\n[오류] 수집된 사규 데이터가 없습니다.")
            sys.exit(1)

        save_regulations_csv(regulations, args.save_csv)

        print("\n" + "=" * 55)
        print("  사규 목록 저장 완료!")
        print(f"  저장 경로: {args.save_csv}")
        print()
        print("  ▶ 다음 단계:")
        print(f"    1. CSV 파일의 '다운로드URL' 컬럼 링크로 브라우저에서 PDF 다운로드")
        print(f"    2. PDF를 ./data/pdfs/ 폴더에 저장")
        print(f"    3. 아래 명령어 실행:")
        print(f"       python scripts/ingest_regulations.py \\")
        print(f"         --pdf_dir ./data/pdfs \\")
        print(f"         --meta_csv {args.save_csv}")
        print("=" * 55)
        return

    # ── 모드 2: PDF 폴더 처리 ────────────────────────────────────
    if args.pdf_dir:
        if not os.path.exists(args.pdf_dir):
            print(f"\n[오류] PDF 폴더를 찾을 수 없습니다: {args.pdf_dir}")
            sys.exit(1)

        print(f"\n[1/4] PDF 폴더 확인... ({args.pdf_dir})")
        print(f"[2/4] 메타데이터 CSV 확인... ({args.meta_csv or '없음'})")

        process_pdf_folder(args.pdf_dir, args.meta_csv, args.chroma_dir)

        print("\n" + "=" * 55)
        print("  사규 문서 적재 완료!")
        print("  이제 기존 RAG 검색에서 사규 문서도 함께 검색됩니다.")
        print("  서버 재시작 없이 바로 적용됩니다.")
        print("=" * 55)
        return

    # ── 인수 없이 실행 시 안내 ────────────────────────────────────
    print()
    print("⚠️  외부 IP에서 PDF 직접 다운로드가 차단될 수 있습니다.")
    print("    아래 순서를 따라주세요:")
    print()
    print("  [1단계] 사규 목록 크롤링:")
    print("    python scripts/ingest_regulations.py --list_only")
    print()
    print("  [2단계] 출력된 CSV의 URL을 브라우저에서 열어 PDF 다운로드")
    print("    → ./data/pdfs/ 폴더에 저장")
    print()
    print("  [3단계] 적재 실행:")
    print("    python scripts/ingest_regulations.py \\")
    print("      --pdf_dir ./data/pdfs \\")
    print("      --meta_csv ./data/regulations_list.csv")
    print()


if __name__ == "__main__":
    main()
