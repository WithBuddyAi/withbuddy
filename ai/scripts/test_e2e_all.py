"""
E2E 통합 테스트 100문항 — WB0001 기준
실행: python scripts/test_e2e_all.py
결과: scripts/test_e2e_all_result_YYYYMMDD_HHMM.json

판정 기준:
  expect_type="rag_answer"      → messageType==rag_answer AND kw 중 1개 이상 포함
  expect_type="out_of_scope"    → messageType==out_of_scope
  expect_type="sensitive_pass"  → messageType != out_of_scope (민감 응대 분기)
  expect_type="any"             → messageType 무관, kw 중 1개 이상 포함
  top_kw                        → 응답 앞 200자 안에 포함 여부 추가 확인 (민감 우선순위)
"""

import json
import os
import sys
import time
import requests

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

BASE_URL = os.getenv("AI_BASE_URL", "http://localhost:8000")
API_KEY  = os.getenv("INTERNAL_API_KEY", "")
COMPANY_CODE = "WB0001"
COMPANY_NAME = "테크주식회사"

# ══════════════════════════════════════════════════════════════════
# 1. IN SCOPE (50건)
# ══════════════════════════════════════════════════════════════════
INSCOPE = [
    # ONBOARDING
    {"id":"Q01","cat":"ONBOARDING","q":"첫 출근하면 누구를 찾아가야 하나요?","expect_type":"rag_answer","kw":["경영지원팀","김지수"]},
    {"id":"Q02","cat":"ONBOARDING","q":"입사 첫날 어떤 장비를 받나요?","expect_type":"rag_answer","kw":["노트북","출입카드"]},
    {"id":"Q03","cat":"ONBOARDING","q":"회사 이메일 계정은 언제 받고 형식은 어떻게 되나요?","expect_type":"rag_answer","kw":["경영지원팀","techco.co.kr"]},
    {"id":"Q04","cat":"ONBOARDING","q":"입사 첫날 점심은 어떻게 하나요?","expect_type":"rag_answer","kw":["법인카드","버디"]},
    {"id":"Q05","cat":"ONBOARDING","q":"입사 첫날 진행 순서는 어떻게 되나요?","expect_type":"rag_answer","kw":["김지수","이메일","Slack"]},
    {"id":"Q06","cat":"ONBOARDING","q":"4대보험은 따로 신청해야 하나요?","expect_type":"rag_answer","kw":["자동","신청"]},
    {"id":"Q07","cat":"ONBOARDING","q":"주차 등록은 어떻게 하나요?","expect_type":"rag_answer","kw":["jisoo.kim","차량번호"]},
    {"id":"Q08","cat":"ONBOARDING","q":"IT 계정이나 VPN 문제는 누구에게 물어봐야 하나요?","expect_type":"rag_answer","kw":["박민준","minjun"]},
    {"id":"Q09","cat":"ONBOARDING","q":"연차나 급여, 증명서 관련 문의는 누구에게 하나요?","expect_type":"rag_answer","kw":["경영지원팀","김지수"]},
    {"id":"Q10","cat":"ONBOARDING","q":"입사 첫날 계정 세팅은 어떤 순서로 하나요?","expect_type":"rag_answer","kw":["Gmail","MFA","Slack"]},
    # HR
    {"id":"Q11","cat":"HR","q":"출근 시간이 어떻게 되나요?","expect_type":"rag_answer","kw":["9시","6시","코어타임"]},
    {"id":"Q12","cat":"HR","q":"출퇴근 기록은 어떻게 해요?","expect_type":"rag_answer","kw":["Flex","출근"]},
    {"id":"Q13","cat":"HR","q":"수습 기간이 얼마나 돼요?","expect_type":"rag_answer","kw":["3개월","100%"]},
    {"id":"Q14","cat":"HR","q":"수습 기간에도 연차가 생기나요?","expect_type":"rag_answer","kw":["1일","개근"]},
    {"id":"Q15","cat":"HR","q":"수습 평가는 어떻게 진행되나요?","expect_type":"rag_answer","kw":["팀장","Flex","면담"]},
    {"id":"Q16","cat":"HR","q":"연차는 언제부터 생겨요?","expect_type":"rag_answer","kw":["1개월","11일","15일"]},
    {"id":"Q17","cat":"HR","q":"연차 신청은 어떻게 해요?","expect_type":"rag_answer","kw":["Flex","휴가","팀장"]},
    {"id":"Q18","cat":"HR","q":"오전 반차를 쓰면 몇 시에 출근하나요?","expect_type":"rag_answer","kw":["오후 2시","0.5"]},
    {"id":"Q19","cat":"HR","q":"병가는 며칠까지 쓸 수 있어요?","expect_type":"rag_answer","kw":["3일","유급"]},
    {"id":"Q20","cat":"HR","q":"급여일이 언제예요?","expect_type":"rag_answer","kw":["25일","Flex"]},
    # ADMIN
    {"id":"Q21","cat":"ADMIN","q":"연차 신청은 Flex에서 해요, Notion에서 해요?","expect_type":"rag_answer","kw":["Flex"]},
    {"id":"Q22","cat":"ADMIN","q":"경비 정산은 어떻게 해요?","expect_type":"rag_answer","kw":["Notion","경비정산서","25일"]},
    {"id":"Q23","cat":"ADMIN","q":"경비 정산 적요란은 어떻게 써요?","expect_type":"rag_answer","kw":["출발지","목적지"]},
    {"id":"Q24","cat":"ADMIN","q":"점심 식대 영수증 제출해야 하나요?","expect_type":"rag_answer","kw":["불필요","복지카드"]},
    {"id":"Q25","cat":"ADMIN","q":"야근 식대랑 회식비는 뭐가 달라요?","expect_type":"rag_answer","kw":["1만원","Flex","회식"]},
    {"id":"Q26","cat":"ADMIN","q":"법인카드 사용 후 어떻게 처리해요?","expect_type":"rag_answer","kw":["3영업일","Notion"]},
    {"id":"Q27","cat":"ADMIN","q":"비품은 어떻게 신청해요?","expect_type":"rag_answer","kw":["Notion","화요일","비품"]},
    {"id":"Q28","cat":"ADMIN","q":"A4 용지는 어디 있어요?","expect_type":"rag_answer","kw":["3층","탕비실","서랍"]},
    {"id":"Q29","cat":"ADMIN","q":"회의실은 어디서 예약해요?","expect_type":"rag_answer","kw":["Google Calendar","회의실"]},
    {"id":"Q30","cat":"ADMIN","q":"출입카드 잃어버리면 어떻게 해요?","expect_type":"rag_answer","kw":["jisoo.kim","재발급"]},
    # WELFARE
    {"id":"Q31","cat":"WELFARE","q":"복지카드는 언제부터 쓸 수 있어요?","expect_type":"rag_answer","kw":["1개월","Flex"]},
    {"id":"Q32","cat":"WELFARE","q":"복지카드 한도가 얼마예요?","expect_type":"rag_answer","kw":["20만원","10만원"]},
    {"id":"Q33","cat":"WELFARE","q":"복지카드로 쓸 수 없는 항목이 뭐예요?","expect_type":"rag_answer","kw":["현금","유흥","명품"]},
    {"id":"Q34","cat":"WELFARE","q":"탕비실에서 뭘 무료로 이용할 수 있어요?","expect_type":"rag_answer","kw":["커피","간식","생수"]},
    {"id":"Q35","cat":"WELFARE","q":"본인 결혼하면 경조금이 얼마예요?","expect_type":"rag_answer","kw":["20만원","경조"]},
    {"id":"Q36","cat":"WELFARE","q":"생일에 뭔가 혜택이 있나요?","expect_type":"rag_answer","kw":["조기 퇴근","오후 2시"]},
    {"id":"Q37","cat":"WELFARE","q":"건강검진은 언제부터 받을 수 있어요?","expect_type":"rag_answer","kw":["3개월","10만원","Flex"]},
    {"id":"Q38","cat":"WELFARE","q":"단체보험은 언제부터 적용돼요?","expect_type":"rag_answer","kw":["입사일","자동","실손"]},
    {"id":"Q39","cat":"WELFARE","q":"도서 구입비 지원이 있나요?","expect_type":"rag_answer","kw":["3만원","Notion","도서"]},
    {"id":"Q40","cat":"WELFARE","q":"온라인 강의 지원은 어떻게 돼요?","expect_type":"rag_answer","kw":["30만원","3개월","Notion"]},
    # IT
    {"id":"Q41","cat":"IT","q":"입사 첫날 노트북 세팅은 어떻게 해요?","expect_type":"rag_answer","kw":["Gmail","MFA","Slack"]},
    {"id":"Q42","cat":"IT","q":"비밀번호는 얼마나 자주 바꿔야 해요?","expect_type":"rag_answer","kw":["90일","10자"]},
    {"id":"Q43","cat":"IT","q":"MFA 설정은 어떻게 해요?","expect_type":"rag_answer","kw":["Google Authenticator","QR"]},
    {"id":"Q44","cat":"IT","q":"VPN은 언제 써야 해요?","expect_type":"rag_answer","kw":["재택","vpn.techco"]},
    {"id":"Q45","cat":"IT","q":"VPN 연결이 안 될 때 어떻게 해요?","expect_type":"rag_answer","kw":["help-it","재시작"]},
    {"id":"Q46","cat":"IT","q":"Slack 채널에 어떻게 초대받아요?","expect_type":"rag_answer","kw":["minjun.park","minjun"]},
    {"id":"Q47","cat":"IT","q":"Slack에서 확인했다는 표시는 어떻게 해요?","expect_type":"rag_answer","kw":["리액션","✅","👍"]},
    {"id":"Q48","cat":"IT","q":"Notion 권한은 어떻게 받아요?","expect_type":"rag_answer","kw":["권한","minjun","1영업일"]},
    {"id":"Q49","cat":"IT","q":"소프트웨어 설치하고 싶은데 어떻게 해요?","expect_type":"rag_answer","kw":["help-it","승인"]},
    {"id":"Q50","cat":"IT","q":"퇴근할 때 노트북 화면 잠금은 어떻게 해요?","expect_type":"rag_answer","kw":["Win+L","Command","5분"]},
]

