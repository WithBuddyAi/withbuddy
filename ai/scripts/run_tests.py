"""
자동 테스트 스크립트
──────────────────────────────────────────────
/chat API에 테스트 질문을 순서대로 전송하고
결과를 마크다운 파일로 저장합니다.

사용법:
    python scripts/run_tests.py
    python scripts/run_tests.py --url http://localhost:8000 --out test_results.md
"""

import argparse
import datetime
import sys
import time

import requests

# ── 테스트 케이스 정의 ──────────────────────────────────────────
# expected: 답변에 포함돼야 할 키워드 (없으면 체크 안 함)
# forbidden: 답변에 없어야 할 키워드 (있으면 WARN)

TEST_CASES = [
    # ── 법률 관련 (원문 우회 포함) ──────────────────────────────
    {
        "group": "법률",
        "no": 1,
        "question": "배우자 출산휴가 며칠이야?",
        "expected": ["20일"],                   # 배우자 출산휴가 20일 (2025년 개정)
        "forbidden": ["미래 시행", "2026. 9. 18"],
    },
    {
        "group": "법률",
        "no": 2,
        "question": "육아휴직 기간이 얼마나 돼?",
        "expected": ["1년"],
        "forbidden": [],
    },
    {
        "group": "법률",
        "no": 3,
        "question": "난임치료휴가 며칠 줘?",
        "expected": ["6일", "난임"],
        "forbidden": [],
    },
    {
        "group": "법률",
        "no": 4,
        "question": "최저임금 얼마야?",
        "expected": ["최저임금"],
        "forbidden": [],
    },
    {
        "group": "법률",
        "no": 5,
        "question": "기간제 근로자 2년 넘으면 어떻게 돼?",
        "expected": ["무기계약", "기간제"],
        "forbidden": [],
    },
    {
        "group": "법률",
        "no": 6,
        "question": "퇴직금 언제 받아?",
        "expected": ["14일", "퇴직"],
        "forbidden": [],
    },

    # ── 회사 문서 관련 ───────────────────────────────────────────
    {
        "group": "회사문서",
        "no": 7,
        "question": "연차 신청 어떻게 해?",
        "expected": ["Flex", "연차"],
        "forbidden": [],
    },
    {
        "group": "회사문서",
        "no": 8,
        "question": "IT 장비 신청하려면 어떻게 해?",
        "expected": ["박민준", "IT"],
        "forbidden": [],
    },
    {
        "group": "회사문서",
        "no": 9,
        "question": "경비 처리 방법이 뭐야?",
        "expected": ["영수증", "경비"],
        "forbidden": [],
    },
    {
        "group": "회사문서",
        "no": 10,
        "question": "재택근무 신청 어떻게 해?",
        "expected": ["Flex", "재택", "2회"],
        "forbidden": [],
    },

    # ── 모호한 질문 (선택지 유도) ────────────────────────────────
    {
        "group": "모호한질문",
        "no": 11,
        "question": "연차",
        "expected": ["①", "②"],
        "forbidden": [],
    },
    {
        "group": "모호한질문",
        "no": 12,
        "question": "휴가",
        "expected": ["①", "②"],
        "forbidden": [],
    },
    {
        "group": "모호한질문",
        "no": 13,
        "question": "경비",
        "expected": ["①", "②"],
        "forbidden": [],
    },

    # ── 범위 외 질문 ─────────────────────────────────────────────
    {
        "group": "범위외",
        "no": 14,
        "question": "오늘 점심 뭐 먹지?",
        "expected": [],
        "forbidden": [],
    },
    {
        "group": "범위외",
        "no": 15,
        "question": "코드 리뷰 어떻게 해?",
        "expected": [],
        "forbidden": [],
    },

    # ── 담당자 질문 ──────────────────────────────────────────────
    {
        "group": "담당자",
        "no": 16,
        "question": "연차 담당자 누구야?",
        "expected": ["김*수"],
        "forbidden": [],
    },
    {
        "group": "담당자",
        "no": 17,
        "question": "IT 장비 담당자 누구야?",
        "expected": ["박민준"],
        "forbidden": [],
    },

    # ── 데모 문서 (4개 규정 파일) ────────────────────────────────
    {
        "group": "데모문서",
        "no": "D1",
        "question": "급여일이 언제야?",
        "expected": ["25일"],
        "forbidden": [],
    },
    {
        "group": "데모문서",
        "no": "D2",
        "question": "IT 계정 세팅 어떻게 해?",
        "expected": ["minjun.park"],
        "forbidden": [],
    },
    {
        "group": "데모문서",
        "no": "D3",
        "question": "복지카드 어떻게 써?",
        "expected": ["10만원", "복지"],
        "forbidden": [],
    },
]


