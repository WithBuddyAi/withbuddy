"""
LangSmith에서 messageType별 건수 집계
실행: python ai/scripts/count_message_types.py [--days 3] [--project withbuddy-ai]
"""

import argparse
import os
from datetime import datetime, timedelta, timezone
from dotenv import load_dotenv

load_dotenv()

_NO_RESULT_KW = [
    "문서에서 확인되지", "관련 정보를 찾을 수 없", "확인되지 않습니다", "답변하기 어렵",
    "안내가 없습니다", "내용이 없습니다", "보유한 문서에는", "문서에는",
    "찾을 수 없습니다", "찾을 수 없었어요", "찾을 수 없어요", "찾을 수 없어",
    "포함되어 있지 않", "정보가 없", "찾지 못했어요",
    "알 수 없어요", "알 수 없습니다", "확인이 어렵", "파악이 어렵",
    "문서에 없어서", "안내드리기 어려워",
    "문서에 없네요", "문서에 없어요",
]
_OUT_OF_SCOPE_KW = ["서비스 범위", "담당 사수님과 직접"]


def classify(output: str) -> str:
    if any(kw in output for kw in _OUT_OF_SCOPE_KW):
        return "out_of_scope"
    if any(kw in output for kw in _NO_RESULT_KW):
        return "no_result"
    return "rag_answer"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--days", type=int, default=3, help="최근 N일 (기본 3)")
    parser.add_argument("--project", default="withbuddy-ai", help="LangSmith 프로젝트명")
    args = parser.parse_args()

    try:
        from langsmith import Client
    except ImportError:
        print("langsmith 패키지가 없습니다: pip install langsmith")
        return

    api_key = os.getenv("LANGCHAIN_API_KEY")
    if not api_key:
        print("LANGCHAIN_API_KEY 환경변수가 없습니다.")
        return

    client = Client(api_key=api_key)

    since = datetime.now(timezone.utc) - timedelta(days=args.days)
    print(f"\n조회 기간: 최근 {args.days}일 ({since.strftime('%Y-%m-%d %H:%M')} UTC ~)")
    print(f"프로젝트: {args.project}")
    print("=" * 50)

    counts = {"rag_answer": 0, "no_result": 0, "out_of_scope": 0}
    total = 0

    runs = client.list_runs(
        project_name=args.project,
        run_type="chain",
        filter='eq(name, "withbuddy-rag")',
        start_time=since,
    )

    for run in runs:
        total += 1
        output = ""
        if run.outputs:
            output = str(run.outputs.get("output", ""))
        msg_type = classify(output)
        counts[msg_type] += 1

    if total == 0:
        print("해당 기간에 조회된 run이 없습니다.")
        print("--days 값을 늘려보거나 프로젝트명을 확인해주세요.")
        return

    print(f"전체 RAG 호출: {total}건\n")
    for k, v in counts.items():
        pct = v / total * 100 if total else 0
        print(f"  {k:<15}: {v:>4}건  ({pct:.1f}%)")

    print("\n※ out_of_scope 중 intent 필터(오케스트레이터)에서 걸러진 건은 LangSmith에 기록되지 않아")
    print("  실제 out_of_scope 비율은 이보다 높을 수 있습니다.")


if __name__ == "__main__":
    main()
