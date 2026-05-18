"""
IN SCOPE 정답률 테스트 (50문항)
실행: python scripts/test_inscope.py
"""

import json
import os
import time
import requests

BASE_URL = os.getenv("AI_BASE_URL", "http://localhost:8000")
API_KEY = os.getenv("INTERNAL_API_KEY", "")
COMPANY_CODE = "WB0001"
COMPANY_NAME = "테크주식회사"

TEST_SET = [
    # ── ONBOARDING ──────────────────────────────────────────────
    {"id": "Q01", "cat": "ONBOARDING", "q": "첫 출근하면 누구를 찾아가야 하나요?", "kw": ["경영지원팀", "김지수"]},
    {"id": "Q02", "cat": "ONBOARDING", "q": "입사 첫날 어떤 장비를 받나요?", "kw": ["노트북", "출입카드"]},
    {"id": "Q03", "cat": "ONBOARDING", "q": "회사 이메일 계정은 언제 받고 형식은 어떻게 되나요?", "kw": ["경영지원팀", "techco.co.kr"]},
    {"id": "Q04", "cat": "ONBOARDING", "q": "입사 첫날 점심은 어떻게 하나요?", "kw": ["법인카드", "버디"]},
    {"id": "Q05", "cat": "ONBOARDING", "q": "입사 첫날 진행 순서는 어떻게 되나요?", "kw": ["김지수", "이메일", "Slack"]},
    {"id": "Q06", "cat": "ONBOARDING", "q": "4대보험은 따로 신청해야 하나요?", "kw": ["자동", "신청"]},
    {"id": "Q07", "cat": "ONBOARDING", "q": "주차 등록은 어떻게 하나요?", "kw": ["jisoo.kim", "차량번호"]},
    {"id": "Q08", "cat": "ONBOARDING", "q": "IT 계정이나 VPN 문제는 누구에게 물어봐야 하나요?", "kw": ["박민준", "minjun"]},
    {"id": "Q09", "cat": "ONBOARDING", "q": "연차나 급여, 증명서 관련 문의는 누구에게 하나요?", "kw": ["경영지원팀", "김지수"]},
    {"id": "Q10", "cat": "ONBOARDING", "q": "입사 첫날 계정 세팅은 어떤 순서로 하나요?", "kw": ["Gmail", "MFA", "Slack"]},

    # ── HR ──────────────────────────────────────────────────────
    {"id": "Q11", "cat": "HR", "q": "출근 시간이 어떻게 되나요?", "kw": ["9시", "6시", "코어타임"]},
    {"id": "Q12", "cat": "HR", "q": "출퇴근 기록은 어떻게 해요?", "kw": ["Flex", "출근"]},
    {"id": "Q13", "cat": "HR", "q": "수습 기간이 얼마나 돼요?", "kw": ["3개월", "100%"]},
    {"id": "Q14", "cat": "HR", "q": "수습 기간에도 연차가 생기나요?", "kw": ["1일", "개근"]},
    {"id": "Q15", "cat": "HR", "q": "수습 평가는 어떻게 진행되나요?", "kw": ["팀장", "Flex", "면담"]},
    {"id": "Q16", "cat": "HR", "q": "연차는 언제부터 생겨요?", "kw": ["1개월", "11일", "15일"]},
    {"id": "Q17", "cat": "HR", "q": "연차 신청은 어떻게 해요?", "kw": ["Flex", "휴가", "팀장"]},
    {"id": "Q18", "cat": "HR", "q": "오전 반차를 쓰면 몇 시에 출근하나요?", "kw": ["오후 2시", "0.5"]},
    {"id": "Q19", "cat": "HR", "q": "병가는 며칠까지 쓸 수 있어요?", "kw": ["3일", "유급"]},
    {"id": "Q20", "cat": "HR", "q": "급여일이 언제예요?", "kw": ["25일", "Flex"]},

    # ── ADMIN ───────────────────────────────────────────────────
    {"id": "Q21", "cat": "ADMIN", "q": "연차 신청은 Flex에서 해요, Notion에서 해요?", "kw": ["Flex"]},
    {"id": "Q22", "cat": "ADMIN", "q": "경비 정산은 어떻게 해요?", "kw": ["Notion", "경비정산서", "25일"]},
    {"id": "Q23", "cat": "ADMIN", "q": "경비 정산 적요란은 어떻게 써요?", "kw": ["출발지", "목적지"]},
    {"id": "Q24", "cat": "ADMIN", "q": "점심 식대 영수증 제출해야 하나요?", "kw": ["불필요", "복지카드"]},
    {"id": "Q25", "cat": "ADMIN", "q": "야근 식대랑 회식비는 뭐가 달라요?", "kw": ["1만원", "Flex", "회식"]},
    {"id": "Q26", "cat": "ADMIN", "q": "법인카드 사용 후 어떻게 처리해요?", "kw": ["3영업일", "Notion"]},
    {"id": "Q27", "cat": "ADMIN", "q": "비품은 어떻게 신청해요?", "kw": ["Notion", "화요일", "비품"]},
    {"id": "Q28", "cat": "ADMIN", "q": "A4 용지는 어디 있어요?", "kw": ["3층", "탕비실", "서랍"]},
    {"id": "Q29", "cat": "ADMIN", "q": "회의실은 어디서 예약해요?", "kw": ["Google Calendar", "회의실"]},
    {"id": "Q30", "cat": "ADMIN", "q": "출입카드 잃어버리면 어떻게 해요?", "kw": ["jisoo.kim", "재발급"]},

    # ── WELFARE ─────────────────────────────────────────────────
    {"id": "Q31", "cat": "WELFARE", "q": "복지카드는 언제부터 쓸 수 있어요?", "kw": ["1개월", "Flex"]},
    {"id": "Q32", "cat": "WELFARE", "q": "복지카드 한도가 얼마예요?", "kw": ["20만원", "10만원"]},
    {"id": "Q33", "cat": "WELFARE", "q": "복지카드로 쓸 수 없는 항목이 뭐예요?", "kw": ["현금", "유흥", "명품"]},
    {"id": "Q34", "cat": "WELFARE", "q": "탕비실에서 뭘 무료로 이용할 수 있어요?", "kw": ["커피", "간식", "생수"]},
    {"id": "Q35", "cat": "WELFARE", "q": "본인 결혼하면 경조금이 얼마예요?", "kw": ["20만원", "경조"]},
    {"id": "Q36", "cat": "WELFARE", "q": "생일에 뭔가 혜택이 있나요?", "kw": ["조기 퇴근", "오후 2시"]},
    {"id": "Q37", "cat": "WELFARE", "q": "건강검진은 언제부터 받을 수 있어요?", "kw": ["3개월", "10만원", "Flex"]},
    {"id": "Q38", "cat": "WELFARE", "q": "단체보험은 언제부터 적용돼요?", "kw": ["입사일", "자동", "실손"]},
    {"id": "Q39", "cat": "WELFARE", "q": "도서 구입비 지원이 있나요?", "kw": ["3만원", "Notion", "도서"]},
    {"id": "Q40", "cat": "WELFARE", "q": "온라인 강의 지원은 어떻게 돼요?", "kw": ["30만원", "3개월", "Notion"]},

    # ── IT ──────────────────────────────────────────────────────
    {"id": "Q41", "cat": "IT", "q": "입사 첫날 노트북 세팅은 어떻게 해요?", "kw": ["Gmail", "MFA", "Slack"]},
    {"id": "Q42", "cat": "IT", "q": "비밀번호는 얼마나 자주 바꿔야 해요?", "kw": ["90일", "10자"]},
    {"id": "Q43", "cat": "IT", "q": "MFA 설정은 어떻게 해요?", "kw": ["Google Authenticator", "QR"]},
    {"id": "Q44", "cat": "IT", "q": "VPN은 언제 써야 해요?", "kw": ["재택", "vpn.techco"]},
    {"id": "Q45", "cat": "IT", "q": "VPN 연결이 안 될 때 어떻게 해요?", "kw": ["help-it", "재시작"]},
    {"id": "Q46", "cat": "IT", "q": "Slack 채널에 어떻게 초대받아요?", "kw": ["minjun.park", "minjun"]},
    {"id": "Q47", "cat": "IT", "q": "Slack에서 확인했다는 표시는 어떻게 해요?", "kw": ["리액션", "✅", "👍"]},
    {"id": "Q48", "cat": "IT", "q": "Notion 권한은 어떻게 받아요?", "kw": ["권한", "minjun", "1영업일"]},
    {"id": "Q49", "cat": "IT", "q": "소프트웨어 설치하고 싶은데 어떻게 해요?", "kw": ["help-it", "승인"]},
    {"id": "Q50", "cat": "IT", "q": "퇴근할 때 노트북 화면 잠금은 어떻게 해요?", "kw": ["Win+L", "Command", "5분"]},
]


