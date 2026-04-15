"""
RAG 시스템 성능 평가 스크립트
────────────────────────────────────────────
테스트 질문셋으로 RAG 파이프라인 성능을 측정합니다.

[측정 지표]
  1. Retrieval Hit Rate     - 검색된 문서에 관련 소스가 포함된 비율
  2. Keyword Hit Rate       - 답변에 기대 키워드가 포함된 비율
  3. LLM Judge Score        - Claude가 1~5점으로 평가한 답변 품질
  4. Unanswered Rate        - 미답변 비율
  5. Avg Response Time (ms) - 평균 응답 시간

[사용법]
  cd ai/
  python scripts/evaluate.py
  python scripts/evaluate.py --output ./data/eval_result.json
  python scripts/evaluate.py --chroma_dir C:/withbuddy_chroma_db
"""

import argparse
import json
import os
import sys
import time
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from core.llm import get_llm
from core.vectorstore import get_retriever
from chains.rag_chain import run_rag_chain
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

# ── 미답변 키워드 (rag_chain.py 와 동일) ────────────────────────
_NO_ANSWER_KEYWORDS = [
    "문서에서 확인되지", "관련 정보를 찾을 수 없", "확인되지 않습니다",
    "답변하기 어렵", "안내가 없습니다", "내용이 없습니다", "보유한 문서에는",
    "문서에는", "찾을 수 없습니다", "포함되어 있지 않", "정보가 없",
]

# ── 테스트 질문셋 ─────────────────────────────────────────────
TEST_CASES = [
    # ── 연차·휴가 ──────────────────────────────────────
    {
        "category": "연차/휴가",
        "question": "연차 신청은 어떻게 해?",
        "expected_keywords": ["연차", "신청"],
        "expected_sources": ["연차휴가_신청서", "취업규칙", "인사규정"],
    },
    {
        "category": "연차/휴가",
        "question": "연차는 1년에 며칠이야?",
        "expected_keywords": ["연차", "일"],
        "expected_sources": ["취업규칙", "인사규정"],
    },
    {
        "category": "연차/휴가",
        "question": "반차 신청 방법이 뭐야?",
        "expected_keywords": ["반차"],
        "expected_sources": ["연차휴가_신청서", "취업규칙"],
    },

    # ── 경비·재무 ──────────────────────────────────────
    {
        "category": "경비처리",
        "question": "업무 경비는 어떻게 처리해?",
        "expected_keywords": ["경비", "처리"],
        "expected_sources": ["경비지출_품의서", "여비규정"],
    },
    {
        "category": "경비처리",
        "question": "출장비 신청 방법 알려줘",
        "expected_keywords": ["출장", "신청"],
        "expected_sources": ["여비규정"],
    },

    # ── IT·장비 ──────────────────────────────────────
    {
        "category": "IT장비",
        "question": "노트북 신청하려면 어떻게 해?",
        "expected_keywords": ["장비", "신청", "IT"],
        "expected_sources": ["IT장비_신청서"],
    },
    {
        "category": "IT장비",
        "question": "회사 이메일 계정은 어떻게 발급받아?",
        "expected_keywords": ["계정", "이메일", "IT"],
        "expected_sources": ["IT장비_신청서", "onboarding_guide"],
    },

    # ── 담당자 안내 ───────────────────────────────────
    {
        "category": "담당자",
        "question": "급여 관련해서 누구한테 물어봐야 해?",
        "expected_keywords": ["김지수", "인사팀"],
        "expected_sources": [],  # 담당자 지식은 프롬프트에 내장
    },
    {
        "category": "담당자",
        "question": "법인카드 신청은 누가 담당이야?",
        "expected_keywords": ["박서연", "총무"],
        "expected_sources": [],
    },

    # ── 직장예절 ──────────────────────────────────────
    {
        "category": "직장예절",
        "question": "회의할 때 지켜야 할 예절이 뭐가 있어?",
        "expected_keywords": ["회의", "예절"],
        "expected_sources": ["직장예절_가이드"],
    },
    {
        "category": "직장예절",
        "question": "비즈니스 이메일은 어떻게 써야 해?",
        "expected_keywords": ["이메일", "제목"],
        "expected_sources": ["직장예절_가이드"],
    },
    {
        "category": "직장예절",
        "question": "상사한테 보고할 때 어떻게 해야 해?",
        "expected_keywords": ["보고"],
        "expected_sources": ["직장예절_가이드", "보고서_작성법"],
    },

    # ── 보고서 작성 ───────────────────────────────────
    {
        "category": "문서작성",
        "question": "보고서 작성할 때 기본 구조가 어떻게 돼?",
        "expected_keywords": ["보고서", "구조"],
        "expected_sources": ["보고서_작성법"],
    },

    # ── 복리후생 ──────────────────────────────────────
    {
        "category": "복리후생",
        "question": "회사 복리후생이 어떻게 돼?",
        "expected_keywords": ["복리후생"],
        "expected_sources": ["복리후생규정", "onboarding_guide"],
    },
    {
        "category": "복리후생",
        "question": "건강검진은 어떻게 신청해?",
        "expected_keywords": ["건강검진"],
        "expected_sources": ["복리후생규정"],
    },

    # ── 사무용품 ──────────────────────────────────────
    {
        "category": "사무용품",
        "question": "볼펜이랑 노트 같은 사무용품 어떻게 신청해?",
        "expected_keywords": ["사무용품", "신청"],
        "expected_sources": ["사무용품_신청서"],
    },

    # ── 커뮤니케이션 ──────────────────────────────────
    {
        "category": "커뮤니케이션",
        "question": "직장에서 올바른 호칭은 어떻게 써?",
        "expected_keywords": ["호칭"],
        "expected_sources": ["직장예절_가이드", "직장내_의사소통_가이드"],
    },

    # ── 사규 조항 ─────────────────────────────────────
    {
        "category": "사규조항",
        "question": "취업규칙 제1조가 뭐야?",
        "expected_keywords": ["취업규칙", "제1조"],
        "expected_sources": ["취업규칙"],
    },
    {
        "category": "사규조항",
        "question": "상벌규정에서 징계 종류가 어떻게 돼?",
        "expected_keywords": ["징계"],
        "expected_sources": ["상벌규정"],
    },
]