# ══════════════════════════════════════════════════════════════════
# 2. OUT OF SCOPE (20건)
# ══════════════════════════════════════════════════════════════════
OUTSCOPE = [
    {"id":"O01","cat":"직무실무","q":"이 개발 코드는 어떻게 리뷰해야 해요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O02","cat":"직무실무","q":"이 기획안 방향 괜찮은지 평가해줘","expect_type":"out_of_scope","kw":[]},
    {"id":"O03","cat":"직무실무","q":"고객사 미팅에서 어떤 전략으로 설득해야 해요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O04","cat":"직무실무","q":"지금 맡은 업무 우선순위를 어떻게 정해야 해요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O05","cat":"직무실무","q":"이 버그는 제가 고쳐야 하나요, 다른 팀에 넘겨야 하나요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O06","cat":"조직맥락","q":"팀장님께 이 내용을 바로 보고해도 될까요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O07","cat":"조직맥락","q":"이 이슈를 슬랙 공개 채널에 올려도 돼요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O08","cat":"조직맥락","q":"이 정도 실수면 혼날까요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O09","cat":"대인관계","q":"사수가 저를 싫어하는 것 같은데 어떡해요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O10","cat":"대인관계","q":"동료가 제 말을 무시하는데 어떻게 대응해야 해요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O11","cat":"법률판단","q":"수습기간에 해고되면 불법이에요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O12","cat":"법률판단","q":"근로계약서에 명시되지 않은 업무를 시키면 거부할 수 있나요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O13","cat":"법률판단","q":"이 상황이 직장 내 괴롭힘으로 인정될 수 있나요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O14","cat":"개인상담","q":"제가 이 회사랑 안 맞는 것 같은데 계속 다녀야 할까요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O15","cat":"개인상담","q":"회사 다니기 너무 힘든데 그만둘까요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O16","cat":"감정위기","q":"일이 너무 많아서 번아웃 같아요. 어떻게 해야 해요?","expect_type":"sensitive_pass","kw":[]},
    {"id":"O17","cat":"감정위기","q":"회사 가기 싫고 다 포기하고 싶어요","expect_type":"sensitive_pass","kw":[]},
    {"id":"O18","cat":"잡담기타","q":"오늘 점심 뭐 먹을까요?","expect_type":"out_of_scope","kw":[]},
    {"id":"O19","cat":"잡담기타","q":"제 커리어 상담 좀 해줘요","expect_type":"out_of_scope","kw":[]},
    {"id":"O20","cat":"잡담기타","q":"파이썬 for문은 어떻게 써요?","expect_type":"out_of_scope","kw":[]},
]

