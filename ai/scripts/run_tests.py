"""
RAGAS 기반 자동 평가 스크립트
──────────────────────────────────────────────
/chat API에 테스트 질문을 전송하고 RAGAS로 품질을 평가합니다.
Claude(Anthropic)를 평가 LLM으로 사용합니다.

사용법:
    python scripts/run_tests.py
    python scripts/run_tests.py --url https://ai.itsdev.kr --out test_results.md
"""

import argparse
import asyncio
import datetime
import os
import sys
import time

import requests
from dotenv import load_dotenv

load_dotenv()

# ── 테스트 케이스 정의 ──────────────────────────────────────────
TEST_CASES = [
    # ── 법률 관련 ───────────────────────────────────────────────
    {"group": "법률", "no": 1,  "question": "배우자 출산휴가 며칠이야?"},
    {"group": "법률", "no": 2,  "question": "육아휴직 기간이 얼마나 돼?"},
    {"group": "법률", "no": 3,  "question": "난임치료휴가 며칠 줘?"},
    {"group": "법률", "no": 4,  "question": "최저임금 얼마야?", "extra_context": "2026년 기준 최저임금: 시간급 10,030원, 월 환산액 2,096,270원 (월 소정근로시간 209시간 기준, 적용기간 2026.01.01~2026.12.31)"},
    {"group": "법률", "no": 5,  "question": "기간제 근로자 2년 넘으면 어떻게 돼?"},
    {"group": "법률", "no": 6,  "question": "퇴직금 언제 받아?"},

    # ── 회사 문서 관련 ───────────────────────────────────────────
    {"group": "회사문서", "no": 7,  "question": "연차 신청 어떻게 해?"},
    {"group": "회사문서", "no": 8,  "question": "IT 장비 신청하려면 어떻게 해?"},
    {"group": "회사문서", "no": 9,  "question": "경비 처리 방법이 뭐야?"},
    {"group": "회사문서", "no": 10, "question": "재택근무 신청 어떻게 해?"},

    # ── 모호한 질문 ──────────────────────────────────────────────
    {"group": "모호한질문", "no": 11, "question": "연차"},
    {"group": "모호한질문", "no": 12, "question": "휴가"},
    {"group": "모호한질문", "no": 13, "question": "경비"},

    # ── 범위 외 질문 ─────────────────────────────────────────────
    {"group": "범위외", "no": 14, "question": "오늘 점심 뭐 먹지?"},
    {"group": "범위외", "no": 15, "question": "코드 리뷰 어떻게 해?"},

    # ── 담당자 질문 ──────────────────────────────────────────────
    {"group": "담당자", "no": 16, "question": "연차 담당자 누구야?"},
    {"group": "담당자", "no": 17, "question": "IT 장비 담당자 누구야?"},

    # ── 데모 문서 ────────────────────────────────────────────────
    {"group": "데모문서", "no": "D1", "question": "급여일이 언제야?"},
    {"group": "데모문서", "no": "D2", "question": "IT 계정 세팅 어떻게 해?"},
    {"group": "데모문서", "no": "D3", "question": "복지카드 어떻게 써?"},
]


