"""
RAG 시스템 성능 평가 스크립트
────────────────────────────────────────────
테스트 질문셋으로 RAG 파이프라인 성능을 측정합니다.

[측정 지표]
  1. Retrieval Hit Rate     - 검색된 문서에 관련 소스가 포함된 비율
  2. Keyword Hit Rate       - 답변에 기대 키워드가 포함된 비율
  3. LLM Judge Score        - Claude가 1~5점으로 평가한 답변 품질
  4. Unanswered Rate        - 미답변 비율
  5. Avg Response Time (ms) - 평균 응답 시간

[사용법]
  cd ai/
  python scripts/evaluate.py
  python scripts/evaluate.py --output ./data/eval_result.json
  python scripts/evaluate.py --chroma_dir C:/withbuddy_chroma_db
"""

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv
load_dotenv()

from core.llm import get_llm
from core.vectorstore import get_retriever
from chains.rag_chain import run_rag_chain
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

# ── 미답변 키워드 (rag_chain.py 와 동일) ────────────────────────
_NO_ANSWER_KEYWORDS = [
    "문서에서 확인되지", "관련 정보를 찾을 수 없", "확인되지 않습니다",
    "답변하기 어렵", "안내가 없습니다", "내용이 없습니다", "보유한 문서에는",
    "문서에는", "찾을 수 없습니다", "포함되어 있지 않", "정보가 없",
]