# ══════════════════════════════════════════════════════════════════
# 3. 복합질문 WB0001 (15건)
# ══════════════════════════════════════════════════════════════════
COMPOSITE = [
    # 유형1: IN + OUT 직무실무
    {"id":"C101","cat":"복합IN+OUT직무","q":"연차 신청 방법이랑 이 코드 어떻게 리뷰하나요?","expect_type":"rag_answer","kw":["사수님","선배님"]},
    {"id":"C102","cat":"복합IN+OUT직무","q":"재직증명서 발급 방법이랑 기획서 작성 팁 알려줘요","expect_type":"rag_answer","kw":["사수님","선배님"]},
    {"id":"C103","cat":"복합IN+OUT직무","q":"반차 신청 방법이랑 보고서 작성 팁 알려줘요","expect_type":"rag_answer","kw":["사수님","선배님"]},
    {"id":"C104","cat":"복합IN+OUT직무","q":"출입카드 발급 방법이랑 회의 진행 잘하는 팁 알려줘요","expect_type":"rag_answer","kw":["사수님","선배님"]},
    # 유형2: IN + OUT 전문가
    {"id":"C201","cat":"복합IN+OUT전문가","q":"복지카드 한도가 얼마예요? 그리고 연말정산 세금 환급액 계산해줄 수 있어요?","expect_type":"rag_answer","kw":["전문가"]},
    {"id":"C202","cat":"복합IN+OUT전문가","q":"경조금은 어떤 경우에 받을 수 있어요? 그리고 증여세 비과세 한도가 얼마인지도 알려줘요","expect_type":"rag_answer","kw":["전문가"]},
    {"id":"C203","cat":"복합IN+OUT전문가","q":"경조금 신청 방법이랑 퇴직금 세금 얼마나 내야 해요?","expect_type":"rag_answer","kw":["전문가"]},
    # 유형3: IN + 민감 (경영지원팀이 앞 200자 안에 나와야 함)
    {"id":"C301","cat":"복합IN+민감","q":"건강검진 신청 방법이랑 수습 때 번아웃이 너무 심해요","expect_type":"rag_answer","kw":["경영지원팀"],"top_kw":["경영지원팀"]},
    {"id":"C302","cat":"복합IN+민감","q":"야근 식대 지원이 언제부터예요? 그리고 퇴사 시 소송 걸 수 있는지도 궁금해요","expect_type":"rag_answer","kw":["경영지원팀"],"top_kw":["경영지원팀"]},
    {"id":"C303","cat":"복합IN+민감","q":"퇴직금 계산 방법이랑 부당해고 소송 어떻게 해요?","expect_type":"rag_answer","kw":["경영지원팀"],"top_kw":["경영지원팀"]},
    {"id":"C304","cat":"복합IN+민감","q":"VPN 연결 방법이랑 팀장한테 성희롱을 당했어요","expect_type":"rag_answer","kw":["경영지원팀"],"top_kw":["경영지원팀"]},
    {"id":"C305","cat":"복합IN+민감","q":"복지카드 신청 방법이 궁금하고, 요즘 너무 힘들어서 퇴사하고 싶어요","expect_type":"rag_answer","kw":["경영지원팀"],"top_kw":["경영지원팀"]},
    # 멀티테넌트 WB0001
    {"id":"MT01","cat":"멀티테넌트","q":"업무 툴 설정 방법이랑 프로젝트 관리 툴 사용법 알려줘요","expect_type":"rag_answer","kw":["Slack","Notion","Google"]},
    {"id":"MT02","cat":"멀티테넌트","q":"야근하면 어떤 혜택이 있어요? 그리고 야근 수당 계산법 알려줘요","expect_type":"rag_answer","kw":["8시","1만원","10,000"]},
    {"id":"MT03","cat":"멀티테넌트","q":"복지카드 관련해서 궁금한 게 있어요. 그리고 세금 환급액 계산해줄 수 있어요?","expect_type":"rag_answer","kw":["20만원"]},
]

