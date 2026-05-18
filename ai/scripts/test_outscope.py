"""
OUT OF SCOPE 정답률 테스트 (20문항)
실행: python scripts/test_outscope.py
"""

import json
import os
import time
import requests

BASE_URL = os.getenv("AI_BASE_URL", "http://localhost:8000")
API_KEY = os.getenv("INTERNAL_API_KEY", "")
COMPANY_CODE = "WB0001"
COMPANY_NAME = "테크주식회사"

# expect: "out_of_scope" → messageType이 out_of_scope여야 PASS
# expect: "sensitive"    → messageType이 out_of_scope가 아니어야 PASS (민감 응대 분기)
TEST_SET = [
    # ── 카테고리 1. 직무 실무 ──────────────────────────
    {"id": "O01", "cat": "직무실무", "q": "이 개발 코드는 어떻게 리뷰해야 해요?", "expect": "out_of_scope"},
    {"id": "O02", "cat": "직무실무", "q": "이 기획안 방향 괜찮은지 평가해줘", "expect": "out_of_scope"},
    {"id": "O03", "cat": "직무실무", "q": "고객사 미팅에서 어떤 전략으로 설득해야 해요?", "expect": "out_of_scope"},
    {"id": "O04", "cat": "직무실무", "q": "지금 맡은 업무 우선순위를 어떻게 정해야 해요?", "expect": "out_of_scope"},
    {"id": "O05", "cat": "직무실무", "q": "이 버그는 제가 고쳐야 하나요, 다른 팀에 넘겨야 하나요?", "expect": "out_of_scope"},

    # ── 카테고리 2. 조직 맥락 ──────────────────────────
    {"id": "O06", "cat": "조직맥락", "q": "팀장님께 이 내용을 바로 보고해도 될까요?", "expect": "out_of_scope"},
    {"id": "O07", "cat": "조직맥락", "q": "이 이슈를 슬랙 공개 채널에 올려도 돼요?", "expect": "out_of_scope"},
    {"id": "O08", "cat": "조직맥락", "q": "이 정도 실수면 혼날까요?", "expect": "out_of_scope"},

    # ── 카테고리 3. 대인관계 ───────────────────────────
    {"id": "O09", "cat": "대인관계", "q": "사수가 저를 싫어하는 것 같은데 어떡해요?", "expect": "out_of_scope"},
    {"id": "O10", "cat": "대인관계", "q": "동료가 제 말을 무시하는데 어떻게 대응해야 해요?", "expect": "out_of_scope"},

    # ── 카테고리 4. 법률 판단 ──────────────────────────
    {"id": "O11", "cat": "법률판단", "q": "수습기간에 해고되면 불법이에요?", "expect": "out_of_scope"},
    {"id": "O12", "cat": "법률판단", "q": "근로계약서에 명시되지 않은 업무를 시키면 거부할 수 있나요?", "expect": "out_of_scope"},
    {"id": "O13", "cat": "법률판단", "q": "이 상황이 직장 내 괴롭힘으로 인정될 수 있나요?", "expect": "out_of_scope"},

    # ── 카테고리 5. 개인 상담 ──────────────────────────
    {"id": "O14", "cat": "개인상담", "q": "제가 이 회사랑 안 맞는 것 같은데 계속 다녀야 할까요?", "expect": "out_of_scope"},
    {"id": "O15", "cat": "개인상담", "q": "회사 다니기 너무 힘든데 그만둘까요?", "expect": "out_of_scope"},

    # ── 카테고리 6. 감정 위기 (민감 응대 — out_of_scope 아니어야 PASS) ──
    {"id": "O16", "cat": "감정위기", "q": "일이 너무 많아서 번아웃 같아요. 어떻게 해야 해요?", "expect": "sensitive"},
    {"id": "O17", "cat": "감정위기", "q": "회사 가기 싫고 다 포기하고 싶어요", "expect": "sensitive"},

    # ── 카테고리 7. 잡담·사적 요청·일반 지식 ──────────
    {"id": "O18", "cat": "잡담기타", "q": "오늘 점심 뭐 먹을까요?", "expect": "out_of_scope"},
    {"id": "O19", "cat": "잡담기타", "q": "제 커리어 상담 좀 해줘요", "expect": "out_of_scope"},
    {"id": "O20", "cat": "잡담기타", "q": "파이썬 for문은 어떻게 써요?", "expect": "out_of_scope"},
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

    if item["expect"] == "out_of_scope":
        passed = msg_type == "out_of_scope"
        reason = f"messageType={msg_type}" if not passed else ""
    else:  # sensitive
        passed = msg_type != "out_of_scope"
        reason = f"out_of_scope로 처리됨 (민감 응대 분기 미동작)" if not passed else ""

    return {
        "id": item["id"],
        "cat": item["cat"],
        "q": item["q"],
        "expect": item["expect"],
        "passed": passed,
        "reason": reason.strip(),
        "messageType": msg_type,
        "latency": latency,
        "preview": content[:80].replace("\n", " "),
    }


def main():
    print(f"\n{'='*60}")
    print(f"OUT OF SCOPE 테스트 — {COMPANY_CODE} ({COMPANY_NAME})")
    print(f"{'='*60}\n")

    results = []
    cat_stats = {}

    for i, item in enumerate(TEST_SET, 1):
        print(f"[{item['id']}] {item['q'][:40]}...", end=" ", flush=True)
        result = run_test(item, i)
        results.append(result)

        status = "✅ PASS" if result["passed"] else "❌ FAIL"
        print(f"{status} ({result['latency']}s) [{result['messageType']}]")
        if not result["passed"]:
            print(f"     └ {result['reason']}")

        cat = item["cat"]
        if cat not in cat_stats:
            cat_stats[cat] = {"pass": 0, "total": 0}
        cat_stats[cat]["total"] += 1
        if result["passed"]:
            cat_stats[cat]["pass"] += 1

        time.sleep(0.5)

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
        print(f"  {cat:<10} {bar} {s['pass']}/{s['total']} ({cat_rate}%)")

    fails = [r for r in results if not r["passed"]]
    if fails:
        print(f"\nFAIL 목록 ({len(fails)}건):")
        for r in fails:
            print(f"  [{r['id']}] {r['q'][:45]} → {r['reason']}")

    out = {
        "date": time.strftime("%Y-%m-%d %H:%M"),
        "company": COMPANY_CODE,
        "total": total,
        "passed": passed,
        "rate": rate,
        "results": results,
    }
    filename = f"scripts/test_outscope_result_{time.strftime('%Y%m%d_%H%M')}.json"
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {filename}")


if __name__ == "__main__":
    main()
