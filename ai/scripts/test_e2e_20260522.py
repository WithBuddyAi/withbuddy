"""
E2E 평가 스크립트 — 40문항 (test_e2e_20260522.md 기반)
실행: python scripts/test_e2e_20260522.py
결과: scripts/test_e2e_20260522_result_YYYYMMDD_HHMM.json

출력 항목: 분류(messageType), 속도(latency), 정확도, 미스이유
판정 기준: expect_type == 실제 messageType 일치 여부
  expect_type: rag_answer / no_result / out_of_scope
"""

import json
import os
import random
import sys
import time
import requests

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

BASE_URL     = os.getenv("AI_BASE_URL", "http://localhost:8000")
API_KEY      = os.getenv("INTERNAL_API_KEY", "")
COMPANY_CODE = "WB0001"
COMPANY_NAME = "테크주식회사"

# ══════════════════════════════════════════════════════════════════
# 테스트 문항 (test_e2e_20260522.md)
# ══════════════════════════════════════════════════════════════════
TESTS = [
    # ── rag_answer (25건) ──
    {"id": "Q01", "q": "입사하고 나서 언제부터 연차를 쓸 수 있어요?",       "expect_type": "rag_answer"},
    {"id": "Q02", "q": "점심값 영수증 제출해야 해요?",                       "expect_type": "rag_answer"},
    {"id": "Q03", "q": "야근하면 밥값 지원되나요?",                          "expect_type": "rag_answer"},
    {"id": "Q04", "q": "병원비 보험 처리 돼요?",                             "expect_type": "rag_answer"},
    {"id": "Q05", "q": "책 사면 회사에서 돈 돌려줘요?",                      "expect_type": "rag_answer"},
    {"id": "Q06", "q": "월급은 언제 들어와요?",                              "expect_type": "rag_answer"},
    {"id": "Q07", "q": "결혼하면 회사에서 뭐 해줘요?",                       "expect_type": "rag_answer"},
    {"id": "Q08", "q": "수습 기간에도 복지카드 쓸 수 있나요?",               "expect_type": "rag_answer"},
    {"id": "Q09", "q": "Notion에서 비품 신청하는 방법이 어떻게 돼요?",       "expect_type": "rag_answer"},
    {"id": "Q10", "q": "Flex에서 반차 신청은 어떻게 해요?",                  "expect_type": "rag_answer"},
    {"id": "Q11", "q": "Slack에서 IT 문의는 어디다 해요?",                   "expect_type": "rag_answer"},
    {"id": "Q12", "q": "회사 강의 지원은 얼마까지 되나요?",                  "expect_type": "rag_answer"},
    {"id": "Q13", "q": "노트북 말고 모니터도 받을 수 있어요?",               "expect_type": "rag_answer"},
    {"id": "Q14", "q": "출장 가면 숙박비 얼마까지 돼요?",                    "expect_type": "rag_answer"},
    {"id": "Q15", "q": "회의실은 몇 개 있어요?",                             "expect_type": "rag_answer"},
    {"id": "Q16", "q": "비밀번호 얼마나 자주 바꿔야 해요?",                  "expect_type": "rag_answer"},
    {"id": "Q17", "q": "법인카드 쓰고 나서 언제까지 정산해야 해요?",         "expect_type": "rag_answer"},
    {"id": "Q18", "q": "퇴사할 때 장비는 어떻게 반납해요?",                  "expect_type": "rag_answer"},
    {"id": "Q19", "q": "생일에 뭔가 혜택이 있나요?",                         "expect_type": "rag_answer"},
    {"id": "Q20", "q": "경비 정산 마감이 언제예요?",                         "expect_type": "rag_answer"},
    {"id": "Q21", "q": "Notion에서 경비 청구서 어떻게 찾아요?",              "expect_type": "rag_answer"},
    {"id": "Q22", "q": "VPN 설치 파일 어디서 받아요?",                       "expect_type": "rag_answer"},
    {"id": "Q23", "q": "수습평가",                                           "expect_type": "rag_answer"},
    {"id": "Q24", "q": "재직증명서",                                         "expect_type": "rag_answer"},
    {"id": "Q25", "q": "반차",                                               "expect_type": "rag_answer"},
    # ── no_result (4건) ──
    {"id": "Q26", "q": "사내 동호회 있어요?",                                "expect_type": "no_result"},
    {"id": "Q27", "q": "리프레시 휴가는 언제 쓸 수 있어요?",                 "expect_type": "no_result"},
    {"id": "Q28", "q": "스톡옵션 받을 수 있나요?",                           "expect_type": "no_result"},
    {"id": "Q29", "q": "사이닝 보너스 있나요?",                              "expect_type": "no_result"},
    # ── rag_answer (법률 1건) ──
    {"id": "Q30", "q": "육아휴직 제도 있어요?",                              "expect_type": "rag_answer"},
    # ── no_result (2건) ──
    {"id": "Q31", "q": "복장 규정이 있나요?",                                "expect_type": "no_result"},
    {"id": "Q32", "q": "주 4일제 운영해요?",                                 "expect_type": "no_result"},
    # ── out_of_scope (7건) ──
    {"id": "Q33", "q": "팀장이랑 사이가 안 좋으면 어떻게 해요?",            "expect_type": "out_of_scope"},
    {"id": "Q34", "q": "요즘 개발자 연봉 시세가 어떻게 돼요?",              "expect_type": "out_of_scope"},
    {"id": "Q35", "q": "퇴근하고 뭐 하면 좋아요?",                          "expect_type": "out_of_scope"},
    {"id": "Q36", "q": "저 요즘 번아웃인 것 같아요",                         "expect_type": "out_of_scope"},
    {"id": "Q37", "q": "우리 회사 주식 살 수 있어요?",                       "expect_type": "out_of_scope"},
    {"id": "Q38", "q": "오늘 점심 뭐 먹을까요?",                             "expect_type": "out_of_scope"},
    {"id": "Q39", "q": "다른 팀 분위기는 어때요?",                           "expect_type": "out_of_scope"},
    # ── rag_answer (1건) ──
    {"id": "Q40", "q": "경조사비",                                           "expect_type": "rag_answer"},
]