# ══════════════════════════════════════════════════════════════════
# 4. 추가 (15건): 복합IN+IN / 법률 / Clarifying
# ══════════════════════════════════════════════════════════════════
EXTRA = [
    {"id":"E01","cat":"복합IN+IN","q":"연차 신청 방법이랑 복지카드 한도가 어떻게 돼요?","expect_type":"rag_answer","kw":["Flex","20만원"]},
    {"id":"E02","cat":"복합IN+IN","q":"출근 시간이 어떻게 되고 비품 신청은 어떻게 해요?","expect_type":"rag_answer","kw":["코어타임","Notion"]},
    {"id":"E03","cat":"복합IN+IN","q":"급여일이 언제고 건강검진은 언제부터 받을 수 있어요?","expect_type":"rag_answer","kw":["25일","3개월"]},
    {"id":"E04","cat":"복합IN+IN","q":"VPN 연결 방법이랑 연차는 언제부터 생겨요?","expect_type":"rag_answer","kw":["vpn.techco","1개월"]},
    {"id":"E05","cat":"복합IN+IN","q":"수습 기간이 얼마고 복지카드는 언제부터 쓸 수 있어요?","expect_type":"rag_answer","kw":["3개월","1개월"]},
    {"id":"L01","cat":"법률","q":"최저임금이 얼마예요?","expect_type":"rag_answer","kw":["10,030"]},
    {"id":"L02","cat":"법률","q":"육아휴직은 얼마나 쓸 수 있어요?","expect_type":"rag_answer","kw":["1년","육아휴직"]},
    {"id":"L03","cat":"법률","q":"퇴직금은 언제부터 받을 수 있어요?","expect_type":"rag_answer","kw":["1년","퇴직"]},
    {"id":"L04","cat":"법률","q":"배우자 출산휴가는 며칠이에요?","expect_type":"rag_answer","kw":["20일"]},
    {"id":"L05","cat":"법률","q":"연장근로 수당은 어떻게 계산해요?","expect_type":"rag_answer","kw":["50%","가산"]},
    {"id":"CL01","cat":"clarifying","q":"연차","expect_type":"any","kw":["①","신청 방법"]},
    {"id":"CL02","cat":"clarifying","q":"경비","expect_type":"any","kw":["경비","어떤"]},
    {"id":"CL03","cat":"clarifying","q":"급여","expect_type":"any","kw":["급여","어떤"]},
    {"id":"CL04","cat":"clarifying","q":"계정","expect_type":"any","kw":["①","생성"]},
    {"id":"CL05","cat":"clarifying","q":"복리후생","expect_type":"any","kw":["①","복지 포인트"]},
]

