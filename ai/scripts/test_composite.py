"""
복합 질문 분리 처리 테스트 (스토리 2-8)
실행: cd ai/ && venv/Scripts/python scripts/test_composite.py [서버URL]
기본: https://ai.itsdev.kr
"""
import sys
import requests

BASE_URL = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "https://ai.itsdev.kr"
print(f"서버: {BASE_URL}\n")

# ── 자동 판정 헬퍼 ──────────────────────────────────────────────

def call(question: str, company_code: str = "WB0001", qid: int = 1) -> dict:
    payload = {
        "questionId": qid,
        "user": {"userId": 1, "name": "테스트", "companyCode": company_code},
        "content": question,
        "conversationHistory": [],
    }
    try:
        r = requests.post(f"{BASE_URL}/internal/ai/answer", json=payload, timeout=60)
        return r.json()
    except Exception as e:
        return {"content": f"[오류] {e}", "messageType": "error"}


def judge(result: dict, *, pass_kw: list[str] = [], fail_kw: list[str] = [], note: str = "") -> str:
    content = result.get("content", "")
    fails = [f"❌ '{kw}' 포함됨" for kw in fail_kw if kw in content]
    passes = [f"✅ '{kw}' 확인" for kw in pass_kw if kw in content]
    missing = [f"⚠️  '{kw}' 미포함" for kw in pass_kw if kw not in content]

    if fails:
        verdict = "AUTO-FAIL"
    elif missing:
        verdict = "요수동확인"
    else:
        verdict = "AUTO-PASS힌트"

    lines = [verdict] + fails + passes + missing
    if note:
        lines.append(f"📝 수동확인: {note}")
    return "\n  ".join(lines)


def print_result(tc_id: str, question: str, company_code: str, result: dict, verdict: str):
    print(f"\n{'─'*70}")
    print(f"[{tc_id}] 회사={company_code}")
    print(f"질문: {question}")
    print(f"messageType: {result.get('messageType')}")
    content = result.get("content", "")
    print(f"응답:\n{content[:600]}{'...(생략)' if len(content) > 600 else ''}")
    print(f"  → {verdict}")


# ── 공통 FAIL 키워드 (외부 기관명) ──────────────────────────────
_EXTERNAL_ORGS = ["고용노동부", "근로복지공단", "국세청", "세무서", "노동청", "법원", "검찰", "경찰"]

# ══════════════════════════════════════════════════════════════════
# 유형 1 — IN + OUT 직무실무
# ══════════════════════════════════════════════════════════════════
print("\n\n━━━ 유형 1: IN + OUT 직무실무 ━━━")

cases_1 = [
    ("C-1-01", "WB0001", "연차 신청 방법이랑 이 코드 어떻게 리뷰하나요?",
     ["사수님", "선배님"], _EXTERNAL_ORGS, "연차 IN + 코드리뷰 OUT. 브릿지 자연스러운지 확인"),
    ("C-1-02", "WB0001", "재직증명서 발급 방법이랑 기획서 작성 팁 알려줘요",
     ["사수님", "선배님"], _EXTERNAL_ORGS, "재직증명서 IN + 기획서 OUT"),
    ("C-1-03", "WB0001", "반차 신청 방법이랑 보고서 작성 팁 알려줘요",
     ["사수님", "선배님"], _EXTERNAL_ORGS, "반차 IN + 보고서 OUT"),
    ("C-1-04", "WB0001", "출입카드 발급 방법이랑 회의 진행 잘하는 팁 알려줘요",
     ["사수님", "선배님"], _EXTERNAL_ORGS, "출입카드 IN + 회의팁 OUT"),
]

for tc_id, company, q, pass_kw, fail_kw, note in cases_1:
    result = call(q, company)
    verdict = judge(result, pass_kw=pass_kw, fail_kw=fail_kw + ["세무사", "전문가"], note=note)
    print_result(tc_id, q, company, result, verdict)

# ══════════════════════════════════════════════════════════════════
# 유형 2 — IN + OUT 전문가 영역
# ══════════════════════════════════════════════════════════════════
print("\n\n━━━ 유형 2: IN + OUT 전문가 영역 ━━━")

cases_2 = [
    ("C-2-01-WB0001", "WB0001", "복지카드 한도가 얼마예요? 그리고 연말정산 세금 환급액 계산해줄 수 있어요?",
     ["전문가"], ["사수님", "선배님"] + _EXTERNAL_ORGS, "복지카드 월20만원 IN + 세무 OUT"),
    ("C-2-01-WB0002", "WB0002", "복지카드 한도가 얼마예요? 그리고 연말정산 세금 환급액 계산해줄 수 있어요?",
     ["전문가"], ["사수님", "선배님"] + _EXTERNAL_ORGS, "WB0002: 복지카드 없음·문화상품권 IN + 세무 OUT"),
    ("C-2-02", "WB0001", "경조금은 어떤 경우에 받을 수 있어요? 그리고 증여세 비과세 한도가 얼마인지도 알려줘요",
     ["전문가"], ["사수님", "선배님"] + _EXTERNAL_ORGS, "경조금 IN + 증여세 OUT"),
    ("C-2-03", "WB0001", "경조금 신청 방법이랑 퇴직금 세금 얼마나 내야 해요?",
     ["전문가"], ["사수님", "선배님"] + _EXTERNAL_ORGS, "경조금 IN + 퇴직금 세금 OUT"),
]