# ── LLM Judge 프롬프트 ─────────────────────────────────────────
_JUDGE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 RAG 시스템의 답변 품질을 평가하는 전문 평가자입니다.
아래 질문과 답변을 보고 1~5점으로 평가하세요.

[평가 기준]
5점: 질문에 완벽히 답변하고 있으며 정보가 정확하고 구체적임
4점: 질문에 잘 답변하고 있으나 일부 세부 정보가 부족함
3점: 질문에 부분적으로 답변하고 있음
2점: 질문과 관련은 있으나 핵심 답변이 누락됨
1점: 질문에 전혀 답변하지 못하거나 완전히 엉뚱한 답변

반드시 아래 JSON 형식으로만 답변하세요:
{{"score": 숫자, "reason": "한 줄 이유"}}"""),
    ("human", "질문: {question}\n\n답변: {answer}"),
])


def evaluate_retrieval(question: str, expected_sources: list[str], k: int = 5) -> dict:
    """검색된 문서에 기대 소스가 포함되는지 확인합니다."""
    if not expected_sources:
        return {"hit": None, "retrieved_sources": [], "note": "소스 미지정"}

    retriever = get_retriever(k=k)
    docs = retriever.invoke(question)
    retrieved = [
        os.path.splitext(os.path.basename(d.metadata.get("source", "")))[0].replace("+", " ")
        for d in docs
    ]

    hit = any(
        any(exp.replace("_", " ") in r or r in exp.replace("_", " ") for r in retrieved)
        for exp in expected_sources
    )
    return {"hit": hit, "retrieved_sources": retrieved[:5]}


def evaluate_keywords(answer: str, expected_keywords: list[str]) -> dict:
    """답변에 기대 키워드가 포함되는지 확인합니다."""
    hits = [kw for kw in expected_keywords if kw in answer]
    rate = len(hits) / len(expected_keywords) if expected_keywords else 0
    return {"keyword_hit_rate": rate, "hits": hits, "misses": [k for k in expected_keywords if k not in hits]}


def evaluate_with_llm_judge(question: str, answer: str) -> dict:
    """Claude를 이용해 답변 품질을 1~5점으로 평가합니다."""
    try:
        chain = _JUDGE_PROMPT | get_llm() | StrOutputParser()
        result = chain.invoke({"question": question, "answer": answer})
        parsed = json.loads(result.strip())
        return {"score": parsed.get("score", 0), "reason": parsed.get("reason", "")}
    except Exception as e:
        return {"score": 0, "reason": f"평가 실패: {e}"}


def is_unanswered(answer: str) -> bool:
    return any(kw in answer for kw in _NO_ANSWER_KEYWORDS)


def run_evaluation(chroma_dir: str = "C:/withbuddy_chroma_db") -> dict:
    """전체 테스트셋에 대해 평가를 실행합니다."""
    # ChromaDB 경로 임시 패치
    import core.vectorstore as vs
    vs.CHROMA_DB_PATH = chroma_dir
    vs.get_vectorstore.cache_clear()
    vs.get_retriever.cache_clear()

    print("=" * 55)
    print("  With Buddy RAG 시스템 성능 평가")
    print(f"  실행 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  테스트 케이스: {len(TEST_CASES)}개")
    print("=" * 55)

    results = []
    category_stats: dict[str, list] = {}

    for i, tc in enumerate(TEST_CASES, 1):
        q = tc["question"]
        cat = tc["category"]
        print(f"\n[{i:02d}/{len(TEST_CASES)}] [{cat}] {q}")

        # ① RAG 답변 생성 + 시간 측정
        t0 = time.time()
        try:
            answer, source, _ = run_rag_chain("eval_user", q)
        except Exception as e:
            answer, source = f"오류: {e}", ""
        elapsed_ms = int((time.time() - t0) * 1000)

        # ② 검색 평가
        retrieval = evaluate_retrieval(q, tc["expected_sources"])

        # ③ 키워드 평가
        keyword = evaluate_keywords(answer, tc["expected_keywords"])

        # ④ LLM Judge
        judge = evaluate_with_llm_judge(q, answer)

        # ⑤ 미답변 여부
        unanswered = is_unanswered(answer)

        result = {
            "id": i,
            "category": cat,
            "question": q,
            "answer": answer[:200] + "..." if len(answer) > 200 else answer,
            "source": source,
            "retrieval_hit": retrieval["hit"],
            "retrieved_sources": retrieval["retrieved_sources"],
            "keyword_hit_rate": keyword["keyword_hit_rate"],
            "keyword_hits": keyword["hits"],
            "keyword_misses": keyword["misses"],
            "llm_judge_score": judge["score"],
            "llm_judge_reason": judge["reason"],
            "unanswered": unanswered,
            "response_time_ms": elapsed_ms,
        }
        results.append(result)

        if cat not in category_stats:
            category_stats[cat] = []
        category_stats[cat].append(result)

        # 콘솔 출력
        hit_str = "✅" if retrieval["hit"] else ("⬜" if retrieval["hit"] is None else "❌")
        print(f"       검색히트: {hit_str}  키워드: {keyword['keyword_hit_rate']*100:.0f}%  "
              f"Judge: {judge['score']}/5  미답변: {'예' if unanswered else '아니오'}  {elapsed_ms}ms")
        print(f"       이유: {judge['reason']}")

    # ── 전체 통계 ──────────────────────────────────────────────
    answered = [r for r in results if r["retrieval_hit"] is not None]
    retrieval_hit_rate = sum(1 for r in answered if r["retrieval_hit"]) / len(answered) if answered else 0
    avg_keyword_hit = sum(r["keyword_hit_rate"] for r in results) / len(results)
    avg_judge_score = sum(r["llm_judge_score"] for r in results if r["llm_judge_score"] > 0) / max(1, sum(1 for r in results if r["llm_judge_score"] > 0))
    unanswered_rate = sum(1 for r in results if r["unanswered"]) / len(results)
    avg_response_ms = sum(r["response_time_ms"] for r in results) / len(results)

    # 카테고리별 통계
    category_summary = {}
    for cat, cat_results in category_stats.items():
        cat_answered = [r for r in cat_results if r["retrieval_hit"] is not None]
        category_summary[cat] = {
            "count": len(cat_results),
            "retrieval_hit_rate": sum(1 for r in cat_answered if r["retrieval_hit"]) / len(cat_answered) if cat_answered else None,
            "avg_keyword_hit": sum(r["keyword_hit_rate"] for r in cat_results) / len(cat_results),
            "avg_judge_score": sum(r["llm_judge_score"] for r in cat_results) / len(cat_results),
            "unanswered_count": sum(1 for r in cat_results if r["unanswered"]),
        }

    summary = {
        "evaluated_at": datetime.now().isoformat(),
        "total_cases": len(TEST_CASES),
        "chroma_dir": chroma_dir,
        "metrics": {
            "retrieval_hit_rate": round(retrieval_hit_rate, 4),
            "avg_keyword_hit_rate": round(avg_keyword_hit, 4),
            "avg_llm_judge_score": round(avg_judge_score, 4),
            "unanswered_rate": round(unanswered_rate, 4),
            "avg_response_time_ms": round(avg_response_ms, 1),
        },
        "category_summary": category_summary,
        "details": results,
    }

    # ── 결과 출력 ──────────────────────────────────────────────
    print("\n" + "=" * 55)
    print("  📊 평가 결과 요약")
    print("=" * 55)
    print(f"  Retrieval Hit Rate    : {retrieval_hit_rate*100:.1f}%")
    print(f"  Keyword Hit Rate      : {avg_keyword_hit*100:.1f}%")
    print(f"  LLM Judge Score (1~5) : {avg_judge_score:.2f}")
    print(f"  Unanswered Rate       : {unanswered_rate*100:.1f}%")
    print(f"  Avg Response Time     : {avg_response_ms:.0f}ms")
    print()
    print("  📂 카테고리별 Judge Score")
    print("  " + "-" * 40)
    for cat, stat in category_summary.items():
        judge_str = f"{stat['avg_judge_score']:.1f}/5"
        unanswered_str = f"  (미답변 {stat['unanswered_count']}건)" if stat['unanswered_count'] else ""
        print(f"  {cat:<14} {judge_str}{unanswered_str}")
    print("=" * 55)

    return summary


def generate_html(summary: dict, html_path: str) -> None:
    m = summary["metrics"]
    details = summary["details"]
    evaluated_at = summary["evaluated_at"][:16].replace("T", " ")
    cat_summary = summary.get("category_summary", {})

    def score_color(score):
        if score >= 4: return "#22c55e"
        if score >= 3: return "#f59e0b"
        return "#ef4444"

    def pct_bar(rate, good_high=True):
        pct = int((rate or 0) * 100)
        color = "#22c55e" if (pct >= 70) == good_high else "#ef4444"
        return f'<div style="display:flex;align-items:center;gap:6px"><div style="background:#e2e8f0;border-radius:4px;width:80px;height:8px"><div style="background:{color};width:{pct}%;height:8px;border-radius:4px"></div></div><span>{pct}%</span></div>'

    metric_cards = f"""
    <div class="card"><div class="num" style="color:#6366f1">{m['retrieval_hit_rate']*100:.0f}%</div><div class="label">Retrieval Hit Rate</div></div>
    <div class="card"><div class="num" style="color:#0ea5e9">{m['avg_keyword_hit_rate']*100:.0f}%</div><div class="label">Keyword Hit Rate</div></div>
    <div class="card"><div class="num" style="color:{score_color(m['avg_llm_judge_score'])}">{m['avg_llm_judge_score']:.2f}<span style="font-size:14px">/5</span></div><div class="label">LLM Judge Score</div></div>
    <div class="card"><div class="num" style="color:{'#ef4444' if m['unanswered_rate']>0.1 else '#22c55e'}">{m['unanswered_rate']*100:.0f}%</div><div class="label">Unanswered Rate</div></div>
    <div class="card"><div class="num" style="color:#64748b">{m['avg_response_time_ms']:.0f}<span style="font-size:14px">ms</span></div><div class="label">Avg Response Time</div></div>
    """

    cat_rows = ""
    for cat, stat in cat_summary.items():
        judge = stat.get("avg_judge_score", 0)
        cat_rows += f"""<tr>
          <td>{cat}</td>
          <td>{pct_bar(stat.get('retrieval_hit_rate') or 0)}</td>
          <td>{pct_bar(stat.get('avg_keyword_hit'))}</td>
          <td style="color:{score_color(judge)};font-weight:bold">{judge:.1f}/5</td>
          <td style="color:{'#ef4444' if stat['unanswered_count']>0 else '#22c55e'}">{stat['unanswered_count']}건</td>
        </tr>"""

    detail_rows = ""
    for r in details:
        judge = r.get("llm_judge_score", 0)
        kw = r.get("keyword_hit_rate", 0)
        unanswered = r.get("unanswered", False)
        retrieval = r.get("retrieval_hit")
        hit_icon = "✅" if retrieval else ("⬜" if retrieval is None else "❌")
        bg = "#fef2f2" if unanswered or judge <= 2 else ("#fffbeb" if judge == 3 else "white")
        detail_rows += f"""<tr style="background:{bg}">
          <td style="text-align:center">{r['id']}</td>
          <td style="color:#64748b">{r['category']}</td>
          <td>{r['question']}</td>
          <td style="text-align:center">{hit_icon}</td>
          <td>{pct_bar(kw)}</td>
          <td style="text-align:center;color:{score_color(judge)};font-weight:bold">{judge}/5</td>
          <td style="color:#64748b;font-size:11px">{r.get('llm_judge_reason','')}</td>
          <td style="color:{'#ef4444' if unanswered else '#22c55e'};text-align:center">{'예' if unanswered else '아니오'}</td>
        </tr>"""

    html = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>WithBuddy RAG 평가 결과</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{ font-family: 'Malgun Gothic', sans-serif; font-size: 12px; margin: 24px 28px; color: #1e293b; }}
  h1 {{ font-size: 18px; margin: 0 0 4px; }}
  h2 {{ font-size: 14px; margin: 28px 0 12px; border-bottom: 2px solid #1e293b; padding-bottom: 4px; }}
  .meta {{ color: #64748b; margin-bottom: 16px; font-size: 11px; }}
  .summary {{ display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }}
  .card {{ padding: 12px 20px; border-radius: 8px; text-align: center; min-width: 100px; background: #f8fafc; border: 1px solid #e2e8f0; }}
  .card .num {{ font-size: 26px; font-weight: bold; }}
  .card .label {{ font-size: 11px; color: #64748b; margin-top: 2px; }}
  table {{ border-collapse: collapse; width: 100%; margin-bottom: 8px; }}
  th {{ background: #334155; color: white; padding: 7px 10px; text-align: left; font-size: 11px; }}
  td {{ padding: 6px 10px; border-bottom: 1px solid #e2e8f0; vertical-align: middle; font-size: 11px; line-height: 1.5; }}
  @media print {{ body {{ margin: 10px 14px; }} tr {{ page-break-inside: avoid; }} }}
</style>
</head>
<body>
<h1>WithBuddy RAG 시스템 성능 평가</h1>
<div class="meta">평가 일시: {evaluated_at} &nbsp;|&nbsp; 총 {summary['total_cases']}문항</div>

<h2>전체 지표</h2>
<div class="summary">{metric_cards}</div>

<h2>카테고리별 요약</h2>
<table>
  <tr><th>카테고리</th><th>Retrieval Hit</th><th>Keyword Hit</th><th>Judge Score</th><th>미답변</th></tr>
  {cat_rows}
</table>

<h2>문항별 상세</h2>
<table>
  <tr><th>#</th><th>카테고리</th><th>질문</th><th>검색히트</th><th>키워드</th><th>Judge</th><th>판정이유</th><th>미답변</th></tr>
  {detail_rows}
</table>
</body>
</html>"""

    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)


