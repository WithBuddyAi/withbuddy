"""
리랭킹 ON/OFF 비교용 경량 평가 스크립트
────────────────────────────────────────────
문서 기반 답변 20개만 테스트 (토큰 절감)

[사용법]
  cd ai/
  PYTHONPATH=. python scripts/evaluate_reranking.py --company_code WB0001 --chroma_dir ./chroma_db
"""

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from core.vectorstore import get_retriever
from chains.rag_chain import run_rag_chain

TEST_CASES = [
    # HR (5개)
    {"category": "HR", "question": "수습 기간이 얼마나 돼요? 그동안 급여는 어떻게 되나요?", "expected_keywords": ["3개월", "100%"]},
    {"category": "HR", "question": "연차는 입사하고 언제부터 생겨요?", "expected_keywords": ["1개월", "1일"]},
    {"category": "HR", "question": "오전 반차 쓰면 몇 시에 출근해야 해요?", "expected_keywords": ["2시"]},
    {"category": "HR", "question": "재택근무는 어떻게 신청해요? 일주일에 몇 번까지 돼요?", "expected_keywords": ["2회", "팀장"]},
    {"category": "HR", "question": "야근하면 수당이 나오나요? 계산법이 궁금해요.", "expected_keywords": ["150%", "팀장"]},

    # ADMIN (5개)
    {"category": "ADMIN", "question": "비품 신청은 어떻게 해요? A4 용지는 어디 있어요?", "expected_keywords": ["탕비실", "Notion"]},
    {"category": "ADMIN", "question": "출장비는 어떻게 정산해요?", "expected_keywords": ["Notion", "5영업일", "실비"]},
    {"category": "ADMIN", "question": "법인카드 쓰고 나서 뭘 해야 해요?", "expected_keywords": ["3영업일", "Notion"]},
    {"category": "ADMIN", "question": "회의실은 어디서 예약해요?", "expected_keywords": ["Google Calendar"]},
    {"category": "ADMIN", "question": "야근 식대랑 회식비는 뭐가 달라요?", "expected_keywords": ["1만원", "실비"]},

    # WELFARE (5개)
    {"category": "WELFARE", "question": "복지카드 한도가 얼마예요? 식대도 포함이에요?", "expected_keywords": ["20만원", "10만원"]},
    {"category": "WELFARE", "question": "건강검진은 언제부터 받을 수 있어요? 어떻게 신청해요?", "expected_keywords": ["3개월", "10만원"]},
    {"category": "WELFARE", "question": "도서 구입비 지원이 있나요? 어떻게 신청해요?", "expected_keywords": ["3만원", "Notion"]},
    {"category": "WELFARE", "question": "생일에 뭔가 특별한 게 있나요?", "expected_keywords": ["2시", "조기 퇴근"]},
    {"category": "WELFARE", "question": "온라인 강의 수강하면 지원받을 수 있나요?", "expected_keywords": ["30만원", "3개월"]},

    # IT (5개)
    {"category": "IT", "question": "재택근무나 외부에서 접속할 때 VPN은 어떻게 연결하나요?", "expected_keywords": ["VPN"]},
    {"category": "IT", "question": "비밀번호는 얼마나 자주 바꿔야 해요?", "expected_keywords": ["90일", "10자"]},
    {"category": "IT", "question": "Notion 권한은 어떻게 받아요?", "expected_keywords": ["1영업일"]},
    {"category": "IT", "question": "업무에 필요한 소프트웨어 설치하고 싶은데 어떻게 해요?", "expected_keywords": ["#help-it"]},
    {"category": "IT", "question": "입사 첫날 노트북 세팅은 어떻게 해요?", "expected_keywords": ["Google", "MFA"]},
]


def evaluate_keywords(answer: str, expected_keywords: list) -> float:
    hits = [kw for kw in expected_keywords if kw in answer]
    return len(hits) / len(expected_keywords) if expected_keywords else 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--company_code", type=str, default="WB0001")
    parser.add_argument("--chroma_dir", type=str, default="./chroma_db")
    args = parser.parse_args()

    import core.vectorstore as vs
    vs.CHROMA_DB_PATH = args.chroma_dir
    vs.get_vectorstore.cache_clear()
    vs.get_retriever.cache_clear()

    from core.vectorstore import RERANKING_ENABLED
    status = "ON" if RERANKING_ENABLED else "OFF"

    print("=" * 55)
    print(f"  리랭킹 {status} — 경량 평가 ({len(TEST_CASES)}개)")
    print("=" * 55)

    total_keyword = 0
    total_time = 0
    category_stats = {}

    for i, tc in enumerate(TEST_CASES, 1):
        q = tc["question"]
        cat = tc["category"]

        t0 = time.time()
        try:
            answer, _, _, _ = run_rag_chain("eval_user", q, company_code=args.company_code)
        except Exception as e:
            answer = f"오류: {e}"
        elapsed_ms = int((time.time() - t0) * 1000)

        kw_rate = evaluate_keywords(answer, tc["expected_keywords"])
        total_keyword += kw_rate
        total_time += elapsed_ms

        if cat not in category_stats:
            category_stats[cat] = {"kw": [], "time": []}
        category_stats[cat]["kw"].append(kw_rate)
        category_stats[cat]["time"].append(elapsed_ms)

        print(f"[{i:02d}] [{cat}] {q[:40]}...")
        print(f"     키워드: {kw_rate*100:.0f}%  응답: {elapsed_ms}ms")

    avg_kw = total_keyword / len(TEST_CASES)
    avg_time = total_time / len(TEST_CASES)

    print("\n" + "=" * 55)
    print(f"  리랭킹 {status} 결과")
    print("=" * 55)
    print(f"  Keyword Hit Rate  : {avg_kw*100:.1f}%")
    print(f"  Avg Response Time : {avg_time:.0f}ms")
    print()
    for cat, stat in category_stats.items():
        print(f"  {cat:<10} 키워드: {sum(stat['kw'])/len(stat['kw'])*100:.0f}%  평균: {sum(stat['time'])//len(stat['time'])}ms")
    print("=" * 55)


if __name__ == "__main__":
    main()