# ── 결과 판정 ────────────────────────────────────────────────────

def check_result(answer: str, expected: list, forbidden: list) -> tuple[str, list]:
    """
    Returns:
        status: "PASS" | "WARN" | "FAIL"
        notes: 판정 근거 목록
    """
    notes = []
    status = "PASS"

    if expected:
        matched = [kw for kw in expected if kw in answer]
        missing = [kw for kw in expected if kw not in answer]
        if matched:
            notes.append(f"✅ 포함 키워드: {', '.join(matched)}")
        if missing:
            notes.append(f"❌ 누락 키워드: {', '.join(missing)}")
            status = "FAIL"

    for kw in forbidden:
        if kw in answer:
            notes.append(f"⚠️ 금지 키워드 발견: {kw}")
            if status == "PASS":
                status = "WARN"

    if not expected and not forbidden:
        notes.append("ℹ️ 키워드 체크 없음 (수동 확인 필요)")
        status = "PASS"

    return status, notes


# ── 메인 ─────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="자동 테스트 스크립트")
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

    results = []
    pass_count = fail_count = warn_count = 0

    current_group = ""
    for tc in TEST_CASES:
        if tc["group"] != current_group:
            current_group = tc["group"]
            print(f"\n── {current_group} ──")

        print(f"  [{tc['no']}] {tc['question'][:30]}...", end=" ", flush=True)

        try:
            resp = requests.post(
                f"{args.url}/chat",
                json={"user_id": args.user_id, "message": tc["question"]},
                timeout=30,
            )
            resp.raise_for_status()
            answer = resp.json().get("answer", "")
            source = resp.json().get("source", "")
            error = None
        except Exception as e:
            answer = ""
            source = ""
            error = str(e)

        if error:
            status, notes = "FAIL", [f"❌ 요청 오류: {error}"]
            fail_count += 1
            print("FAIL (오류)")
        else:
            status, notes = check_result(answer, tc["expected"], tc["forbidden"])
            if status == "PASS":
                pass_count += 1
                print("PASS ✅")
            elif status == "WARN":
                warn_count += 1
                print("WARN ⚠️")
            else:
                fail_count += 1
                print("FAIL ❌")

        results.append({**tc, "answer": answer, "source": source, "status": status, "notes": notes})

        if args.delay > 0:
            time.sleep(args.delay)

    # ── 마크다운 작성 ──────────────────────────────────────────
    total = len(TEST_CASES)
    lines = [
        f"# 테스트 결과 보고서",
        f"",
        f"- **실행 일시:** {datetime.datetime.now().strftime('%Y-%m-%d %H:%M')}",
        f"- **서버:** {args.url}",
        f"- **총 {total}건** — ✅ PASS {pass_count} / ❌ FAIL {fail_count} / ⚠️ WARN {warn_count}",
        f"",
        f"---",
        f"",
    ]

    current_group = ""
    for r in results:
        if r["group"] != current_group:
            current_group = r["group"]
            lines.append(f"## {current_group}")
            lines.append("")

        status_icon = {"PASS": "✅", "FAIL": "❌", "WARN": "⚠️"}.get(r["status"], "")
        lines.append(f"### {status_icon} [{r['no']}] {r['question']}")
        lines.append("")

        if r["notes"]:
            for note in r["notes"]:
                lines.append(f"> {note}")
            lines.append("")

        lines.append(f"**답변:**")
        lines.append("")
        # 답변 내 마크다운 깨짐 방지
        answer_escaped = r["answer"].replace("\n", "\n> ")
        lines.append(f"> {answer_escaped}")
        lines.append("")

        if r["source"]:
            lines.append(f"**출처:** `{r['source']}`")
            lines.append("")

        lines.append("---")
        lines.append("")

    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"\n결과 저장: {out_file}")
    print(f"총 {total}건 — PASS {pass_count} / FAIL {fail_count} / WARN {warn_count}")

    if fail_count > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