assert len(TESTS) == 40, f"테스트 케이스 수 오류: {len(TESTS)}건"

_BY_TYPE = {
    "rag_answer":   [t for t in TESTS if t["expect_type"] == "rag_answer"],
    "no_result":    [t for t in TESTS if t["expect_type"] == "no_result"],
    "out_of_scope": [t for t in TESTS if t["expect_type"] == "out_of_scope"],
}


# ══════════════════════════════════════════════════════════════════
# 실행 로직
# ══════════════════════════════════════════════════════════════════

_RUN_ID = random.randint(1000, 9999)

def run_one(item: dict, q_num: int) -> dict:
    payload = {
        "questionId": q_num,
        "user": {
            "userId": int(f"{_RUN_ID}{q_num:02d}"),
            "name": "테스트",
            "companyCode": COMPANY_CODE,
            "companyName": COMPANY_NAME,
            "hireDate": "2026-03-01",
        },
        "content": item["q"],
        "conversationHistory": [],
    }
    headers = {"Content-Type": "application/json", "X-API-Key": API_KEY}

    start = time.time()
    msg_type = content = ""
    input_tokens = output_tokens = cache_read = cache_creation = 0
    try:
        r = requests.post(
            f"{BASE_URL}/internal/ai/answer/stream",
            json=payload,
            headers=headers,
            timeout=60,
            stream=True,
        )
        r.raise_for_status()
        for raw in r.iter_lines(decode_unicode=True):
            if not raw or not raw.startswith("data: "):
                continue
            try:
                data = json.loads(raw[6:])
            except Exception:
                continue
            if "messageType" in data:
                msg_type       = data.get("messageType", "")
                content        = data.get("content", "")
                input_tokens   = data.get("inputTokens", 0)
                output_tokens  = data.get("outputTokens", 0)
                cache_read     = data.get("cacheReadTokens", 0)
                cache_creation = data.get("cacheCreationTokens", 0)
        latency = round(time.time() - start, 2)
    except Exception as e:
        return {
            **item,
            "passed":         False,
            "reason":         f"ERROR: {e}",
            "messageType":    "error",
            "latency":        0,
            "input_tokens":   0,
            "output_tokens":  0,
            "cache_read":     0,
            "cache_creation": 0,
            "preview":        "",
        }

    passed = (msg_type == item["expect_type"])
    reason = "" if passed else f"expected={item['expect_type']} / actual={msg_type}"

    return {
        **item,
        "passed":         passed,
        "reason":         reason,
        "messageType":    msg_type,
        "latency":        latency,
        "input_tokens":   input_tokens,
        "output_tokens":  output_tokens,
        "cache_read":     cache_read,
        "cache_creation": cache_creation,
        "preview":        content[:80].replace("\n", " "),
    }


def _confusion_row(expect: str, results: list) -> dict:
    sub  = [r for r in results if r["expect_type"] == expect]
    dist: dict = {}
    for r in sub:
        mt = r["messageType"]
        dist[mt] = dist.get(mt, 0) + 1
    return {"total": len(sub), "dist": dist}