for tc_id, company, q, pass_kw, fail_kw, note in cases_2:
    result = call(q, company)
    verdict = judge(result, pass_kw=pass_kw, fail_kw=fail_kw, note=note)
    print_result(tc_id, q, company, result, verdict)

# ══════════════════════════════════════════════════════════════════
# 유형 3 — IN + 민감 키워드
# ══════════════════════════════════════════════════════════════════
print("\n\n━━━ 유형 3: IN + 민감 키워드 ━━━")

cases_3 = [
    ("C-3-01", "WB0001", "건강검진 신청 방법이랑 수습 때 번아웃이 너무 심해요",
     ["경영지원팀"], _EXTERNAL_ORGS, "민감 최상단? 건강검진 IN 포함?"),
    ("C-3-02", "WB0001", "야근 식대 지원이 언제부터예요? 그리고 퇴사 시 소송 걸 수 있는지도 궁금해요",
     ["경영지원팀"], _EXTERNAL_ORGS, "민감 최상단? 야근 식대 기준 포함?"),
    ("C-3-03", "WB0001", "퇴직금 계산 방법이랑 부당해고 소송 어떻게 해요?",
     ["경영지원팀"], _EXTERNAL_ORGS, "민감 최상단? 퇴직금 계산 포함?"),
    ("C-3-04", "WB0001", "VPN 연결 방법이랑 팀장한테 성희롱을 당했어요",
     ["경영지원팀"], _EXTERNAL_ORGS, "민감 최상단 필수. VPN 먼저 나오면 FAIL"),
    ("C-3-05", "WB0001", "복지카드 신청 방법이 궁금하고, 요즘 너무 힘들어서 퇴사하고 싶어요",
     ["경영지원팀"], _EXTERNAL_ORGS, "민감 최상단? 복지카드 IN 포함?"),
]

for tc_id, company, q, pass_kw, fail_kw, note in cases_3:
    result = call(q, company)
    content = result.get("content", "")

    # 민감 응대 최상단 확인: 첫 200자 안에 경영지원팀 있어야 함
    top_200 = content[:200]
    top_check = "✅ 민감응대 최상단 OK" if "경영지원팀" in top_200 else "❌ 민감응대 최상단 아님"

    verdict = judge(result, pass_kw=pass_kw, fail_kw=fail_kw, note=note)
    print_result(tc_id, q, company, result, f"{top_check}\n  {verdict}")

# ══════════════════════════════════════════════════════════════════
# 멀티테넌트 격리 테스트
# ══════════════════════════════════════════════════════════════════
print("\n\n━━━ MT: 멀티테넌트 격리 ━━━")

mt_cases = [
    ("MT-01", "업무 툴 설정 방법이랑 프로젝트 관리 툴 사용법 알려줘요",
     {"WB0001": ["Slack", "Notion", "Jira", "Google"], "WB0002": ["Figma", "Adobe", "Slack"]},
     "두 회사 IN 답변이 다른지 확인"),
    ("MT-02", "야근하면 어떤 혜택이 있어요? 그리고 야근 수당 계산법 알려줘요",
     {"WB0001": ["8시", "1만원", "10,000"], "WB0002": ["8시 30분", "12,000"]},
     "야근 식대 기준이 회사별로 다른지"),
    ("MT-03", "복지카드 관련해서 궁금한 게 있어요. 그리고 세금 환급액 계산해줄 수 있어요?",
     {"WB0001": ["20만원"], "WB0002": ["문화상품권", "5만원"]},
     "WB0001 복지카드 vs WB0002 문화상품권"),
]

for tc_id, q, expected_by_company, note in mt_cases:
    print(f"\n{'─'*70}")
    print(f"[{tc_id}] {note}")
    print(f"질문: {q}")

    for company, expected_kws in expected_by_company.items():
        result = call(q, company, qid=99)
        content = result.get("content", "")
        found = [kw for kw in expected_kws if kw in content]
        missing = [kw for kw in expected_kws if kw not in content]
        status = "✅" if found else "❌"
        print(f"\n  [{company}] messageType={result.get('messageType')}")
        print(f"  응답(앞 300자): {content[:300]}")
        print(f"  {status} 기대 키워드 포함: {found} | 미포함: {missing}")

print("\n\n━━━ 테스트 완료 ━━━")
print("유형 3 민감 케이스는 응답 전문 보고 민감 응대가 최상단인지 수동 확인 필요.")
