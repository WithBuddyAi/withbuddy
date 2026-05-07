"""
Fallback QA 테스트 (SCRUM-173)
T-01 ~ T-05 케이스별 /internal/ai/answer 응답 검증
"""

import json
import requests

BASE_URL = "http://localhost:8000"
ENDPOINT = f"{BASE_URL}/internal/ai/answer"

CASES = [
    {
        "id": "T-01",
        "desc": "회사 문서 있음",
        "payload": {
            "questionId": 1,
            "user": {"userId": 1, "name": "테스트", "companyCode": "WB0001", "companyName": "", "hireDate": ""},
            "content": "연차 신청은 어떻게 하나요?",
            "conversationHistory": [],
        },
        "expect": {"messageType": "rag_answer", "has_docs": True, "no_fallback_text": True},
    },
    {
        "id": "T-02",
        "desc": "회사 문서 없음 + 공통 법령 있음 (Case A)",
        "payload": {
            "questionId": 2,
            "user": {"userId": 1, "name": "테스트", "companyCode": "WB0001", "companyName": "", "hireDate": ""},
            "content": "근로기준법상 연장근로 한도가 어떻게 되나요?",
            "conversationHistory": [],
        },
        "expect": {"messageType": "rag_answer", "case_a_text": True},
    },
    {
        "id": "T-03",
        "desc": "회사 문서 없음 + 공통 법령도 없음 (Case B)",
        "payload": {
            "questionId": 3,
            "user": {"userId": 1, "name": "테스트", "companyCode": "WB0001", "companyName": "", "hireDate": ""},
            "content": "회사 기숙사 신청 방법이 어떻게 되나요?",
            "conversationHistory": [],
        },
        "expect": {"messageType": "no_result", "documents_empty": True, "has_contacts": True},
    },
    {
        "id": "T-04",
        "desc": "OUT OF SCOPE 질문",
        "payload": {
            "questionId": 4,
            "user": {"userId": 1, "name": "테스트", "companyCode": "WB0001", "companyName": "", "hireDate": ""},
            "content": "오늘 점심 뭐 먹을까요?",
            "conversationHistory": [],
        },
        "expect": {"messageType": "out_of_scope"},
    },
    {
        "id": "T-05",
        "desc": "민감 키워드 질문 (행정 의도 없음)",
        "payload": {
            "questionId": 5,
            "user": {"userId": 1, "name": "테스트", "companyCode": "WB0001", "companyName": "", "hireDate": ""},
            "content": "동료한테 괴롭힘을 당하고 있어요",
            "conversationHistory": [],
        },
        "expect": {"messageType": "out_of_scope"},
    },
]

# Case A 고정 문구 키워드 (prompts.py 기준)
CASE_A_KEYWORDS = ["법령", "근로기준법", "규정상", "회사 사규에는"]
# fallback 문구가 없어야 하는 경우 체크할 키워드
FALLBACK_KEYWORDS = ["담당자에게 문의", "사수님과 직접"]


def check(case: dict, resp: dict) -> list[str]:
    fails = []
    exp = case["expect"]
    msg_type = resp.get("messageType", "")
    content = resp.get("content", "")
    docs = resp.get("documents", [])
    contacts = resp.get("recommendedContacts", [])

    if msg_type != exp["messageType"]:
        fails.append(f"messageType: 기대={exp['messageType']}, 실제={msg_type}")

    if exp.get("has_docs") and not docs:
        fails.append("documents[]가 비어있음 (회사 문서 ID 있어야 함)")

    if exp.get("documents_empty") and docs:
        fails.append(f"documents[]가 비어있어야 하는데 {docs} 반환됨")

    if exp.get("has_contacts") and not contacts:
        fails.append("recommendedContacts[]가 비어있음 (담당자 정보 있어야 함)")

    if exp.get("no_fallback_text"):
        for kw in FALLBACK_KEYWORDS:
            if kw in content:
                fails.append(f"fallback 문구 포함됨: '{kw}'")

    return fails


def run():
    print("=" * 60)
    print("  Fallback QA 테스트 (SCRUM-173)")
    print("=" * 60)

    results = []
    for case in CASES:
        print(f"\n[{case['id']}] {case['desc']}")
        print(f"  질문: {case['payload']['content']}")
        try:
            r = requests.post(ENDPOINT, json=case["payload"], timeout=60)
            r.raise_for_status()
            resp = r.json()

            msg_type = resp.get("messageType", "-")
            docs = resp.get("documents", [])
            contacts = resp.get("recommendedContacts", [])
            content_preview = resp.get("content", "")[:80].replace("\n", " ")

            print(f"  messageType     : {msg_type}")
            print(f"  documents       : {len(docs)}건 {[d.get('documentId') for d in docs]}")
            print(f"  recommendedContacts: {len(contacts)}건")
            print(f"  content (앞80자): {content_preview}...")

            fails = check(case, resp)
            if fails:
                print(f"  ❌ FAIL")
                for f in fails:
                    print(f"     - {f}")
                results.append((case["id"], "FAIL", fails))
            else:
                print(f"  ✅ PASS")
                results.append((case["id"], "PASS", []))

        except Exception as e:
            print(f"  ❌ ERROR: {e}")
            results.append((case["id"], "ERROR", [str(e)]))

    print("\n" + "=" * 60)
    print("  결과 요약")
    print("=" * 60)
    for rid, status, fails in results:
        mark = "✅" if status == "PASS" else "❌"
        print(f"  {mark} {rid}: {status}")
        for f in fails:
            print(f"       → {f}")
    print("=" * 60)


if __name__ == "__main__":
    run()