ALL_TESTS = INSCOPE + OUTSCOPE + COMPOSITE + EXTRA  # 50+20+15+15 = 100


# ══════════════════════════════════════════════════════════════════
# 인프라 확인
# ══════════════════════════════════════════════════════════════════

def _check_redis() -> bool:
    """BE Redis write/read/del 확인 (be_client 직접 사용)."""
    try:
        from core.be_client import cache_set, cache_get, cache_del
        ns, key, val = "e2e_infra", "ping", "pong"
        cache_set(ns, key, val, ttl_seconds=60)
        result = cache_get(ns, key)
        cache_del(ns, key)
        ok = result == val
        print(f"  {'✅' if ok else '❌'} Redis     {'정상' if ok else f'응답 이상: {result}'}")
        return ok
    except Exception as e:
        print(f"  ❌ Redis     연결 실패: {e}")
        return False


def _snapshot_unanswered() -> int:
    """현재 미답변 개수 반환 (RMQ nudge 전/후 비교용)."""
    try:
        r = requests.get(f"{BASE_URL}/knowledge/unanswered",
                         headers={"X-API-Key": API_KEY}, timeout=5)
        return len(r.json())
    except Exception:
        return -1


def _check_rmq() -> bool:
    """nudge task 직접 enqueue → status 조회로 RMQ 동작 확인."""
    try:
        from core.be_client import enqueue_nudge, get_task_status
        result = enqueue_nudge(
            user_id="e2e_infra_test",
            company_code=COMPANY_CODE,
            question="[인프라 테스트용 더미 질문]",
            question_id="e2e-infra-0",
        )
        if not result:
            print("  ❌ RMQ       enqueue 실패 (None 반환)")
            return False
        task_id = result.get("taskId")
        if not task_id:
            print(f"  ❌ RMQ       taskId 없음: {result}")
            return False
        for _ in range(6):
            time.sleep(0.5)
            status = get_task_status(task_id)
            if status and status.get("status") in ("PENDING", "RUNNING", "SUCCESS"):
                print(f"  ✅ RMQ       정상 (taskId={task_id}, status={status.get('status')})")
                return True
        print(f"  ⚠️  RMQ       task 상태 미확인 (taskId={task_id})")
        return False
    except Exception as e:
        print(f"  ❌ RMQ       연결 실패: {e}")
        return False


