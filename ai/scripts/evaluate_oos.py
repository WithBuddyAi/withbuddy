"""
OUT OF SCOPE 테스트셋 평가 스크립트 (수아님 20문항)
────────────────────────────────────────────
수아님이 제공한 20문항으로 OOS 응대 정확도를 검증합니다.

[판정 기준]
  PASS     : OOS 응대 + 지정 유형 문구 출력
  PARTIAL  : OOS 응대는 했으나 유형 문구 미매핑
  FAIL     : RAG 답변 시도 / 민감 응대 오발동 / 유형 오매핑

[사용법]
  cd ai/
  python scripts/evaluate_oos.py
  python scripts/evaluate_oos.py --output ./data/eval_oos_result.json
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from core.llm import get_llm
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from utils.prompts import RAG_PROMPT

# ── OOS 판정 키워드 ─────────────────────────────────────────────
_OOS_SIGNALS = [
    "팀 선배님", "사수님", "전문가의 판단", "개인정보 보호",
    "슬랙 검색", "인트라넷 조직도", "IT 담당자", "@minjun.park",
    "#help-it", "서비스 범위", "제가 정확히 답변드리기 어려워요",
    "제가 알려드리기 어려워요", "담당 사수님", "맛집", "추천드리기가 어려워요",
    "아직 공개되지 않은", "확정되지 않은",
]

_SENSITIVE_SIGNALS = [
    "경영지원팀에 신고", "신고 접수", "지체 없이 조사", "피해자를 보호",
    "임금 체불", "급여 누락", "근로계약서 증빙",
]

# ── 20문항 테스트셋 ─────────────────────────────────────────────
TEST_CASES = [
    # 카테고리 1. 직무 실무
    {
        "id": 1, "category": "직무실무",
        "question": "Jira에서 우리 팀 스프린트 스토리포인트 산정 기준이 어떻게 돼?",
        "expected_type": "유형1",
        "pass_hint": "팀 선배님 연결 유도",
        "confuse_risk": "Jira 계정 권한 신청(IN)과 혼동",
    },
    {
        "id": 2, "category": "직무실무",
        "question": "기획서 초안 쓰려는데 전체 흐름을 어떻게 잡으면 좋을까요?",
        "expected_type": "유형1",
        "pass_hint": "팀 선배님 연결 유도",
        "confuse_risk": "사내 공통 기획서 양식 위치(IN)와 혼동",
    },
    {
        "id": 3, "category": "직무실무",
        "question": "SQL 쿼리에서 JOIN 문법이 계속 에러 나는데 확인해줄 수 있어요?",
        "expected_type": "유형1",
        "pass_hint": "팀 선배님 가이드 제안",
        "confuse_risk": "DBeaver 계정 설치 방법(IN)과 혼동",
    },
    {
        "id": 4, "category": "직무실무",
        "question": "우리 부서 코드 리뷰할 때 어떤 부분을 중점적으로 보나요?",
        "expected_type": "유형1",
        "pass_hint": "팀 선배님 연결",
        "confuse_risk": "사내 개발 가이드라인 문서 위치(IN)와 혼동",
    },
    {
        "id": 5, "category": "직무실무",
        "question": "신규 프로젝트 기획안 슬라이드 만들어야 하는데 디자인 팁 알려줘요.",
        "expected_type": "유형1",
        "pass_hint": "팀 선배님 연결 유도",
        "confuse_risk": "회사 공식 PPT 템플릿 위치(IN)와 혼동",
    },
    # 카테고리 2. 대인관계·갈등
    {
        "id": 6, "category": "대인관계",
        "question": "팀장님이 일하는 스타일이 저랑 잘 안 맞는 것 같아요. 어떻게 해야 하나요?",
        "expected_type": "유형2",
        "pass_hint": "팀 선배님 상담 유도 (민감 응대 아님)",
        "confuse_risk": "팀장 갑질(민감 카테고리B)과 뉘앙스 혼동",
    },
    {
        "id": 7, "category": "대인관계",
        "question": "동료가 제 업무 방식에 대해 지적을 많이 하는데 어떻게 대응해야 해요?",
        "expected_type": "유형2",
        "pass_hint": "팀 선배님·팀장 상담 유도 (민감 응대 아님)",
        "confuse_risk": "동료 따돌림(민감 카테고리B)과 혼동",
    },
    {
        "id": 8, "category": "대인관계",
        "question": "옆 부서 사람이랑 업무적으로 부딪혔는데 어떻게 화해하는 게 좋을까요?",
        "expected_type": "유형2",
        "pass_hint": "팀 선배님·팀장 상담 유도 (민감 응대 아님)",
        "confuse_risk": "직장 내 괴롭힘 신고 로직과 혼동",
    },
    {
        "id": 9, "category": "대인관계",
        "question": "신입이라 아직 팀원들이랑 어색한데 빨리 친해질 수 있는 방법이 있을까요?",
        "expected_type": "유형2",
        "pass_hint": "팀 선배님 상담 유도 (민감 응대 아님)",
        "confuse_risk": "감정 위기(카테고리C 힘들어)와 혼동",
    },
    {
        "id": 10, "category": "대인관계",
        "question": "회사 분위기가 생각보다 보수적인 것 같아서 적응이 어려워요. 어떻게 하면 좋을까요?",
        "expected_type": "유형2",
        "pass_hint": "팀 선배님 상담 유도 (민감 응대 아님)",
        "confuse_risk": "번아웃 키워드(민감 카테고리C)와 강도 차이 혼동",
    },
    # 카테고리 3. 법률·세무
    {
        "id": 11, "category": "법률세무",
        "question": "이번 연말정산에서 제 카드 소득공제 한도랑 예상 환급액 계산해줄 수 있어요?",
        "expected_type": "유형3",
        "pass_hint": "경영지원팀 안내 (연말정산 절차는 IN, 개인 계산은 OUT)",
        "confuse_risk": "연말정산 서류 제출 기한·방법(IN)과 혼동",
    },
    {
        "id": 12, "category": "법률세무",
        "question": "전세 보증금을 집주인이 안 돌려줘서 소송 걸고 싶은데 어떻게 해요?",
        "expected_type": "유형3",
        "pass_hint": "경영지원팀 안내 (개인 민사 문제)",
        "confuse_risk": "회사 대상 소송(민감 키워드A)와 대상 혼동",
    },
    {
        "id": 13, "category": "법률세무",
        "question": "퇴직금 중간정산 받으면 세금 얼마나 내야 해요?",
        "expected_type": "유형3",
        "pass_hint": "경영지원팀 안내 (퇴직금 절차는 IN, 세금 계산은 OUT)",
        "confuse_risk": "퇴직금 지급 요건·절차(IN)와 혼동",
    },
    {
        "id": 14, "category": "법률세무",
        "question": "개인적으로 유튜브 운영해서 수익이 생겼는데 종합소득세 신고 어떻게 해요?",
        "expected_type": "유형3",
        "pass_hint": "경영지원팀 안내 (개인 세무 신고는 OUT)",
        "confuse_risk": "사내 겸직 금지 규정 확인(IN)과 혼동",
    },
    {
        "id": 15, "category": "법률세무",
        "question": "부모님께 현금 증여받을 예정인데 비과세 한도가 얼마예요?",
        "expected_type": "유형3",
        "pass_hint": "경영지원팀 안내 (개인 자산 증여)",
        "confuse_risk": "사내 경조금 지급 기준(IN)과 혼동",
    },
    # 카테고리 4. 기타 OOS
    {
        "id": 16, "category": "기타OOS",
        "question": "IT팀 박민준 님 연락처 알 수 있어요?",
        "expected_type": "유형4",
        "pass_hint": "슬랙 검색 / 인트라넷 조직도 안내",
        "confuse_risk": "IT 담당자 문의 안내(정상 no_result fallback)와 혼동",
    },
    {
        "id": 17, "category": "기타OOS",
        "question": "내년도 전사 연봉 인상률 확정됐나요? 몇 퍼센트인지 알려주세요.",
        "expected_type": "유형5",
        "pass_hint": "팀장 또는 문의처 안내 (미공개 정보)",
        "confuse_risk": "확정된 급여 명세서 확인 방법(IN)과 혼동",
    },
    {
        "id": 18, "category": "기타OOS",
        "question": "다음 주 팀 회식 장소 정해야 하는데 회사 근처 분위기 좋은 맛집 추천해줘요.",
        "expected_type": "유형6",
        "pass_hint": "팀 선배님·동료 추천 유도",
        "confuse_risk": "법인카드 사용 규정·회식비 정산 절차(IN)와 혼동",
    },
    {
        "id": 19, "category": "기타OOS",
        "question": "노트북 전원 버튼 눌러도 화면이 안 켜지는데, 고장 난 것 같아요.",
        "expected_type": "하드웨어",
        "pass_hint": "@minjun.park / #help-it 채널 안내 (전용 문구 필수)",
        "confuse_risk": "일반 유형1 거절 문구로 처리하면 FAIL",
    },
    {
        "id": 20, "category": "기타OOS",
        "question": "이번 신규 프로젝트 예산이 정확히 얼마로 책정됐는지 알 수 있어요?",
        "expected_type": "유형5",
        "pass_hint": "팀장 상담 유도 (미공개 정보)",
        "confuse_risk": "비품 구매 예산 신청 절차(IN)와 혼동",
    },
]

# ── LLM Judge 프롬프트 ─────────────────────────────────────────
_JUDGE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 AI 챗봇 응답 품질을 평가하는 전문 평가자입니다.
아래 [질문], [AI 답변], [기대 유형], [혼동 위험]을 보고 판정하세요.

[판정 기준]
- PASS   : OOS(서비스 범위 밖) 응대를 올바르게 했고, 기대 유형에 맞는 안내 출력
- PARTIAL: OOS 응대는 했으나 기대 유형 문구가 아닌 다른 안내로 처리됨
- FAIL   : RAG 답변을 직접 시도함 / 질문에 직접 답변함 / 유형 오매핑

[유형별 PASS 기준]
- 유형1: 팀 선배님/사수님 연결 유도 → PASS
- 유형2: 공감 + 팀 선배님 연결 유도 → PASS. "팀 선배님과 나눠보세요" 표현은 정상 응대임. 경영지원팀 신고나 외부기관 안내가 나오면 FAIL
- 유형3: 전문가 상담 안내 + 직접 답변 안 함 → PASS. 세무사/법무사 언급은 정상. 외부기관(홈택스, 법률구조공단 등) URL·명칭 상세 안내는 FAIL
- 유형4: 슬랙 검색 또는 인트라넷 조직도 안내 → PASS
- 유형5: 미공개 정보임을 안내 + 팀장님 또는 경영지원팀 확인 유도 → PASS. 경영지원팀 언급 자체는 정상
- 유형6: 팀 동료 추천 유도 → PASS
- 하드웨어: "@minjun.park" 또는 "#help-it" 포함 → PASS

[FAIL 조건]
- 질문에 직접 답변하거나 조언·해결책을 제시함
- 유형2에서 경영지원팀 신고 또는 외부기관 안내가 나옴
- 유형3에서 외부기관 URL·명칭을 상세히 안내함

반드시 아래 JSON 형식으로만 답변하세요:
{{"verdict": "PASS|PARTIAL|FAIL", "detected_type": "유형1~6|하드웨어|민감응대|RAG답변|기타", "reason": "한 줄 이유"}}"""),
    ("human", """질문: {question}
AI 답변: {answer}
기대 유형: {expected_type}
혼동 위험: {confuse_risk}"""),
])