def run_test(item, q_num):
    payload = {
        "questionId": q_num,
        "user": {
            "userId": 9999,
            "name": "테스트",
            "companyCode": COMPANY_CODE,
            "companyName": COMPANY_NAME,
            "hireDate": "",
        },
        "content": item["q"],
    }
    headers = {"Content-Type": "application/json", "X-API-Key": API_KEY}

    start = time.time()
    try:
        r = requests.post(f"{BASE_URL}/internal/ai/answer", json=payload, headers=headers, timeout=30)
        latency = round(time.time() - start, 2)
        data = r.json()
    except Exception as e:
        return {"id": item["id"], "cat": item["cat"], "passed": False, "reason": f"ERROR: {e}", "latency": 0}

    msg_type = data.get("messageType", "")
    content = data.get("content", "")

    type_ok = msg_type == "rag_answer"
    kw_ok = any(kw in content for kw in item["kw"])
    passed = type_ok and kw_ok

    reason = ""
    if not type_ok:
        reason += f"messageType={msg_type} "
    if not kw_ok:
        reason += f"키워드 미포함({item['kw']})"

    return {
        "id": item["id"],
        "cat": item["cat"],
        "q": item["q"],
        "passed": passed,
        "reason": reason.strip(),
        "messageType": msg_type,
        "latency": latency,
        "preview": content[:80].replace("\n", " "),
    }


