"""
AgentExecutor 기반 RAG 체인 테스트
실행: cd ai/ && venv/Scripts/python scripts/test_agent.py
"""
import os
from dotenv import load_dotenv
load_dotenv()
print("ANTHROPIC_API_KEY:", "있음" if os.getenv("ANTHROPIC_API_KEY") else "없음!!!", flush=True)

from chains.agent_rag_chain import run_agent_rag_chain

TEST_CASES = [
    "연차 신청 방법이 뭐야?",
    "연차 신청 방법이랑 복지카드 사용법 알려줘",
    "IT 장비 신청하려면 어떻게 해?",
    "최저임금이 얼마야?",
]

q = "최저임금이 얼마야?"  # 로컬엔 법률 문서만 있음
print(f"\n질문: {q}")
import traceback
import sys
print("호출 시작...", flush=True)
sys.stdout.flush()
try:
    answer, source, _, _ = run_agent_rag_chain("1", q, company_code="WB0001")
    print(f"툴: {source}")
    print(f"답변: {answer[:200] if answer else '(비어있음)'}")
except BaseException as e:
    traceback.print_exc()