def main():
    print(f"\n{'='*65}")
    print(f"  E2E 평가 — 40문항 ({COMPANY_CODE} / {COMPANY_NAME})")
    print(f"  서버: {BASE_URL}")
    print(f"  기대 타입: rag_answer {len(_BY_TYPE['rag_answer'])}건 /"
          f" no_result {len(_BY_TYPE['no_result'])}건 /"
          f" out_of_scope {len(_BY_TYPE['out_of_scope'])}건")
    print(f"{'='*65}\n")

    results: list = []
    total_input   = 0
    total_output  = 0
    total_cache_r = 0
    total_cache_c = 0

    for i, item in enumerate(TESTS, 1):
        label = f"[{item['id']}] {item['q'][:38]}"
        print(f"{label:<46}", end=" ", flush=True)
        result = run_one(item, i)
        results.append(result)

        total_input   += result["input_tokens"]
        total_output  += result["output_tokens"]
        total_cache_r += result["cache_read"]
        total_cache_c += result["cache_creation"]

        status  = "✅" if result["passed"] else "❌"
        tok_str = f"in={result['input_tokens']} out={result['output_tokens']}"
        if result["cache_read"]:
            tok_str += f" cr={result['cache_read']}"
        print(f"{status}  {result['messageType']:<14} ({result['latency']}s  {tok_str})")
        if not result["passed"]:
            print(f"     └ {result['reason']}")

        time.sleep(0.3)

    # ── 집계 ─────────────────────────────────────────────────────
    total   = len(results)
    passed  = sum(1 for r in results if r["passed"])
    rate    = round(passed / total * 100, 1)
    lats    = [r["latency"] for r in results if r["latency"] > 0]
    avg_lat = round(sum(lats) / len(lats), 2) if lats else 0
    max_lat = round(max(lats), 2) if lats else 0

    print(f"\n{'='*65}")
    print(f"  최종 정답률: {passed}/{total} ({rate}%)")
    print(f"  속도: 평균 {avg_lat}s / 최대 {max_lat}s")
    print(f"  토큰: input {total_input:,} / output {total_output:,} (총 {total_input + total_output:,})")
    if total_cache_r or total_cache_c:
        cache_pct = round(total_cache_r / total_input * 100, 1) if total_input else 0
        print(f"  캐시: cache_read {total_cache_r:,} ({cache_pct}%) / cache_creation {total_cache_c:,}")
    print(f"{'='*65}")

    # ── 응답 타입별 정확도 ─────────────────────────────────────
    print("\n[응답 타입별 정확도]")
    for etype in ("rag_answer", "no_result", "out_of_scope"):
        sub     = [r for r in results if r["expect_type"] == etype]
        ok      = sum(1 for r in sub if r["passed"])
        subrate = round(ok / len(sub) * 100, 1) if sub else 0.0
        bar     = "█" * ok + "░" * (len(sub) - ok)
        print(f"  {etype:<16} {bar} {ok}/{len(sub)} ({subrate}%)")

    # ── 혼동 행렬 ─────────────────────────────────────────────
    print("\n[혼동 행렬 — 기대→실제]")
    all_types = sorted({r["messageType"] for r in results} | {"rag_answer", "no_result", "out_of_scope"})
    print(f"  {'기대↓ / 실제→':<18}" + "".join(f"{t:<16}" for t in all_types))
    for etype in ("rag_answer", "no_result", "out_of_scope"):
        sub = [r for r in results if r["expect_type"] == etype]
        row = f"  {etype:<18}"
        for atype in all_types:
            cnt    = sum(1 for r in sub if r["messageType"] == atype)
            marker = f"[{cnt}]" if atype == etype else f" {cnt} "
            row   += f"{marker:<16}"
        print(row)

    # ── FAIL 상세 ─────────────────────────────────────────────
    fails = [r for r in results if not r["passed"]]
    if fails:
        print(f"\n[FAIL 목록 — {len(fails)}건]")
        for r in fails:
            print(f"  [{r['id']}] {r['q'][:48]}")
            print(f"       └ {r['reason']}")
            if r["preview"]:
                print(f"         응답: {r['preview'][:60]}")
    else:
        print("\n✅ 전체 통과")

    # ── JSON 저장 ─────────────────────────────────────────────
    out = {
        "date":    time.strftime("%Y-%m-%d %H:%M"),
        "company": COMPANY_CODE,
        "server":  BASE_URL,
        "total":   total,
        "passed":  passed,
        "rate":    rate,
        "speed": {
            "avg_latency_s": avg_lat,
            "max_latency_s": max_lat,
        },
        "tokens": {
            "input_total":          total_input,
            "output_total":         total_output,
            "cache_read_total":     total_cache_r,
            "cache_creation_total": total_cache_c,
            "cache_hit_rate_pct":   round(total_cache_r / total_input * 100, 1) if total_input else 0,
            "avg_input_per_q":      total_input  // total if total else 0,
            "avg_output_per_q":     total_output // total if total else 0,
        },
        "by_type": {
            etype: _confusion_row(etype, results)
            for etype in ("rag_answer", "no_result", "out_of_scope")
        },
        "results": results,
    }
    fname = f"scripts/test_e2e_20260522_result_{time.strftime('%Y%m%d_%H%M')}.json"
    with open(fname, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {fname}")


if __name__ == "__main__":
    main()