def main():
    parser = argparse.ArgumentParser(description="RAG 시스템 성능 평가")
    parser.add_argument("--output", type=str, default="./data/eval_result.json",
                        help="결과 저장 경로 (기본: ./data/eval_result.json)")
    parser.add_argument("--chroma_dir", type=str, default="C:/withbuddy_chroma_db",
                        help="ChromaDB 경로 (기본: C:/withbuddy_chroma_db)")
    args = parser.parse_args()

    summary = run_evaluation(chroma_dir=args.chroma_dir)

    os.makedirs(os.path.dirname(args.output), exist_ok=True)

    # ── 단일 결과 저장 ──────────────────────────────────────────
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"\n  💾 상세 결과 저장: {args.output}")

    # ── 히스토리 누적 저장 (버전별 비교용) ──────────────────────
    history_path = os.path.join(os.path.dirname(args.output), "eval_history.json")
    history = []
    if os.path.exists(history_path):
        with open(history_path, encoding="utf-8") as f:
            try:
                history = json.load(f)
            except Exception:
                history = []

    history_entry = {
        "evaluated_at": summary["evaluated_at"],
        "total_cases": summary["total_cases"],
        **summary["metrics"],
    }
    history.append(history_entry)

    with open(history_path, "w", encoding="utf-8") as f:
        json.dump(history, f, ensure_ascii=False, indent=2)

    # ── 이전 결과와 비교 출력 ────────────────────────────────────
    if len(history) >= 2:
        prev = history[-2]
        curr = history[-1]
        print("\n  📈 이전 평가 대비 변화")
        print("  " + "-" * 40)
        for key, label in [
            ("retrieval_hit_rate",    "Retrieval Hit Rate"),
            ("avg_keyword_hit_rate",  "Keyword Hit Rate  "),
            ("avg_llm_judge_score",   "LLM Judge Score   "),
            ("unanswered_rate",       "Unanswered Rate   "),
            ("avg_response_time_ms",  "Avg Response Time "),
        ]:
            p, c = prev.get(key, 0), curr.get(key, 0)
            diff = c - p
            if key in ("unanswered_rate", "avg_response_time_ms"):
                arrow = "✅" if diff < 0 else ("❌" if diff > 0 else "➖")
            else:
                arrow = "✅" if diff > 0 else ("❌" if diff < 0 else "➖")
            unit = "ms" if "ms" in key else ("%")
            if unit == "%":
                print(f"  {label}: {p*100:.1f}% → {c*100:.1f}% ({arrow} {diff*100:+.1f}%p)")
            else:
                print(f"  {label}: {p:.0f}ms → {c:.0f}ms ({arrow} {diff:+.0f}ms)")

    print(f"\n  📋 누적 평가 횟수: {len(history)}회  (history: {history_path})")
    print("=" * 55)

    html_path = args.output.replace(".json", ".html")
    generate_html(summary, html_path)
    print(f"  🌐 HTML 리포트: {html_path}")
    print(f"  → 브라우저에서 열고 Ctrl+P → PDF로 저장하세요")


if __name__ == "__main__":
    main()