def call_chat_api(url: str, question: str, user_id: int, delay: float) -> dict:
    """채팅 API 호출"""
    try:
        resp = requests.post(
            f"{url}/chat",
            json={"user_id": user_id, "message": question},
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        return {
            "answer": data.get("answer", ""),
            "source": data.get("source", ""),
            "error": None,
        }
    except Exception as e:
        return {"answer": "", "source": "", "error": str(e)}
    finally:
        if delay > 0:
            time.sleep(delay)


def fetch_contexts(question: str, k: int = 3) -> list[str]:
    """벡터스토어에서 관련 문서 청크 가져오기"""
    try:
        import sys, os
        sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        from core.vectorstore import get_vectorstore
        vs = get_vectorstore()
        docs = vs.similarity_search(question, k=k)
        return [doc.page_content for doc in docs] if docs else ["없음"]
    except Exception:
        return ["없음"]


async def evaluate_with_ragas(samples: list) -> dict:
    """RAGAS로 답변 품질 평가 (Claude 사용)"""
    try:
        import pandas as pd
        from ragas import EvaluationDataset, evaluate
        from ragas.run_config import RunConfig
        from ragas.metrics import AnswerRelevancy, Faithfulness, AspectCritic
        from ragas.llms import LangchainLLMWrapper
        from ragas.embeddings import LangchainEmbeddingsWrapper
        from langchain_anthropic import ChatAnthropic
        from langchain_huggingface import HuggingFaceEmbeddings

        llm = LangchainLLMWrapper(
            ChatAnthropic(
                model="claude-haiku-4-5-20251001",
                api_key=os.getenv("ANTHROPIC_API_KEY"),
            )
        )
        embeddings = LangchainEmbeddingsWrapper(
            HuggingFaceEmbeddings(model_name="jhgan/ko-sroberta-multitask")
        )

        # 법령 조항 번호 포함 여부 (법률 그룹만 의미 있음)
        legal_citation = AspectCritic(
            name="legal_citation",
            definition="답변에 '제N조' 형식의 법령 조항 번호가 포함되어 있으면 1, 없으면 0",
            llm=llm,
        )
        polite_tone = AspectCritic(
            name="polite_tone",
            definition="답변이 존댓말(~요, ~습니다 등)로 작성되어 있으면 1, 반말이면 0",
            llm=llm,
        )
        out_of_scope = AspectCritic(
            name="out_of_scope",
            definition="질문이 서비스 범위 밖인 경우 적절히 거절하거나 안내했으면 1, 그냥 엉뚱한 답변을 했으면 0. 서비스 범위 내 질문이면 1로 처리.",
            llm=llm,
        )
        contact_info = AspectCritic(
            name="contact_info",
            definition="답변에 담당자 이름, 팀명 등 연락처 정보가 포함되어 있으면 1, 없으면 0",
            llm=llm,
        )

        metrics = [
            AnswerRelevancy(llm=llm, embeddings=embeddings),
            Faithfulness(llm=llm),
            legal_citation,
            polite_tone,
            out_of_scope,
            contact_info,
        ]

        valid = [s for s in samples if s["answer"]]
        if not valid:
            return {}

        print("  컨텍스트 검색 중...")
        dataset = EvaluationDataset.from_list([
            {
                "user_input": s["question"],
                "response": s["answer"],
                "retrieved_contexts": fetch_contexts(s["question"]) + ([s["extra_context"]] if s.get("extra_context") else []),
            }
            for s in valid
        ])

        # LangSmith 연동
        langsmith_api_key = os.getenv("LANGCHAIN_API_KEY")
        langsmith_project = os.getenv("LANGCHAIN_PROJECT", "withbuddy-ai")
        result = evaluate(
            dataset=dataset,
            metrics=metrics,
            experiment_name=langsmith_project if langsmith_api_key and os.getenv("LANGCHAIN_TRACING_V2") == "true" else None,
            run_config=RunConfig(max_workers=3, timeout=180),
        )
        df = result.to_pandas()

        scores = {}
        for i, s in enumerate(valid):
            key = str(s["no"])
            scores[key] = {
                "answer_relevancy": round(float(df.iloc[i]["answer_relevancy"]), 3) if i < len(df) else None,
                "faithfulness": round(float(df.iloc[i]["faithfulness"]), 3) if i < len(df) else None,
                "legal_citation": int(df.iloc[i]["legal_citation"]) if i < len(df) and not pd.isna(df.iloc[i]["legal_citation"]) else None,
                "polite_tone": int(df.iloc[i]["polite_tone"]) if i < len(df) and not pd.isna(df.iloc[i]["polite_tone"]) else None,
                "out_of_scope": int(df.iloc[i]["out_of_scope"]) if i < len(df) and not pd.isna(df.iloc[i]["out_of_scope"]) else None,
                "contact_info": int(df.iloc[i]["contact_info"]) if i < len(df) and not pd.isna(df.iloc[i]["contact_info"]) else None,
            }
        return scores

    except Exception as e:
        print(f"\n⚠️ RAGAS 평가 오류: {e}")
        return {}


def write_report(results: list, ragas_scores: dict, url: str, out_file: str):
    """마크다운 보고서 작성"""
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    total = len(results)
    error_count = sum(1 for r in results if r["error"])
    relevancy_scores = [v["answer_relevancy"] for v in ragas_scores.values() if v and v["answer_relevancy"] is not None]
    faithfulness_scores = [v["faithfulness"] for v in ragas_scores.values() if v and v["faithfulness"] is not None]
    legal_scores = [v["legal_citation"] for v in ragas_scores.values() if v and v["legal_citation"] is not None]
    polite_scores = [v["polite_tone"] for v in ragas_scores.values() if v and v["polite_tone"] is not None]
    scope_scores = [v["out_of_scope"] for v in ragas_scores.values() if v and v["out_of_scope"] is not None]
    contact_scores = [v["contact_info"] for v in ragas_scores.values() if v and v["contact_info"] is not None]
    avg_relevancy = round(sum(relevancy_scores) / len(relevancy_scores), 3) if relevancy_scores else "-"
    avg_faithfulness = round(sum(faithfulness_scores) / len(faithfulness_scores), 3) if faithfulness_scores else "-"
    legal_pass = sum(1 for s in legal_scores if s == 1)
    polite_pass = sum(1 for s in polite_scores if s == 1)
    scope_pass = sum(1 for s in scope_scores if s == 1)
    contact_pass = sum(1 for s in contact_scores if s == 1)

    # 범위외 그룹만 따로 집계
    oos_nos = {str(r["no"]) for r in results if r["group"] == "범위외"}
    oos_scores = [ragas_scores[k]["out_of_scope"] for k in oos_nos if k in ragas_scores and ragas_scores[k] and ragas_scores[k]["out_of_scope"] is not None]
    oos_pass = sum(1 for s in oos_scores if s == 1)

    lines = [
        "# RAGAS 평가 보고서",
        "",
        f"- **실행 일시:** {now}",
        f"- **서버:** {url}",
        f"- **총 {total}건** — 오류 {error_count}건 / RAGAS 평가 {len(relevancy_scores)}건",
        f"- **평균 Answer Relevancy:** {avg_relevancy}",
        f"- **평균 Faithfulness:** {avg_faithfulness}",
        f"- **경어 사용:** {polite_pass}/{len(polite_scores)}건",
        f"- **응대 적절성:** {scope_pass}/{len(scope_scores)}건 (범위 내 정상 답변 + 범위 외 적절 거절 포함)",
        f"- **범위 외 거절:** {oos_pass}/{len(oos_scores)}건 (범위외 질문 {len(oos_scores)}건 중)",
        f"- **담당자 안내:** {contact_pass}/{len(contact_scores)}건",
        f"- **법령 조항 인용:** {legal_pass}/{len(legal_scores)}건",
        "",
        "> Answer Relevancy: 질문과 답변의 관련성 (0~1)",
        "> Faithfulness: 검색된 문서 기반 답변 여부 — 높을수록 hallucination 적음 (0~1)",
        "> 법령 조항 인용: 법률 질문 답변에 제N조 형식 조항 번호 포함 여부",
        "> 경어 사용: 답변이 존댓말로 작성됐는지",
        "> 응대 적절성: 모든 질문에 대해 적절히 처리됐는지 (범위 내 답변 + 범위 외 거절 포함)",
        "> 범위 외 거절: 서비스 범위 밖 질문만 별도 집계 — 거절 여부 확인용",
        "> 담당자 안내: 관련 담당자 정보를 안내했는지",
        "",
        "---",
        "",
    ]

    current_group = ""
    for r in results:
        if r["group"] != current_group:
            current_group = r["group"]
            lines.append(f"## {current_group}")
            lines.append("")

        score = ragas_scores.get(str(r["no"]))
        relevancy = score["answer_relevancy"] if score else None
        faithfulness = score["faithfulness"] if score else None

        lines.append(f"### [{r['no']}] {r['question']}")
        lines.append("")
        legal_citation = score["legal_citation"] if score else None
        polite_tone = score["polite_tone"] if score else None
        out_of_scope = score["out_of_scope"] if score else None
        contact_info = score["contact_info"] if score else None

        lines.append(f"- **Answer Relevancy:** {relevancy if relevancy is not None else '평가불가'}")
        lines.append(f"- **Faithfulness:** {faithfulness if faithfulness is not None else '평가불가'}")
        lines.append(f"- **경어 사용:** {'✅' if polite_tone == 1 else '❌' if polite_tone == 0 else '평가불가'}")
        lines.append(f"- **범위 외 거절:** {'✅' if out_of_scope == 1 else '❌' if out_of_scope == 0 else '평가불가'}")
        lines.append(f"- **담당자 안내:** {'✅' if contact_info == 1 else '❌' if contact_info == 0 else '평가불가'}")
        if r["group"] == "법률":
            lines.append(f"- **법령 조항 인용:** {'✅' if legal_citation == 1 else '❌' if legal_citation == 0 else '평가불가'}")

        if r["error"]:
            lines.append(f"- **오류:** {r['error']}")
        else:
            lines.append(f"- **출처:** `{r['source']}`")

        lines.append("")
        if r["answer"]:
            answer_escaped = r["answer"].replace("\n", "\n> ")
            lines.append(f"**답변:**")
            lines.append("")
            lines.append(f"> {answer_escaped}")
            lines.append("")

        lines.append("---")
        lines.append("")

    os.makedirs(os.path.dirname(os.path.abspath(out_file)), exist_ok=True)
    md_content = "\n".join(lines)
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(md_content)

    # PDF 변환
    pdf_file = out_file.replace(".md", ".pdf")
    try:
        import markdown as md_lib
        import pdfkit
        html_body = md_lib.markdown(md_content, extensions=["tables", "fenced_code"])
        html_full = f"""<!DOCTYPE html>
<html><head>
<meta charset="utf-8">
<style>
  body {{ font-family: sans-serif; font-size: 13px; padding: 30px; line-height: 1.6; }}
  h1 {{ font-size: 20px; }} h2 {{ font-size: 16px; }} h3 {{ font-size: 14px; }}
  blockquote {{ border-left: 3px solid #ccc; margin: 0; padding-left: 12px; color: #555; }}
  hr {{ border: none; border-top: 1px solid #ddd; }}
  code {{ background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }}
</style>
</head><body>{html_body}</body></html>"""
        pdfkit.from_string(html_full, pdf_file)
        print(f"PDF 저장: {pdf_file}")
    except Exception as e:
        print(f"⚠️ PDF 변환 실패: {e}")

    print(f"\n결과 저장: {out_file}")
    print(f"총 {total}건 — 오류 {error_count}건 / 평균 Relevancy: {avg_relevancy} / 평균 Faithfulness: {avg_faithfulness}")


def main():
    parser = argparse.ArgumentParser(description="RAGAS 기반 자동 평가 스크립트")
    parser.add_argument("--url", default="http://localhost:8000", help="서버 URL")
    parser.add_argument("--user_id", default=9999, type=int, help="테스트용 user_id")
    parser.add_argument("--out", default="", help="결과 파일명 (기본: test_results_날짜.md)")
    parser.add_argument("--delay", default=1.0, type=float, help="요청 간 딜레이(초)")
    args = parser.parse_args()

    today = datetime.datetime.now().strftime("%Y%m%d_%H%M")
    out_file = args.out or f"test_results_{today}.md"

    print(f"서버: {args.url}")
    print(f"결과 파일: {out_file}")
    print(f"테스트 수: {len(TEST_CASES)}\n")

    # 1단계: API 호출
    results = []
    current_group = ""
    for tc in TEST_CASES:
        if tc["group"] != current_group:
            current_group = tc["group"]
            print(f"\n── {current_group} ──")

        print(f"  [{tc['no']}] {tc['question'][:30]}...", end=" ", flush=True)
        result = call_chat_api(args.url, tc["question"], args.user_id, args.delay)
        results.append({**tc, **result})

        if result["error"]:
            print("오류")
        else:
            print("완료")

    # 2단계: RAGAS 평가
    print("\nRAGAS 평가 중...")
    ragas_scores = asyncio.run(evaluate_with_ragas(results))

    # 3단계: 보고서 작성
    write_report(results, ragas_scores, args.url, out_file)


if __name__ == "__main__":
    main()