def main():
    print(f"\n{'='*60}")
    print(f"IN SCOPE 정답률 테스트 — {COMPANY_CODE} ({COMPANY_NAME})")
    print(f"{'='*60}\n")

    results = []
    cat_stats = {}

    for i, item in enumerate(TEST_SET, 1):
        print(f"[{item['id']}] {item['q'][:40]}...", end=" ", flush=True)
        result = run_test(item, i)
        results.append(result)

        status = "✅ PASS" if result["passed"] else "❌ FAIL"
        print(f"{status} ({result['latency']}s)")
        if not result["passed"]:
            print(f"     └ {result['reason']}")

        cat = item["cat"]
        if cat not in cat_stats:
            cat_stats[cat] = {"pass": 0, "total": 0}
        cat_stats[cat]["total"] += 1
        if result["passed"]:
            cat_stats[cat]["pass"] += 1

        time.sleep(0.5)

    # ── 결과 요약 ─────────────────────────────────────────────
    total = len(results)
    passed = sum(1 for r in results if r["passed"])
    rate = round(passed / total * 100, 1)

    print(f"\n{'='*60}")
    print(f"최종 정답률: {passed}/{total} ({rate}%)")
    print(f"{'='*60}")

    print("\n카테고리별 결과:")
    for cat, s in cat_stats.items():
        cat_rate = round(s["pass"] / s["total"] * 100, 1)
        bar = "█" * s["pass"] + "░" * (s["total"] - s["pass"])
        print(f"  {cat:<12} {bar} {s['pass']}/{s['total']} ({cat_rate}%)")

    fails = [r for r in results if not r["passed"]]
    if fails:
        print(f"\nFAIL 목록 ({len(fails)}건):")
        for r in fails:
            print(f"  [{r['id']}] {r['q'][:45]} → {r['reason']}")

    # JSON 저장
    out = {
        "date": time.strftime("%Y-%m-%d %H:%M"),
        "company": COMPANY_CODE,
        "total": total,
        "passed": passed,
        "rate": rate,
        "results": results,
    }
    filename = f"scripts/test_inscope_result_{time.strftime('%Y%m%d_%H%M')}.json"
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {filename}")


if __name__ == "__main__":
    main()