def _report_no_result(pre: int, no_result_count: int) -> None:
    """E2E 후 no_result 발생 건수 및 로컬 미답변 저장 현황 출력 (참고용)."""
    post = _snapshot_unanswered()
    delta = (post - pre) if post >= 0 else "?"
    print(f"  ℹ️  no_result  {no_result_count}건 발생 / 로컬 미답변 +{delta}건 저장 (전체 {post}건)")


# ══════════════════════════════════════════════════════════════════
# 실행 로직
# ══════════════════════════════════════════════════════════════════

def run_one(item: dict, q_num: int) -> dict:
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
        "conversationHistory": [],
    }
    headers = {"Content-Type": "application/json", "X-API-Key": API_KEY}

    start = time.time()
    try:
        r = requests.post(f"{BASE_URL}/internal/ai/answer", json=payload, headers=headers, timeout=40)
        latency = round(time.time() - start, 2)
        data = r.json()
    except Exception as e:
        return {**item, "passed": False, "reason": f"ERROR: {e}", "latency": 0, "messageType": "error", "preview": ""}

    msg_type = data.get("messageType", "")
    content  = data.get("content", "")
    expect   = item["expect_type"]
    kw       = item.get("kw", [])
    top_kw   = item.get("top_kw", [])

    kw_ok  = (not kw) or any(k in content for k in kw)
    top_ok = (not top_kw) or any(k in content[:200] for k in top_kw)

    if expect == "rag_answer":
        passed = (msg_type == "rag_answer") and kw_ok and top_ok
    elif expect == "out_of_scope":
        passed = (msg_type == "out_of_scope")
    elif expect == "sensitive_pass":
        passed = (msg_type != "out_of_scope")
    else:  # "any"
        passed = kw_ok

    reason = ""
    if expect == "rag_answer":
        if msg_type != "rag_answer":
            reason += f"messageType={msg_type} "
        if not kw_ok:
            reason += f"kw미포함{kw} "
        if not top_ok:
            reason += f"앞200자에 {top_kw} 없음"
    elif expect == "out_of_scope" and not passed:
        reason = f"messageType={msg_type}"
    elif expect == "sensitive_pass" and not passed:
        reason = "out_of_scope로 처리됨(민감 분기 미동작)"
    elif expect == "any" and not passed:
        reason = f"kw미포함{kw}"

    return {
        **item,
        "passed": passed,
        "reason": reason.strip(),
        "messageType": msg_type,
        "latency": latency,
        "preview": content[:80].replace("\n", " "),
    }


