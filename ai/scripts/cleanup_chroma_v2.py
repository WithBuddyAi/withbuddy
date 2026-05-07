"""
ChromaDB 정리 스크립트 v2
삭제 대상:
  1. document_type == 'TEMPLATE' 청크 (서식지는 RAG 검색 대상 아님)
  2. 구버전 파일명 청크 (신버전으로 교체된 잔존 문서)
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.vectorstore import get_vectorstore

OLD_SOURCES = [
    "HR.txt", "ADMIN.txt", "IT.txt", "WELFARE.txt",
    "근로기준법.txt", "최저임금법.txt", "퇴직급여법.txt",
    "기간제법.txt", "남녀고용평등법.txt",
]

vs = get_vectorstore()
results = vs.get(include=["metadatas"])

template_ids = []
old_ids = []

for doc_id, meta in zip(results["ids"], results["metadatas"]):
    doc_type = meta.get("document_type", "")
    source = os.path.basename(meta.get("source", ""))

    if doc_type == "TEMPLATE":
        template_ids.append(doc_id)
    elif source in OLD_SOURCES:
        old_ids.append(doc_id)

print(f"\n[TEMPLATE 청크] {len(template_ids)}개")
print(f"[구버전 문서 청크] {len(old_ids)}개")
print(f"[총 삭제 대상] {len(template_ids) + len(old_ids)}개")

confirm = input("\n삭제 진행할까요? (y/n): ")
if confirm.lower() != "y":
    print("취소됨.")
    sys.exit(0)

delete_ids = template_ids + old_ids
if delete_ids:
    vs.delete(ids=delete_ids)
    print(f"\n[OK] {len(delete_ids)}개 청크 삭제 완료")
else:
    print("삭제할 항목 없음.")
