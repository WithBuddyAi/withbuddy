"""
Prompt Caching Before/After 토큰 측정 스크립트
────────────────────────────────────────────
Before: Prompt Caching 적용 전 실행 → token_measurement_before.json
After : Prompt Caching 적용 후 실행 → token_measurement_after.json

실행:
  python scripts/measure_tokens.py before
  python scripts/measure_tokens.py after
"""

import json
import os
import sys
from datetime import datetime, date as _date

os.environ["LANGCHAIN_TRACING_V2"] = "false"  # LangSmith 트레이싱 비활성화

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from core.llm import get_llm
from core.vectorstore import search_with_company_fallback
from utils.prompts import RAG_PROMPT_CACHED
from chains.retriever import get_hr_contact as _get_hr_contact, get_it_contact as _get_it_contact, get_company_specific_rules as _get_company_specific_rules

COMPANY_CODE = "WB0001"
COMPANY_NAME = "테크주식회사"

# test_inscope.py 50문항에서 유형별로 고른 10문항
TEST_QUESTIONS = [
    "연차는 언제부터 생겨요?",
    "수습 기간이 얼마나 돼요?",
    "경비 정산은 어떻게 해요?",
    "코어타임이 뭐예요?",
    "야근 식대 얼마예요?",
    "연차 신청은 어떻게 해요?",
    "급여일이 언제예요?",
    "회의실 예약은 어디서 해요?",
    "4대보험은 따로 신청해야 하나요?",
    "수습 평가는 어떻게 진행되나요?",
]


def measure(phase: str):
    print(f"\n{'='*50}")
    print(f"  Prompt Caching {phase.upper()} 측정")
    print(f"  {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*50}\n")

    llm = get_llm()
    test_chain = RAG_PROMPT_CACHED | llm  # StrOutputParser 제외 → usage_metadata 접근

    hr_team, _ = _get_hr_contact(COMPANY_CODE)
    it_contact = _get_it_contact(COMPANY_CODE)
    company_specific_rules = _get_company_specific_rules(COMPANY_CODE)
    today_date = _date.today().strftime("%Y년 %m월 %d일")

    results = []
    total_input = 0
    total_output = 0
    total_cache_read = 0
    total_cache_write = 0

    for i, question in enumerate(TEST_QUESTIONS):
        print(f"[{i+1}/{len(TEST_QUESTIONS)}] {question}")

        docs = search_with_company_fallback(question, k=3, company_code=COMPANY_CODE)
        context = "\n\n".join([doc.page_content[:400] for doc in docs])

        response = test_chain.invoke({
            "company_name": COMPANY_NAME,
            "user_style": "",
            "context": context,
            "chat_history": [],
            "question": question,
            "hr_team": hr_team,
            "it_contact": it_contact,
            "company_specific_rules": company_specific_rules,
            "today_date": today_date,
            "hire_info": "",
            "user_name": "",
        })

        usage = response.usage_metadata or {}
        input_tokens  = usage.get("input_tokens", 0)
        output_tokens = usage.get("output_tokens", 0)

        # LangChain은 cache 필드를 input_token_details 하위에 넣음
        token_details = usage.get("input_token_details") or {}
        cache_read  = token_details.get("cache_read", 0)
        cache_write = token_details.get("cache_creation", 0)

        total_input       += input_tokens
        total_output      += output_tokens
        total_cache_read  += cache_read
        total_cache_write += cache_write

        print(f"  input: {input_tokens}, output: {output_tokens}", end="")
        if cache_read:
            print(f", cache_read: {cache_read}", end="")
        if cache_write:
            print(f", cache_write: {cache_write}", end="")
        print()

        results.append({
            "question": question,
            "input_tokens": input_tokens,
            "output_tokens": output_tokens,
            "cache_read_input_tokens": cache_read,
            "cache_creation_input_tokens": cache_write,
        })

    n = len(TEST_QUESTIONS)
    print(f"\n{'='*50}")
    print(f"총   input  tokens : {total_input:,}")
    print(f"총   output tokens : {total_output:,}")
    if total_cache_read:
        print(f"총   cache_read    : {total_cache_read:,}")
    if total_cache_write:
        print(f"총   cache_write   : {total_cache_write:,}")
    print(f"평균 input  / 질문 : {total_input // n:,}")
    print(f"평균 output / 질문 : {total_output // n:,}")

    output = {
        "phase": phase,
        "measured_at": datetime.now().isoformat(),
        "questions": n,
        "total_input_tokens": total_input,
        "total_output_tokens": total_output,
        "total_cache_read_tokens": total_cache_read,
        "total_cache_write_tokens": total_cache_write,
        "avg_input_tokens": total_input // n,
        "avg_output_tokens": total_output // n,
        "details": results,
    }

    out_path = f"scripts/token_measurement_{phase}.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장 → {out_path}")


if __name__ == "__main__":
    phase = sys.argv[1] if len(sys.argv) > 1 else "before"
    if phase not in ("before", "after"):
        print("사용법: python scripts/measure_tokens.py before|after")
        sys.exit(1)
    measure(phase)