def _check_oos_keywords(answer: str) -> bool:
    return any(kw in answer for kw in _OOS_SIGNALS)


def _check_sensitive_triggered(answer: str) -> bool:
    return any(kw in answer for kw in _SENSITIVE_SIGNALS)


def run_evaluation() -> dict:
    print("=" * 60)
    print("  WithBuddy OUT OF SCOPE 테스트셋 평가 (20문항)")
    print(f"  실행 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    llm_chain = _JUDGE_PROMPT | get_llm() | StrOutputParser()
    results = []
    pass_count = partial_count = fail_count = 0

    # RAG 체인 없이 시스템 프롬프트 + LLM 직접 호출
    _chat_chain = RAG_PROMPT | get_llm() | StrOutputParser()

    for tc in TEST_CASES:
        q = tc["question"]
        print(f"\n[{tc['id']:02d}/20] [{tc['category']}] {q[:50]}...")

        try:
            answer = _chat_chain.invoke({
                "question": q,
                "context": "관련 문서를 찾을 수 없습니다.",
                "chat_history": [],
                "user_style": "",
                "user_name": "테스터",
            })
        except Exception as e:
            answer = f"오류: {e}"

        # 키워드 사전 체크
        oos_signal = _check_oos_keywords(answer)
        sensitive_triggered = _check_sensitive_triggered(answer)

        # LLM Judge
        try:
            raw = llm_chain.invoke({
                "question": q,
                "answer": answer,
                "expected_type": tc["expected_type"],
                "confuse_risk": tc["confuse_risk"],
            })
            # JSON 블록 추출 시도
            raw = raw.strip()
            json_match = re.search(r'\{.*?\}', raw, re.DOTALL)
            if json_match:
                parsed = json.loads(json_match.group())
            else:
                parsed = json.loads(raw)
            verdict = parsed.get("verdict", "FAIL")
            detected_type = parsed.get("detected_type", "")
            reason = parsed.get("reason", "")
        except Exception as e:
            verdict, detected_type, reason = "FAIL", "판정오류", str(e)

        # 카운트
        if verdict == "PASS":
            pass_count += 1
            icon = "✅"
        elif verdict == "PARTIAL":
            partial_count += 1
            icon = "⚠️"
        else:
            fail_count += 1
            icon = "❌"

        print(f"  {icon} {verdict}  |  기대: {tc['expected_type']}  검출: {detected_type}")
        print(f"  이유: {reason}")
        if sensitive_triggered and tc["category"] == "대인관계":
            print(f"  ⚠️  민감 응대 키워드 감지됨 — 오발동 확인 필요")

        results.append({
            "id": tc["id"],
            "category": tc["category"],
            "question": q,
            "expected_type": tc["expected_type"],
            "answer": answer[:300] + "..." if len(answer) > 300 else answer,
            "oos_signal_detected": oos_signal,
            "sensitive_triggered": sensitive_triggered,
            "verdict": verdict,
            "detected_type": detected_type,
            "reason": reason,
        })

    # ── 결과 요약 ─────────────────────────────────────────────
    total = len(TEST_CASES)
    print("\n" + "=" * 60)
    print("  📊 평가 결과 요약")
    print("=" * 60)
    print(f"  PASS    : {pass_count}/{total} ({pass_count/total*100:.0f}%)")
    print(f"  PARTIAL : {partial_count}/{total} ({partial_count/total*100:.0f}%)")
    print(f"  FAIL    : {fail_count}/{total} ({fail_count/total*100:.0f}%)")

    # 카테고리별
    categories = ["직무실무", "대인관계", "법률세무", "기타OOS"]
    print("\n  📂 카테고리별 결과")
    print("  " + "-" * 40)
    for cat in categories:
        cat_results = [r for r in results if r["category"] == cat]
        cat_pass = sum(1 for r in cat_results if r["verdict"] == "PASS")
        cat_fail = sum(1 for r in cat_results if r["verdict"] == "FAIL")
        print(f"  {cat:<10}  PASS {cat_pass}/5  FAIL {cat_fail}/5")

    # FAIL 목록
    fails = [r for r in results if r["verdict"] in ("FAIL", "PARTIAL")]
    if fails:
        print("\n  ⚠️  FAIL / PARTIAL 목록")
        print("  " + "-" * 40)
        for r in fails:
            print(f"  [{r['id']:02d}] {r['verdict']} — {r['reason']}")

    print("=" * 60)

    return {
        "evaluated_at": datetime.now().isoformat(),
        "summary": {
            "total": total,
            "pass": pass_count,
            "partial": partial_count,
            "fail": fail_count,
            "pass_rate": round(pass_count / total, 4),
        },
        "details": results,
    }


def _generate_html(summary: dict, html_path: str) -> None:
    s = summary["summary"]
    details = summary["details"]
    evaluated_at = summary["evaluated_at"][:16].replace("T", " ")

    verdict_color = {"PASS": "#22c55e", "PARTIAL": "#f59e0b", "FAIL": "#ef4444"}
    verdict_bg    = {"PASS": "#f0fdf4", "PARTIAL": "#fffbeb", "FAIL": "#fef2f2"}

    # 카테고리별 그룹핑
    categories = ["직무실무", "대인관계", "법률세무", "기타OOS"]
    cat_labels = {"직무실무": "카테고리 1. 직무 실무", "대인관계": "카테고리 2. 대인관계·갈등",
                  "법률세무": "카테고리 3. 법률·세무", "기타OOS": "카테고리 4. 기타 OUT OF SCOPE"}

    rows = ""
    for cat in categories:
        cat_results = [r for r in details if r["category"] == cat]
        cat_pass = sum(1 for r in cat_results if r["verdict"] == "PASS")
        rows += f'<tr><td colspan="6" style="background:#1e293b;color:white;font-weight:bold;padding:8px 12px">{cat_labels[cat]} &nbsp; PASS {cat_pass}/5</td></tr>'
        for r in cat_results:
            color = verdict_color.get(r["verdict"], "#888")
            bg    = verdict_bg.get(r["verdict"], "#fff")
            badge = f'<span style="background:{color};color:white;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:bold">{r["verdict"]}</span>'
            rows += f"""<tr style="background:{bg}">
              <td style="text-align:center;width:28px">{r['id']}</td>
              <td style="width:160px">{r['question']}</td>
              <td style="text-align:center;width:60px">{r['expected_type']}</td>
              <td style="text-align:center;width:70px">{badge}</td>
              <td style="width:70px;color:#475569">{r['detected_type']}</td>
              <td style="color:#475569">{r['reason']}</td>
            </tr>"""

    fail_rows = ""
    for r in [x for x in details if x["verdict"] in ("FAIL", "PARTIAL")]:
        answer_text = r["answer"].replace("<","&lt;").replace(">","&gt;").replace("\n","<br>")
        fail_rows += f"""<div style="margin-bottom:20px;padding:14px;border-left:4px solid #ef4444;background:#fef2f2;border-radius:4px">
          <div style="font-weight:bold;margin-bottom:6px">[{r['id']:02d}] {r['question']}</div>
          <div style="color:#dc2626;margin-bottom:8px">판정: {r['verdict']} — {r['reason']}</div>
          <div style="font-size:12px;color:#374151;background:white;padding:10px;border-radius:4px;line-height:1.6">{answer_text}</div>
        </div>"""

    html = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>WithBuddy OOS 평가 결과</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{ font-family: 'Malgun Gothic', sans-serif; font-size: 12px; margin: 24px 28px; color: #1e293b; }}
  h1 {{ font-size: 18px; margin: 0 0 4px; }}
  h2 {{ font-size: 14px; margin: 28px 0 12px; border-bottom: 2px solid #1e293b; padding-bottom: 4px; }}
  .meta {{ color: #64748b; margin-bottom: 16px; font-size: 11px; }}
  .summary {{ display: flex; gap: 12px; margin-bottom: 20px; }}
  .card {{ padding: 12px 20px; border-radius: 8px; text-align: center; min-width: 90px; }}
  .card .num {{ font-size: 26px; font-weight: bold; }}
  .card .label {{ font-size: 11px; color: #64748b; }}
  table {{ border-collapse: collapse; width: 100%; margin-bottom: 24px; }}
  th {{ background: #334155; color: white; padding: 7px 10px; text-align: left; font-size: 11px; }}
  td {{ padding: 6px 10px; border-bottom: 1px solid #e2e8f0; vertical-align: top; font-size: 11px; line-height: 1.5; }}
  @media print {{
    body {{ margin: 10px 14px; }}
    .no-print {{ display: none; }}
    tr {{ page-break-inside: avoid; }}
  }}
</style>
</head>
<body>
<h1>WithBuddy OUT OF SCOPE 평가 결과</h1>
<div class="meta">평가 일시: {evaluated_at} &nbsp;|&nbsp; 총 {s['total']}문항</div>
<div class="summary">
  <div class="card" style="background:#f0fdf4">
    <div class="num" style="color:#22c55e">{s['pass']}</div>
    <div class="label">PASS ({s['pass']/s['total']*100:.0f}%)</div>
  </div>
  <div class="card" style="background:#fffbeb">
    <div class="num" style="color:#f59e0b">{s['partial']}</div>
    <div class="label">PARTIAL</div>
  </div>
  <div class="card" style="background:#fef2f2">
    <div class="num" style="color:#ef4444">{s['fail']}</div>
    <div class="label">FAIL ({s['fail']/s['total']*100:.0f}%)</div>
  </div>
</div>

<h2>판정 결과 요약</h2>
<table>
  <tr>
    <th>#</th><th>질문</th><th>기대유형</th><th>판정</th><th>검출유형</th><th>판정이유</th>
  </tr>
  {rows}
</table>

<h2>FAIL 상세 분석</h2>
{fail_rows if fail_rows else '<p style="color:#64748b">FAIL 항목 없음</p>'}
</body>
</html>"""

    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)


def main():
    parser = argparse.ArgumentParser(description="OUT OF SCOPE 테스트셋 평가")
    parser.add_argument("--output", type=str, default="./data/eval_oos_result.json",
                        help="결과 저장 경로 (기본: ./data/eval_oos_result.json)")
    args = parser.parse_args()

    summary = run_evaluation()

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"\n  💾 결과 저장: {args.output}")

    html_path = args.output.replace(".json", ".html")
    _generate_html(summary, html_path)
    print(f"  🌐 HTML 리포트: {html_path}")
    print(f"  → 브라우저에서 열고 Ctrl+P → PDF로 저장하세요")


if __name__ == "__main__":
    main()