# ── 테스트 질문셋 ─────────────────────────────────────────────
TEST_CASES = [
    # ── HR: 인사·근태 (Q1~Q13) ────────────────────────
    {
        "category": "HR",
        "question": "연차는 입사하고 언제부터 생겨요?",
        "expected_keywords": ["1개월", "1일"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "입사 첫 달에 연차가 며칠이나 쌓여요?",
        "expected_keywords": ["1일", "Flex"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "수습 기간에도 연차가 생기나요?",
        "expected_keywords": ["수습", "1일"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "갑자기 몸이 아파서 못 나갈 것 같아요. 아침에 연락하고 연차 써도 되나요?",
        "expected_keywords": ["팀장", "Flex"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "반차랑 반반차 차이가 뭐예요?",
        "expected_keywords": ["0.5", "0.25"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "오전 반차 쓰면 몇 시에 출근해야 해요?",
        "expected_keywords": ["2시"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "수습 기간이 얼마나 돼요? 그동안 급여는 어떻게 되나요?",
        "expected_keywords": ["3개월", "100%"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "재택근무는 어떻게 신청해요? 일주일에 몇 번까지 돼요?",
        "expected_keywords": ["Flex", "2회", "팀장"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "오늘 7시부터 10시까지 야근했는데 수당이 나오나요? 계산법이 궁금해요.",
        "expected_keywords": ["150%", "팀장"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "야근하면 밥값도 나와요?",
        "expected_keywords": ["8시", "1만원"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt", "techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "HR",
        "question": "급여일이 언제예요?",
        "expected_keywords": ["25일", "영업일"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "재직증명서는 어떻게 발급받아요?",
        "expected_keywords": ["Flex", "PDF"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "HR",
        "question": "퇴사하려면 얼마나 전에 말해야 해요?",
        "expected_keywords": ["30일", "Flex"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },

    # ── ADMIN: 행정·오피스 (Q14~Q25) ─────────────────
    {
        "category": "ADMIN",
        "question": "지난주에 산 비품비 정산하고 싶은데, Flex에서 하나요 Notion에서 하나요?",
        "expected_keywords": ["Notion", "경비정산서"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "점심 식대 영수증 제출해야 하나요?",
        "expected_keywords": ["복지카드", "불필요"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt", "techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "ADMIN",
        "question": "야근 식대랑 회식비는 뭐가 달라요?",
        "expected_keywords": ["1만원", "실비"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt", "techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "ADMIN",
        "question": "비품 신청은 어떻게 해요? A4 용지는 어디 있어요?",
        "expected_keywords": ["탕비실", "Notion", "화요일"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "회의실은 어디서 예약해요?",
        "expected_keywords": ["Google Calendar"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "회의실 C는 왜 따로 승인받아야 해요?",
        "expected_keywords": ["15인", "중복"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "경비 정산 적요란은 어떻게 써요?",
        "expected_keywords": ["형식"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "출장비는 어떻게 정산해요?",
        "expected_keywords": ["Notion", "5영업일", "실비"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "법인카드 쓰고 나서 뭘 해야 해요?",
        "expected_keywords": ["3영업일", "Notion", "경비정산서"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "영수증 없이 경비 정산할 수 있나요?",
        "expected_keywords": ["불가", "영수증", "Notion"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "명함은 어떻게 만들어요?",
        "expected_keywords": ["jisoo", "5~7 영업일"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },
    {
        "category": "ADMIN",
        "question": "비품함은 몇 층에 있어요?",
        "expected_keywords": ["3층", "탕비실"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },

    # ── WELFARE: 복리후생 (Q26~Q38) ──────────────────
    {
        "category": "WELFARE",
        "question": "복지카드는 언제부터 쓸 수 있어요?",
        "expected_keywords": ["1개월", "Flex"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "복지카드 한도가 얼마예요? 식대도 포함이에요?",
        "expected_keywords": ["20만원", "10만원"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "복지카드 잔액이 남으면 다음 달에 이어서 쓸 수 있나요?",
        "expected_keywords": ["이월", "소멸"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "복지카드는 어디에 쓸 수 있고, 어디에는 못 쓰나요?",
        "expected_keywords": ["식비", "현금"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "입사 첫날 점심은 어떻게 해요?",
        "expected_keywords": ["버디", "법인카드"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
    },
    {
        "category": "WELFARE",
        "question": "계약직도 복지카드 받을 수 있나요?",
        "expected_keywords": ["1개월", "15시간"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "건강검진은 언제부터 받을 수 있어요? 어떻게 신청해요?",
        "expected_keywords": ["3개월", "10만원", "Flex"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "경조금은 어떤 경우에 받을 수 있어요?",
        "expected_keywords": ["결혼", "5영업일"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "생일에 뭔가 특별한 게 있나요?",
        "expected_keywords": ["2시", "조기 퇴근"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "도서 구입비 지원이 있나요? 어떻게 신청해요?",
        "expected_keywords": ["3만원", "Notion"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "온라인 강의 수강하면 지원받을 수 있나요?",
        "expected_keywords": ["30만원", "3개월"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "단체보험은 언제부터 적용돼요?",
        "expected_keywords": ["입사일", "회사"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
    },
    {
        "category": "WELFARE",
        "question": "출입카드는 어떻게 발급받아요? 입사했을 때 받는 방법이 궁금해요.",
        "expected_keywords": ["입사 당일", "경영지원"],
        "expected_sources": ["techco_ADMIN_규정_v4.1.txt", "ADMIN.txt"],
    },

    # ── IT: IT 환경 (Q39~Q50) ─────────────────────────
    {
        "category": "IT",
        "question": "입사 첫날 노트북 세팅은 어떻게 해요?",
        "expected_keywords": ["Google", "비밀번호", "MFA"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "회사 이메일 계정 첫 로그인은 어떻게 해요?",
        "expected_keywords": ["임시 비밀번호"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "비밀번호는 얼마나 자주 바꿔야 해요?",
        "expected_keywords": ["90일", "10자"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "MFA 설정은 어떻게 해요?",
        "expected_keywords": ["Google Authenticator", "QR"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "재택근무나 외부에서 접속할 때 VPN은 어떻게 연결하나요?",
        "expected_keywords": ["VPN", "vpn.techco.co.kr"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "VPN이 연결이 안 될 때 어떻게 해요?",
        "expected_keywords": ["#help-it"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "Slack 채널에 어떻게 초대받아요?",
        "expected_keywords": ["minjun.park", "공개 채널"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "Slack에서 확인했다는 걸 어떻게 표시해요?",
        "expected_keywords": ["리액션"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "Notion 권한은 어떻게 받아요?",
        "expected_keywords": ["minjun.park", "1영업일"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "Jira 계정은 어떻게 신청해요?",
        "expected_keywords": ["minjun.park", "1영업일"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "업무에 필요한 소프트웨어 설치하고 싶은데 어떻게 해요?",
        "expected_keywords": ["#help-it", "무단 설치"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
    {
        "category": "IT",
        "question": "퇴근할 때 노트북은 어떻게 해야 해요?",
        "expected_keywords": ["화면 잠금", "5분"],
        "expected_sources": ["techco_IT_규정_v3.1.txt", "IT.txt"],
    },
]

# ── 회사 특화 vs 법령 충돌 시나리오 ─────────────────────────────
CONFLICT_CASES = [
    # ── C: 회사 우위 ──────────────────────────────────
    {
        "id": "C-01", "type": "회사우위",
        "question": "수습 기간에 월급이 깎이나요?",
        "induced_question": "법적으로는 수습 때 90%만 줘도 된다는데, 여기도 그런가요?",
        "expected_keywords": ["100%", "감액"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": ["90%"],
    },
    {
        "id": "C-02", "type": "회사우위",
        "question": "배우자 출산휴가가 며칠이에요?",
        "induced_question": "배우자 출산휴가가 법적으로 10일이라고 들었는데, 여기도 10일인가요?",
        "expected_keywords": ["20일"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": ["10일"],
    },
    {
        "id": "C-03", "type": "회사우위",
        "question": "1년 지나면 연차가 몇 개 생겨요? 계속 다니면 더 늘어나나요?",
        "induced_question": "연차가 법적으로 15일이면 그게 전부인가요? 더 쌓이지는 않나요?",
        "expected_keywords": ["15일", "25일", "가산"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "C-04", "type": "회사우위",
        "question": "결혼하면 휴가가 며칠이나 돼요?",
        "induced_question": "법적으로 경조사 휴가가 정해진 게 없다고 하던데, 여기는 따로 규정이 있나요?",
        "expected_keywords": ["5일"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "C-05", "type": "회사우위",
        "question": "퇴직하면 퇴직금이 언제 나와요?",
        "induced_question": "퇴직금은 법적으로 14일 안에 주면 된다는데, 여기도 2주 걸려요?",
        "expected_keywords": ["7영업일"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": ["14일"],
    },
    # ── A: 회사 추가 제공 ──────────────────────────────
    {
        "id": "A-01", "type": "회사추가",
        "question": "복지카드 한도가 얼마예요?",
        "induced_question": "복지카드가 법적으로 의무인가요, 회사가 자체적으로 주는 건가요?",
        "expected_keywords": ["20만원", "10만원"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "A-02", "type": "회사추가",
        "question": "야근하면 밥값이 나오나요?",
        "induced_question": "야근 식대가 법적으로 의무는 아니잖아요. 여기는 따로 지원이 있나요?",
        "expected_keywords": ["8시", "1만원"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
        "fail_keywords": ["150%"],
        "induced_fail_keywords": [],
    },
    {
        "id": "A-03", "type": "회사추가",
        "question": "생일에 뭔가 특별한 게 있나요?",
        "induced_question": "생일 혜택은 법적으로 보장된 게 아니죠? 여기는 따로 있나요?",
        "expected_keywords": ["2시", "조기 퇴근", "팀장"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "A-04", "type": "회사추가",
        "question": "책이나 온라인 강의 비용을 회사에서 지원해주나요?",
        "induced_question": "이런 자기계발 지원이 법적 의무는 아닌 거죠?",
        "expected_keywords": ["3만원", "30만원", "3개월"],
        "expected_sources": ["techco_WELFARE_규정_v3.1.txt", "WELFARE.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    # ── F: Fallback ────────────────────────────────────
    {
        "id": "F-01", "type": "Fallback",
        "question": "출산휴가가 며칠이나 되나요?",
        "induced_question": None,
        "expected_keywords": ["90일", "60일"],
        "expected_sources": ["근로기준법.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "F-02", "type": "Fallback",
        "question": "육아휴직 중에 급여가 나오나요? 얼마나 받을 수 있어요?",
        "induced_question": None,
        "expected_keywords": ["80%", "고용보험"],
        "expected_sources": ["남녀고용평등법.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "F-03", "type": "Fallback",
        "question": "난임 치료를 받아야 하는데 휴가를 쓸 수 있나요?",
        "induced_question": None,
        "expected_keywords": ["3일", "1일"],
        "expected_sources": ["남녀고용평등법.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "F-04", "type": "Fallback",
        "question": "급여가 제때 안 들어왔는데 어떻게 해야 하나요?",
        "induced_question": None,
        "expected_keywords": ["경영지원", "1350"],
        "expected_sources": ["근로기준법.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    # ── P: 절차 차이 ───────────────────────────────────
    {
        "id": "P-01", "type": "절차차이",
        "question": "연차가 언제 생기고 어디서 확인해요?",
        "induced_question": "연차는 법적으로 월 1일씩 생기는 거잖아요. 따로 신청해야 하나요?",
        "expected_keywords": ["1개월", "Flex"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "P-02", "type": "절차차이",
        "question": "야근 수당은 어떻게 계산되고 어떻게 받아요?",
        "induced_question": "야근 수당이 법적으로 1.5배잖아요. 신청 같은 건 따로 안 해도 자동으로 받을 수 있죠?",
        "expected_keywords": ["150%", "팀장", "Flex"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
    {
        "id": "P-03", "type": "절차차이",
        "question": "퇴직금은 어떻게 받을 수 있어요?",
        "induced_question": "퇴직금은 법적으로 1년 이상 일하면 자동으로 주는 거죠?",
        "expected_keywords": ["1년", "15시간", "Flex"],
        "expected_sources": ["techco_HR_규정_v4.1.txt", "HR.txt"],
        "fail_keywords": [],
        "induced_fail_keywords": [],
    },
]

# ── LLM Judge 프롬프트 ─────────────────────────────────────────
_JUDGE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 회사 내부 RAG 시스템의 답변 품질을 평가하는 전문 평가자입니다.
아래 질문과 답변을 보고 1~5점으로 평가하세요.

[중요 전제]
- 이 시스템은 특정 회사의 내부 사규/규정 문서를 기반으로 답변합니다.
- 회사 특정 정보(회사명, 내부 시스템명, 담당자 등)가 포함된 답변은 정상이며 감점 대상이 아닙니다.
- 질문에 대해 회사 내부 기준으로 구체적으로 답변했다면 높은 점수를 부여하세요.

[평가 기준]
5점: 질문에 완벽히 답변하고 있으며 정보가 정확하고 구체적임
4점: 질문에 잘 답변하고 있으나 일부 세부 정보가 부족함
3점: 질문에 부분적으로 답변하고 있음
2점: 질문과 관련은 있으나 핵심 답변이 누락됨
1점: 질문에 전혀 답변하지 못하거나 완전히 엉뚱한 답변

반드시 아래 JSON 형식으로만 답변하세요:
{{"score": 숫자, "reason": "한 줄 이유"}}"""),
    ("human", "질문: {question}\n\n답변: {answer}"),
])


# ── WB0002(스튜디오 프리즘) 테스트 케이스 ────────────────────────
WB0002_TEST_CASES = [
    # ── HR: 인사·근태 ────────────────────────
    {
        "category": "HR",
        "question": "수습 기간이 얼마나 돼요?",
        "expected_keywords": ["2개월"],
        "expected_sources": ["prism_HR_v1.0.md"],
    },
    {
        "category": "HR",
        "question": "급여일이 언제예요?",
        "expected_keywords": ["20일"],
        "expected_sources": ["prism_HR_v1.0.md"],
    },
    {
        "category": "HR",
        "question": "연차는 어떻게 신청해요?",
        "expected_keywords": ["Gmail", "PDF"],
        "expected_sources": ["prism_HR_v1.0.md"],
    },
    {
        "category": "HR",
        "question": "재택근무 신청은 어떻게 해요?",
        "expected_keywords": ["팀장", "PDF"],
        "expected_sources": ["prism_HR_v1.0.md"],
    },
    {
        "category": "HR",
        "question": "연차는 언제부터 생겨요?",
        "expected_keywords": ["매월", "1일"],
        "expected_sources": ["prism_HR_v1.0.md"],
    },
    # ── WELFARE: 복지·식대 ────────────────────────
    {
        "category": "WELFARE",
        "question": "야근 식대가 얼마예요?",
        "expected_keywords": ["12,000원"],
        "expected_sources": ["prism_WELFARE_v1.0.md"],
    },
    {
        "category": "WELFARE",
        "question": "야근 식대는 몇 시 이후부터 지원돼요?",
        "expected_keywords": ["20:30"],
        "expected_sources": ["prism_WELFARE_v1.0.md"],
    },
    {
        "category": "WELFARE",
        "question": "복지 혜택이 어떻게 되나요?",
        "expected_keywords": ["문화상품권", "5만원"],
        "expected_sources": ["prism_WELFARE_v1.0.md"],
    },
    {
        "category": "WELFARE",
        "question": "경비 청구는 언제까지 제출해야 해요?",
        "expected_keywords": ["20일"],
        "expected_sources": ["prism_ADMIN_v1.0.md"],
    },
    # ── IT: 계정·장비 ────────────────────────
    {
        "category": "IT",
        "question": "프로젝트 관리 툴이 뭐예요?",
        "expected_keywords": ["Notion"],
        "expected_sources": ["prism_IT_v1.0.md"],
    },
    {
        "category": "IT",
        "question": "어떤 장비가 지급되나요?",
        "expected_keywords": ["Mac"],
        "expected_sources": ["prism_IT_v1.0.md"],
    },
    {
        "category": "IT",
        "question": "IT 문제가 생기면 누구한테 연락해요?",
        "expected_keywords": ["박소연"],
        "expected_sources": ["prism_IT_v1.0.md"],
    },
    # ── CREATIVE: 저작권·자산 ────────────────────────
    {
        "category": "CREATIVE",
        "question": "폰트는 어디서 받아요?",
        "expected_keywords": ["산돌구름"],
        "expected_sources": ["prism_CREATIVE_ASSET_v1.0.md"],
    },
    {
        "category": "CREATIVE",
        "question": "포트폴리오에 작업물 올려도 되나요?",
        "expected_keywords": ["고객사 공개"],
        "expected_sources": ["prism_CREATIVE_ASSET_v1.0.md"],
    },
    # ── EQUIPMENT: 촬영 장비 ────────────────────────
    {
        "category": "EQUIPMENT",
        "question": "촬영 장비 빌리려면 어떻게 해요?",
        "expected_keywords": ["장비 대여 장부", "minji.cho"],
        "expected_sources": ["prism_EQUIPMENT_v1.0.md"],
    },
]

# ── WB0002 멀티테넌시 격리 검증 케이스 ────────────────────────────
# 회사 A(WB0001) 정보가 회사 B(WB0002) 사용자 답변에 섞이면 FAIL
WB0002_ISOLATION_CASES = [
    {
        "id": "ISO-01", "type": "격리검증",
        "question": "복지카드 어디서 신청해요?",
        "induced_question": None,
        "expected_keywords": ["문화상품권", "없습니다"],
        "expected_sources": ["prism_WELFARE_v1.0.md"],
        "fail_keywords": ["Flex", "복지카드 신청"],
        "induced_fail_keywords": [],
    },
    {
        "id": "ISO-02", "type": "격리검증",
        "question": "Jira 권한 주세요",
        "induced_question": None,
        "expected_keywords": ["Notion", "사용하지 않습니다"],
        "expected_sources": ["prism_IT_v1.0.md"],
        "fail_keywords": ["Jira 권한", "Jira 사용"],
        "induced_fail_keywords": [],
    },
    {
        "id": "ISO-03", "type": "격리검증",
        "question": "VPN 연결이 안 돼요",
        "induced_question": None,
        "expected_keywords": ["미운영", "운영하지 않습니다"],
        "expected_sources": ["prism_IT_v1.0.md"],
        "fail_keywords": ["자가진단", "VPN 연결 방법"],
        "induced_fail_keywords": [],
    },
    {
        "id": "ISO-04", "type": "격리검증",
        "question": "연차 어디서 신청해요?",
        "induced_question": None,
        "expected_keywords": ["Gmail", "PDF"],
        "expected_sources": ["prism_HR_v1.0.md"],
        "fail_keywords": ["Flex에서"],
        "induced_fail_keywords": [],
    },
    {
        "id": "ISO-05", "type": "격리검증",
        "question": "급여일이 언제예요?",
        "induced_question": None,
        "expected_keywords": ["20일"],
        "expected_sources": ["prism_HR_v1.0.md"],
        "fail_keywords": ["25일"],
        "induced_fail_keywords": [],
    },
    {
        "id": "ISO-06", "type": "격리검증",
        "question": "야근 식대 얼마예요?",
        "induced_question": None,
        "expected_keywords": ["12,000원"],
        "expected_sources": ["prism_WELFARE_v1.0.md"],
        "fail_keywords": ["1만원", "10,000원"],
        "induced_fail_keywords": [],
    },
    {
        "id": "ISO-07", "type": "격리검증",
        "question": "수습 기간이 얼마예요?",
        "induced_question": None,
        "expected_keywords": ["2개월"],
        "expected_sources": ["prism_HR_v1.0.md"],
        "fail_keywords": ["3개월"],
        "induced_fail_keywords": [],
    },
]


def evaluate_retrieval(question: str, expected_sources: list[str], k: int = 5, company_code: str = "") -> dict:
    """검색된 문서에 기대 소스가 포함되는지 확인합니다."""
    if not expected_sources:
        return {"hit": None, "retrieved_sources": [], "note": "소스 미지정"}

    retriever = get_retriever(k=k, company_code=company_code)
    docs = retriever.invoke(question)
    retrieved = [
        os.path.splitext(os.path.basename(d.metadata.get("source", "")))[0].replace("+", " ")
        for d in docs
    ]

    hit = any(
        any(exp.replace("_", " ") in r or r in exp.replace("_", " ") for r in retrieved)
        for exp in expected_sources
    )
    return {"hit": hit, "retrieved_sources": retrieved[:5]}


def evaluate_keywords(answer: str, expected_keywords: list[str]) -> dict:
    """답변에 기대 키워드가 포함되는지 확인합니다."""
    hits = [kw for kw in expected_keywords if kw in answer]
    rate = len(hits) / len(expected_keywords) if expected_keywords else 0
    return {"keyword_hit_rate": rate, "hits": hits, "misses": [k for k in expected_keywords if k not in hits]}


def evaluate_with_llm_judge(question: str, answer: str) -> dict:
    """Claude를 이용해 답변 품질을 1~5점으로 평가합니다."""
    try:
        chain = _JUDGE_PROMPT | get_llm() | StrOutputParser()
        result = chain.invoke({"question": question, "answer": answer})
        result = result.strip()
        json_match = re.search(r'\{.*?\}', result, re.DOTALL)
        if json_match:
            parsed = json.loads(json_match.group())
        else:
            parsed = json.loads(result)
        return {"score": parsed.get("score", 0), "reason": parsed.get("reason", "")}
    except Exception as e:
        return {"score": 0, "reason": f"평가 실패: {e}"}


def is_unanswered(answer: str) -> bool:
    return any(kw in answer for kw in _NO_ANSWER_KEYWORDS)


def run_evaluation(chroma_dir: str = "C:/withbuddy_chroma_db", company_code: str = "") -> dict:
    """전체 테스트셋에 대해 평가를 실행합니다."""
    # ChromaDB 경로 임시 패치
    import core.vectorstore as vs
    vs.CHROMA_DB_PATH = chroma_dir
    vs.get_vectorstore.cache_clear()
    vs.get_retriever.cache_clear()

    # company_code에 따라 테스트 케이스 선택
    is_wb0002 = company_code.upper() == "WB0002"
    test_cases = WB0002_TEST_CASES if is_wb0002 else TEST_CASES
    conflict_cases = WB0002_ISOLATION_CASES if is_wb0002 else CONFLICT_CASES

    print("=" * 55)
    print("  With Buddy RAG 시스템 성능 평가")
    print(f"  실행 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  테스트 케이스: {len(test_cases)}개")
    print("=" * 55)

    results = []
    category_stats: dict[str, list] = {}

    for i, tc in enumerate(test_cases, 1):
        q = tc["question"]
        cat = tc["category"]
        print(f"\n[{i:02d}/{len(test_cases)}] [{cat}] {q}")

        # ① RAG 답변 생성 + 시간 측정
        t0 = time.time()
        try:
            answer, source, _, _doc_ids = run_rag_chain("eval_user", q, company_code=company_code)
        except Exception as e:
            answer, source = f"오류: {e}", ""
        elapsed_ms = int((time.time() - t0) * 1000)

        # ② 검색 평가
        retrieval = evaluate_retrieval(q, tc["expected_sources"], company_code=company_code)

        # ③ 키워드 평가
        keyword = evaluate_keywords(answer, tc["expected_keywords"])

        # ④ LLM Judge
        judge = evaluate_with_llm_judge(q, answer)

        # ⑤ 미답변 여부
        unanswered = is_unanswered(answer)

        result = {
            "id": i,
            "category": cat,
            "question": q,
            "answer": answer[:200] + "..." if len(answer) > 200 else answer,
            "source": source,
            "retrieval_hit": retrieval["hit"],
            "retrieved_sources": retrieval["retrieved_sources"],
            "keyword_hit_rate": keyword["keyword_hit_rate"],
            "keyword_hits": keyword["hits"],
            "keyword_misses": keyword["misses"],
            "llm_judge_score": judge["score"],
            "llm_judge_reason": judge["reason"],
            "unanswered": unanswered,
            "response_time_ms": elapsed_ms,
        }
        results.append(result)

        if cat not in category_stats:
            category_stats[cat] = []
        category_stats[cat].append(result)

        # 콘솔 출력
        hit_str = "✅" if retrieval["hit"] else ("⬜" if retrieval["hit"] is None else "❌")
        print(f"       검색히트: {hit_str}  키워드: {keyword['keyword_hit_rate']*100:.0f}%  "
              f"Judge: {judge['score']}/5  미답변: {'예' if unanswered else '아니오'}  {elapsed_ms}ms")
        print(f"       이유: {judge['reason']}")

    # ── 전체 통계 ──────────────────────────────────────────────
    answered = [r for r in results if r["retrieval_hit"] is not None]
    retrieval_hit_rate = sum(1 for r in answered if r["retrieval_hit"]) / len(answered) if answered else 0
    avg_keyword_hit = sum(r["keyword_hit_rate"] for r in results) / len(results)
    avg_judge_score = sum(r["llm_judge_score"] for r in results if r["llm_judge_score"] > 0) / max(1, sum(1 for r in results if r["llm_judge_score"] > 0))
    unanswered_rate = sum(1 for r in results if r["unanswered"]) / len(results)
    avg_response_ms = sum(r["response_time_ms"] for r in results) / len(results)

    # 카테고리별 통계
    category_summary = {}
    for cat, cat_results in category_stats.items():
        cat_answered = [r for r in cat_results if r["retrieval_hit"] is not None]
        category_summary[cat] = {
            "count": len(cat_results),
            "retrieval_hit_rate": sum(1 for r in cat_answered if r["retrieval_hit"]) / len(cat_answered) if cat_answered else None,
            "avg_keyword_hit": sum(r["keyword_hit_rate"] for r in cat_results) / len(cat_results),
            "avg_judge_score": sum(r["llm_judge_score"] for r in cat_results) / len(cat_results),
            "unanswered_count": sum(1 for r in cat_results if r["unanswered"]),
        }

    summary = {
        "evaluated_at": datetime.now().isoformat(),
        "total_cases": len(TEST_CASES),
        "chroma_dir": chroma_dir,
        "metrics": {
            "retrieval_hit_rate": round(retrieval_hit_rate, 4),
            "avg_keyword_hit_rate": round(avg_keyword_hit, 4),
            "avg_llm_judge_score": round(avg_judge_score, 4),
            "unanswered_rate": round(unanswered_rate, 4),
            "avg_response_time_ms": round(avg_response_ms, 1),
        },
        "category_summary": category_summary,
        "details": results,
    }

    # ── 결과 출력 ──────────────────────────────────────────────
    print("\n" + "=" * 55)
    print("  📊 평가 결과 요약")
    print("=" * 55)
    print(f"  Retrieval Hit Rate    : {retrieval_hit_rate*100:.1f}%")
    print(f"  Keyword Hit Rate      : {avg_keyword_hit*100:.1f}%")
    print(f"  LLM Judge Score (1~5) : {avg_judge_score:.2f}")
    print(f"  Unanswered Rate       : {unanswered_rate*100:.1f}%")
    print(f"  Avg Response Time     : {avg_response_ms:.0f}ms")
    print()
    print("  📂 카테고리별 Judge Score")
    print("  " + "-" * 40)
    for cat, stat in category_summary.items():
        judge_str = f"{stat['avg_judge_score']:.1f}/5"
        unanswered_str = f"  (미답변 {stat['unanswered_count']}건)" if stat['unanswered_count'] else ""
        print(f"  {cat:<14} {judge_str}{unanswered_str}")
    print("=" * 55)

    # ── 충돌/격리 시나리오 평가 ──────────────────────────────────
    print("\n" + "=" * 55)
    if is_wb0002:
        print("  🔒  멀티테넌시 격리 검증")
    else:
        print("  ⚖️  회사 특화 vs 법령 충돌 시나리오 평가")
    print(f"  케이스: {len(conflict_cases)}개")
    print("=" * 55)

    conflict_results = []
    for cc in conflict_cases:
        cid = cc["id"]
        ctype = cc["type"]

        for question_type, q in [("일반", cc["question"]), ("유도", cc.get("induced_question"))]:
            if q is None:
                continue

            print(f"\n[{cid}] [{ctype}] [{question_type}] {q}")

            t0 = time.time()
            try:
                answer, source, _, _doc_ids = run_rag_chain("eval_user", q, company_code=company_code)
            except Exception as e:
                answer, source = f"오류: {e}", ""
            elapsed_ms = int((time.time() - t0) * 1000)

            retrieval = evaluate_retrieval(q, cc["expected_sources"], company_code=company_code)
            keyword = evaluate_keywords(answer, cc["expected_keywords"])
            judge = evaluate_with_llm_judge(q, answer)

            fail_kws = cc["induced_fail_keywords"] if question_type == "유도" else cc["fail_keywords"]
            fail_triggered = [kw for kw in fail_kws if kw in answer]

            hit_str = "✅" if retrieval["hit"] else ("⬜" if retrieval["hit"] is None else "❌")
            fail_str = f"  ⚠️ FAIL 키워드 감지: {fail_triggered}" if fail_triggered else ""
            print(f"       검색히트: {hit_str}  키워드: {keyword['keyword_hit_rate']*100:.0f}%  "
                  f"Judge: {judge['score']}/5  {elapsed_ms}ms{fail_str}")
            print(f"       이유: {judge['reason']}")

            conflict_results.append({
                "id": cid,
                "type": ctype,
                "question_type": question_type,
                "question": q,
                "answer": answer[:200] + "..." if len(answer) > 200 else answer,
                "retrieval_hit": retrieval["hit"],
                "keyword_hit_rate": keyword["keyword_hit_rate"],
                "llm_judge_score": judge["score"],
                "llm_judge_reason": judge["reason"],
                "fail_triggered": fail_triggered,
                "response_time_ms": elapsed_ms,
            })

    summary["conflict_results"] = conflict_results

    return summary


def generate_html(summary: dict, html_path: str) -> None:
    m = summary["metrics"]
    details = summary["details"]
    evaluated_at = summary["evaluated_at"][:16].replace("T", " ")
    cat_summary = summary.get("category_summary", {})

    def score_color(score):
        if score >= 4: return "#22c55e"
        if score >= 3: return "#f59e0b"
        return "#ef4444"

    def pct_bar(rate, good_high=True):
        pct = int((rate or 0) * 100)
        color = "#22c55e" if (pct >= 70) == good_high else "#ef4444"
        return f'<div style="display:flex;align-items:center;gap:6px"><div style="background:#e2e8f0;border-radius:4px;width:80px;height:8px"><div style="background:{color};width:{pct}%;height:8px;border-radius:4px"></div></div><span>{pct}%</span></div>'

    metric_cards = f"""
    <div class="card"><div class="num" style="color:#6366f1">{m['retrieval_hit_rate']*100:.0f}%</div><div class="label">Retrieval Hit Rate</div></div>
    <div class="card"><div class="num" style="color:#0ea5e9">{m['avg_keyword_hit_rate']*100:.0f}%</div><div class="label">Keyword Hit Rate</div></div>
    <div class="card"><div class="num" style="color:{score_color(m['avg_llm_judge_score'])}">{m['avg_llm_judge_score']:.2f}<span style="font-size:14px">/5</span></div><div class="label">LLM Judge Score</div></div>
    <div class="card"><div class="num" style="color:{'#ef4444' if m['unanswered_rate']>0.1 else '#22c55e'}">{m['unanswered_rate']*100:.0f}%</div><div class="label">Unanswered Rate</div></div>
    <div class="card"><div class="num" style="color:#64748b">{m['avg_response_time_ms']:.0f}<span style="font-size:14px">ms</span></div><div class="label">Avg Response Time</div></div>
    """

    cat_rows = ""
    for cat, stat in cat_summary.items():
        judge = stat.get("avg_judge_score", 0)
        cat_rows += f"""<tr>
          <td>{cat}</td>
          <td>{pct_bar(stat.get('retrieval_hit_rate') or 0)}</td>
          <td>{pct_bar(stat.get('avg_keyword_hit'))}</td>
          <td style="color:{score_color(judge)};font-weight:bold">{judge:.1f}/5</td>
          <td style="color:{'#ef4444' if stat['unanswered_count']>0 else '#22c55e'}">{stat['unanswered_count']}건</td>
        </tr>"""

    conflict_rows = ""
    for r in summary.get("conflict_results", []):
        judge = r.get("llm_judge_score", 0)
        kw = r.get("keyword_hit_rate", 0)
        retrieval = r.get("retrieval_hit")
        hit_icon = "✅" if retrieval else ("⬜" if retrieval is None else "❌")
        fail_triggered = r.get("fail_triggered", [])
        fail_str = f"⚠️ {', '.join(fail_triggered)}" if fail_triggered else ""
        bg = "#fef2f2" if fail_triggered or judge <= 2 else ("#fffbeb" if judge == 3 else "white")
        conflict_rows += f"""<tr style="background:{bg}">
          <td style="text-align:center">{r['id']}</td>
          <td style="color:#64748b">{r.get('type','')}</td>
          <td style="color:#64748b">{r.get('question_type','')}</td>
          <td>{r['question']}</td>
          <td style="text-align:center">{hit_icon}</td>
          <td>{pct_bar(kw)}</td>
          <td style="text-align:center;color:{score_color(judge)};font-weight:bold">{judge}/5</td>
          <td style="color:#64748b;font-size:11px">{r.get('llm_judge_reason','')}</td>
          <td style="color:#ef4444;font-size:11px">{fail_str}</td>
        </tr>"""

    conflict_section = ""
    if conflict_rows:
        conflict_section = f"""
<h2>충돌 · 격리 시나리오</h2>
<table>
  <tr><th>ID</th><th>유형</th><th>질문 유형</th><th>질문</th><th>검색히트</th><th>키워드</th><th>Judge</th><th>판정이유</th><th>FAIL 키워드</th></tr>
  {conflict_rows}
</table>"""

    detail_rows = ""
    for r in details:
        judge = r.get("llm_judge_score", 0)
        kw = r.get("keyword_hit_rate", 0)
        unanswered = r.get("unanswered", False)
        retrieval = r.get("retrieval_hit")
        hit_icon = "✅" if retrieval else ("⬜" if retrieval is None else "❌")
        bg = "#fef2f2" if unanswered or judge <= 2 else ("#fffbeb" if judge == 3 else "white")
        detail_rows += f"""<tr style="background:{bg}">
          <td style="text-align:center">{r['id']}</td>
          <td style="color:#64748b">{r['category']}</td>
          <td>{r['question']}</td>
          <td style="text-align:center">{hit_icon}</td>
          <td>{pct_bar(kw)}</td>
          <td style="text-align:center;color:{score_color(judge)};font-weight:bold">{judge}/5</td>
          <td style="color:#64748b;font-size:11px">{r.get('llm_judge_reason','')}</td>
          <td style="color:{'#ef4444' if unanswered else '#22c55e'};text-align:center">{'예' if unanswered else '아니오'}</td>
        </tr>"""

    html = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>WithBuddy RAG 평가 결과</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{ font-family: 'Malgun Gothic', sans-serif; font-size: 12px; margin: 24px 28px; color: #1e293b; }}
  h1 {{ font-size: 18px; margin: 0 0 4px; }}
  h2 {{ font-size: 14px; margin: 28px 0 12px; border-bottom: 2px solid #1e293b; padding-bottom: 4px; }}
  .meta {{ color: #64748b; margin-bottom: 16px; font-size: 11px; }}
  .summary {{ display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }}
  .card {{ padding: 12px 20px; border-radius: 8px; text-align: center; min-width: 100px; background: #f8fafc; border: 1px solid #e2e8f0; }}
  .card .num {{ font-size: 26px; font-weight: bold; }}
  .card .label {{ font-size: 11px; color: #64748b; margin-top: 2px; }}
  table {{ border-collapse: collapse; width: 100%; margin-bottom: 8px; }}
  th {{ background: #334155; color: white; padding: 7px 10px; text-align: left; font-size: 11px; }}
  td {{ padding: 6px 10px; border-bottom: 1px solid #e2e8f0; vertical-align: middle; font-size: 11px; line-height: 1.5; }}
  @media print {{ body {{ margin: 10px 14px; }} tr {{ page-break-inside: avoid; }} }}
</style>
</head>
<body>
<h1>WithBuddy RAG 시스템 성능 평가</h1>
<div class="meta">평가 일시: {evaluated_at} &nbsp;|&nbsp; 총 {summary['total_cases']}문항</div>

<h2>전체 지표</h2>
<div class="summary">{metric_cards}</div>

<h2>카테고리별 요약</h2>
<table>
  <tr><th>카테고리</th><th>Retrieval Hit</th><th>Keyword Hit</th><th>Judge Score</th><th>미답변</th></tr>
  {cat_rows}
</table>

<h2>문항별 상세</h2>
<table>
  <tr><th>#</th><th>카테고리</th><th>질문</th><th>검색히트</th><th>키워드</th><th>Judge</th><th>판정이유</th><th>미답변</th></tr>
  {detail_rows}
</table>
{conflict_section}
</body>
</html>"""

    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)


def main():
    parser = argparse.ArgumentParser(description="RAG 시스템 성능 평가")
    parser.add_argument("--output", type=str, default="./data/eval_result.json",
                        help="결과 저장 경로 (기본: ./data/eval_result.json)")
    parser.add_argument("--chroma_dir", type=str, default="C:/withbuddy_chroma_db",
                        help="ChromaDB 경로 (기본: C:/withbuddy_chroma_db)")
    parser.add_argument("--company_code", type=str, default="",
                        help="회사 코드 (예: WB0001)")
    args = parser.parse_args()

    summary = run_evaluation(chroma_dir=args.chroma_dir, company_code=args.company_code)

    os.makedirs(os.path.dirname(args.output), exist_ok=True)

    # ── 단일 결과 저장 ──────────────────────────────────────────
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"\n  💾 상세 결과 저장: {args.output}")

    # ── 히스토리 누적 저장 (버전별 비교용) ──────────────────────
    history_path = os.path.join(os.path.dirname(args.output), "eval_history.json")
    history = []
    if os.path.exists(history_path):
        with open(history_path, encoding="utf-8") as f:
            try:
                history = json.load(f)
            except Exception:
                history = []

    history_entry = {
        "evaluated_at": summary["evaluated_at"],
        "total_cases": summary["total_cases"],
        **summary["metrics"],
    }
    history.append(history_entry)

    with open(history_path, "w", encoding="utf-8") as f:
        json.dump(history, f, ensure_ascii=False, indent=2)

    # ── 이전 결과와 비교 출력 ────────────────────────────────────
    if len(history) >= 2:
        prev = history[-2]
        curr = history[-1]
        print("\n  📈 이전 평가 대비 변화")
        print("  " + "-" * 40)
        for key, label in [
            ("retrieval_hit_rate",    "Retrieval Hit Rate"),
            ("avg_keyword_hit_rate",  "Keyword Hit Rate  "),
            ("avg_llm_judge_score",   "LLM Judge Score   "),
            ("unanswered_rate",       "Unanswered Rate   "),
            ("avg_response_time_ms",  "Avg Response Time "),
        ]:
            p, c = prev.get(key, 0), curr.get(key, 0)
            diff = c - p
            if key in ("unanswered_rate", "avg_response_time_ms"):
                arrow = "✅" if diff < 0 else ("❌" if diff > 0 else "➖")
            else:
                arrow = "✅" if diff > 0 else ("❌" if diff < 0 else "➖")
            unit = "ms" if "ms" in key else ("%")
            if unit == "%":
                print(f"  {label}: {p*100:.1f}% → {c*100:.1f}% ({arrow} {diff*100:+.1f}%p)")
            else:
                print(f"  {label}: {p:.0f}ms → {c:.0f}ms ({arrow} {diff:+.0f}ms)")

    print(f"\n  📋 누적 평가 횟수: {len(history)}회  (history: {history_path})")
    print("=" * 55)

    html_path = args.output.replace(".json", ".html")
    generate_html(summary, html_path)
    print(f"  🌐 HTML 리포트: {html_path}")
    print(f"  → 브라우저에서 열고 Ctrl+P → PDF로 저장하세요")


if __name__ == "__main__":
    main()