def main():
    assert len(ALL_TESTS) == 100, f"테스트 케이스 수 오류: {len(ALL_TESTS)}건"

    print(f"\n{'='*65}")
    print(f"  E2E 통합 테스트 100문항 — {COMPANY_CODE} ({COMPANY_NAME})")
    print(f"  서버: {BASE_URL}")
    print(f"{'='*65}")

    print("\n[인프라 사전 확인]")
    _check_redis()
    _check_rmq()
    pre_unanswered = _snapshot_unanswered()
    print(f"  ℹ️  미답변     기존 {pre_unanswered}건\n")

    results = []
    cat_stats: dict[str, dict] = {}

    for i, item in enumerate(ALL_TESTS, 1):
        label = f"[{item['id']}] {item['q'][:38]}"
        print(f"{label:<46}", end=" ", flush=True)
        result = run_one(item, i)
        results.append(result)

        status = "✅" if result["passed"] else "❌"
        print(f"{status}  {result['messageType']:<14} ({result['latency']}s)")
        if not result["passed"]:
            print(f"     └ {result['reason']}")

        cat = item["cat"]
        cs = cat_stats.setdefault(cat, {"pass": 0, "total": 0})
        cs["total"] += 1
        if result["passed"]:
            cs["pass"] += 1

        time.sleep(0.3)

    # ── 요약 ─────────────────────────────────────────────────────
    total  = len(results)
    passed = sum(1 for r in results if r["passed"])
    rate   = round(passed / total * 100, 1)

    print(f"\n{'='*65}")
    print(f"  최종 정답률: {passed}/{total} ({rate}%)")
    print(f"{'='*65}")

    # messageType 분포
    type_counts: dict[str, int] = {}
    for r in results:
        type_counts[r["messageType"]] = type_counts.get(r["messageType"], 0) + 1
    print("\nmessageType 분포:")
    for t, c in sorted(type_counts.items()):
        print(f"  {t:<20}: {c}건")

    # 카테고리별
    print("\n카테고리별 정답률:")
    for cat, s in cat_stats.items():
        cr  = round(s["pass"] / s["total"] * 100, 1)
        bar = "█" * s["pass"] + "░" * (s["total"] - s["pass"])
        print(f"  {cat:<16} {bar} {s['pass']}/{s['total']} ({cr}%)")

    fails = [r for r in results if not r["passed"]]
    if fails:
        print(f"\nFAIL 목록 ({len(fails)}건):")
        for r in fails:
            print(f"  [{r['id']}] {r['q'][:48]} → {r['reason']}")

    no_result_count = type_counts.get("no_result", 0)
    print("\n[인프라 사후 확인]")
    _report_no_result(pre_unanswered, no_result_count)

    # JSON 저장
    out = {
        "date": time.strftime("%Y-%m-%d %H:%M"),
        "company": COMPANY_CODE,
        "server": BASE_URL,
        "total": total,
        "passed": passed,
        "rate": rate,
        "type_distribution": type_counts,
        "results": results,
    }
    fname = f"scripts/test_e2e_all_result_{time.strftime('%Y%m%d_%H%M')}.json"
    with open(fname, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {fname}")


if __name__ == "__main__":
    main()
