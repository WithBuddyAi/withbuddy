"""
E2E 평가 스크립트 — 위드버디 테스트셋 v2 (40문항)
실행: python scripts/test_e2e_v2.py
결과: scripts/test_e2e_v2_result_YYYYMMDD_HHMM.json

출력 항목: 분류(messageType), 속도(latency), 정확도, 미스이유
판정 기준: expect_type == 실제 messageType 일치 여부
  expect_type: rag_answer / no_result / out_of_scope

★ = BM25 성능 측정용 단어형 쿼리 (15문항)
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
# 테스트 문항 (withbuddy_testset_v2.md)
# ══════════════════════════════════════════════════════════════════
TESTS = [
    # ── rag_answer (25건) ──
    {"id": "Q01", "q": "월급은 언제 들어와?",                               "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q02", "q": "수습 때 월급 깎여?",                                "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q03", "q": "야근하면 수당 얼마야?",                              "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q04", "q": "연차 언제부터 쓸 수 있어?",                         "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q05", "q": "반차 어떻게 신청해?",                               "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q06", "q": "결혼하면 휴가 며칠 줘?",                            "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q07", "q": "재택근무 가능해?",                                  "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q08", "q": "재직증명서 어떻게 뽑아?",                           "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q09", "q": "점심 영수증 제출해야 해?",                          "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q10", "q": "야근하면 밥값 나와?",                               "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q11", "q": "복지카드 한도 얼마야?",                             "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q12", "q": "수습 때도 복지카드 써?",                            "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q13", "q": "결혼하면 회사에서 돈 줘?",                          "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q14", "q": "생일에 혜택 있어?",                                 "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q15", "q": "책 사면 회사에서 돈 돌려줘?",                       "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q16", "q": "온라인 강의비 지원돼?",                             "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q17", "q": "병원비 보험 처리 돼?",                              "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q18", "q": "경비 정산 마감이 언제야?",                          "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q19", "q": "법인카드 쓰고 나서 언제까지 정산해야 해?",          "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q20", "q": "출장 숙박비 얼마까지 돼?",                          "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q21", "q": "비품 신청 어떻게 해?",                              "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q22", "q": "퇴사할 때 장비 어떻게 반납해?",                     "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q23", "q": "비밀번호 얼마나 자주 바꿔야 해?",                   "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q24", "q": "VPN 설치 파일 어디서 받아?",                        "bm25": True,  "expect_type": "rag_answer"},
    {"id": "Q25", "q": "Slack에서 IT 문의 어디다 해?",                      "bm25": True,  "expect_type": "rag_answer"},
    # ── no_result (8건) ──
    {"id": "Q26", "q": "사내 동호회 있어?",                                 "bm25": True,  "expect_type": "no_result"},
    {"id": "Q27", "q": "리프레시 휴가 있어?",                               "bm25": False, "expect_type": "no_result"},
    {"id": "Q28", "q": "연봉 협상은 어떻게 해?",                            "bm25": True,  "expect_type": "no_result"},
    {"id": "Q29", "q": "업무폰 지급돼?",                                    "bm25": False, "expect_type": "no_result"},
    {"id": "Q30", "q": "주 4일제야?",                                       "bm25": False, "expect_type": "rag_answer"},
    {"id": "Q31", "q": "육아휴직 어떻게 신청해?",                           "bm25": False, "expect_type": "no_result"},
    {"id": "Q32", "q": "스톡옵션 받을 수 있어?",                            "bm25": True,  "expect_type": "no_result"},
    {"id": "Q33", "q": "복장 규정 있어?",                                   "bm25": False, "expect_type": "rag_answer"},
    # ── out_of_scope (7건) ──
    {"id": "Q34", "q": "팀장이랑 사이가 안 좋으면 어떻게 해?",             "bm25": False, "expect_type": "out_of_scope"},
    {"id": "Q35", "q": "요즘 개발자 연봉 시세 어떻게 돼?",                 "bm25": False, "expect_type": "out_of_scope"},
    {"id": "Q36", "q": "저 요즘 번아웃인 것 같아요",                        "bm25": False, "expect_type": "out_of_scope"},
    {"id": "Q37", "q": "오늘 점심 뭐 먹을까?",                              "bm25": False, "expect_type": "out_of_scope"},
    {"id": "Q38", "q": "우리 회사 주식 살 수 있어?",                        "bm25": False, "expect_type": "out_of_scope"},
    {"id": "Q39", "q": "다른 팀 분위기는 어때?",                            "bm25": False, "expect_type": "out_of_scope"},
    {"id": "Q40", "q": "퇴근하고 뭐 하면 좋아?",                            "bm25": False, "expect_type": "out_of_scope"},
]

assert len(TESTS) == 40, f"테스트 케이스 수 오류: {len(TESTS)}건"
assert sum(1 for t in TESTS if t["bm25"]) == 15, f"BM25 ★ 문항 수 오류"

_BY_TYPE = {
    "rag_answer":   [t for t in TESTS if t["expect_type"] == "rag_answer"],
    "no_result":    [t for t in TESTS if t["expect_type"] == "no_result"],
    "out_of_scope": [t for t in TESTS if t["expect_type"] == "out_of_scope"],
}
_BM25 = [t for t in TESTS if t["bm25"]]


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
    print(f"  E2E 평가 v2 — 40문항 ({COMPANY_CODE} / {COMPANY_NAME})")
    print(f"  서버: {BASE_URL}")
    print(f"  기대 타입: rag_answer {len(_BY_TYPE['rag_answer'])}건 /"
          f" no_result {len(_BY_TYPE['no_result'])}건 /"
          f" out_of_scope {len(_BY_TYPE['out_of_scope'])}건")
    print(f"  BM25 ★ 문항: {len(_BM25)}건")
    print(f"{'='*65}\n")

    results: list = []
    total_input   = 0
    total_output  = 0
    total_cache_r = 0
    total_cache_c = 0

    for i, item in enumerate(TESTS, 1):
        star  = "★" if item["bm25"] else " "
        label = f"[{item['id']}]{star} {item['q'][:36]}"
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

    # ── BM25 ★ 문항 정확도 ────────────────────────────────────
    bm25_results = [r for r in results if r.get("bm25")]
    bm25_ok      = sum(1 for r in bm25_results if r["passed"])
    bm25_rate    = round(bm25_ok / len(bm25_results) * 100, 1) if bm25_results else 0.0
    print(f"\n[BM25 ★ 문항 정확도]")
    bar = "█" * bm25_ok + "░" * (len(bm25_results) - bm25_ok)
    print(f"  BM25 ★          {bar} {bm25_ok}/{len(bm25_results)} ({bm25_rate}%)")

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
            star = " ★" if r.get("bm25") else ""
            print(f"  [{r['id']}]{star} {r['q'][:48]}")
            print(f"       └ {r['reason']}")
            if r["preview"]:
                print(f"         응답: {r['preview'][:60]}")
    else:
        print("\n✅ 전체 통과")

    # ── JSON 저장 ─────────────────────────────────────────────
    out = {
        "date":    time.strftime("%Y-%m-%d %H:%M"),
        "version": "v2",
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
        "bm25": {
            "total":  len(bm25_results),
            "passed": bm25_ok,
            "rate":   bm25_rate,
        },
        "results": results,
    }
    fname = f"scripts/test_e2e_v2_result_{time.strftime('%Y%m%d_%H%M')}.json"
    with open(fname, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {fname}")


if __name__ == "__main__":
    main()
