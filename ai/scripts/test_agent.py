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
    # ── 단일 질문 10개 ──────────────────────────────
    "연차 신청 방법이 뭐야?",
    "복지카드 사용법 알려줘",
    "노트북 신청하려면 어떻게 해?",
    "출산휴가는 며칠이야?",
    "재택근무 신청 방법이 뭐야?",
    "병가는 어떻게 신청해?",
    "명함 신청은 어떻게 해?",
    "건강검진은 어떻게 신청해?",
    "볼펜이랑 노트 같은 사무용품 어떻게 신청해?",
    "퇴직금은 어떻게 받아?",
    # ── 복합 질문 5개 ──────────────────────────────
    "연차 신청 방법이랑 복지카드 사용법 알려줘",
    "출산휴가랑 육아휴직 차이가 뭐야?",
    "법인카드 한도랑 경비처리 방법 알려줘",
    "노트북 신청이랑 이메일 계정 발급 방법 알려줘",
    "재택근무 신청이랑 병가 신청 방법 알려줘",
    # ── 법률 질문 2개 ──────────────────────────────
    "최저임금이 얼마야?",
    "퇴직금 법적 기준이 뭐야?",
    # ── 범위 외 질문 3개 ──────────────────────────
    "오늘 날씨 어때?",
    "내 이름이 뭐야?",
    "지금 몇시야?",
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
