"""
ChromaDB에서 불필요한 법률 문서 청크 제거
유지: 근로기준법, 최저임금법, 남녀고용평등법, 근로자퇴직급여보장법
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.vectorstore import get_vectorstore

# 유지할 법률 파일 키워드 (파일명 일부)
KEEP_KEYWORDS = [
    "근로기준법",
    "최저임금법",
    "남녀고용평등",
    "근로자퇴직급여",
]

vs = get_vectorstore()
results = vs.get(include=["metadatas"])

delete_ids = []
keep_ids = []
keep_sources = set()
delete_sources = set()

for doc_id, meta in zip(results["ids"], results["metadatas"]):
    src = meta.get("source", "")

    # 법률 폴더 문서인지 확인 (제XXX호 패턴)
    import re
    if not re.search(r"제\d+호", src):
        continue  # 법률 파일 아님 → 건드리지 않음

    is_keep = any(kw in src for kw in KEEP_KEYWORDS)
    if is_keep:
        keep_ids.append(doc_id)
        keep_sources.add(src)
    else:
        delete_ids.append(doc_id)
        delete_sources.add(src)

print(f"\n[유지] {len(keep_ids)}개 청크")
for s in sorted(keep_sources):
    print(f"  [keep] {os.path.basename(s)}")

print(f"\n[삭제] {len(delete_ids)}개 청크")
for s in sorted(delete_sources):
    print(f"  [del] {os.path.basename(s)}")

confirm = input("\n삭제 진행할까요? (y/n): ")
if confirm.lower() != "y":
    print("취소됨.")
    sys.exit(0)

# 삭제 실행
if delete_ids:
    vs.delete(ids=delete_ids)
    print(f"\n[OK] {len(delete_ids)}개 청크 삭제 완료")
else:
    print("삭제할 항목 없음.")
