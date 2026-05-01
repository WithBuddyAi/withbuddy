"""
Clarifying (의도 명확화) 유틸리티
────────────────────────────────────────────
- 모호한 질문 감지 + clarifying 질문 생성 (1회 LLM 호출)
- post-clarifying 상태 감지 (LLM 불필요)
- clarifying 답변 → RAG 쿼리 확장
"""

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from core.llm import get_llm

# clarifying 질문의 특징적인 종결 표현들 (post-clarifying 상태 감지에 사용)
_CLARIFYING_ENDINGS = [
    "어떤 부분이 궁금하신가요?",
    "어떤 게 필요하신가요?",
    "어떤 게 필요하신지",
    "어떤 종류의 휴가를 쓰실 예정인가요?",
    "어떤 사안에 대한 승인이 필요하신가요?",
    "어떤 계정에서 어떤 문제가 생기셨나요?",
    "어떤 툴의 권한이 필요하신가요?",
    "어떤 파일을 올리실 예정인가요?",
    "어떤 장비가 필요하신가요?",
    "어떤 부분이 필요하신가요?",
    "프로그램이나 설정 관련 문제인가요",
    "도서와 강의 중 어떤 게 필요하신가요?",
    "지각 처리 기준이 궁금하신가요",
    "말씀해 주시면 바로 안내해드릴게요!",
]

# 감지 + 생성 통합 프롬프트 (LLM 호출 1회)
_CLARIFY_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 With Buddy AI 질문 분류기입니다.

주어진 질문이 "모호한 질문"인지 판단하세요.
- 모호한 질문: 회사 업무 관련이지만 세부 의도가 2가지 이상으로 해석 가능
- 명확한 질문: 특정 정보나 행동을 요청하는 경우

[모호한 질문 예시 → clarifying 필요]
- "연차요", "연차 관련해서요" → 발생 기준/신청 방법/잔여 확인 중 불명확
- "반차 쓰려고요" → 신청 방법/시간 기준/차감 일수 중 불명확
- "증명서 필요해요" → 재직/경력/원천징수/영문 중 불명확
- "정산이요", "정산 어떻게 해요?" → 경비/식대/야근식대/출장비 중 불명확
- "복지 혜택 뭐 있어요?" → 전체 목록 vs 특정 항목 불명확
- "계정 문제가 있어요" → 어떤 계정/어떤 문제 불명확
- "결재 어떻게 올려요?" → 연차/경비/비품 중 불명확
- "퇴사 관련해서요" → 절차/급여정산/퇴직금 중 불명확
- "야근 관련 혜택이요" → 식대 vs 수당 불명확
- "수습 관련해서요" → 기간/급여/연차/평가 불명확

[명확한 질문 예시 → NONE 반환]
- "연차 신청 방법 알려줘" → 신청 방법으로 명확
- "재직증명서 발급 어떻게 해요?" → 재직증명서로 명확
- "법인카드 사용 조건이요" → 사용 조건으로 명확
- "야근 수당 계산 방법" → 수당 계산으로 명확
- "코드 리뷰 방법" → 업무 범위 밖 (out_of_scope 처리됨)

모호하면 clarifying 질문을 생성하세요. 명확하면 "NONE"을 반환하세요.

[clarifying 질문 작성 규칙]
1. 존댓말 사용, 반말 절대 금지
2. 가능한 의도를 2~4가지 선택지로 제시 (축이 2개 이상이면 슬롯형)
3. 절차·담당자·기한 등 회사별로 달라지는 정보는 포함하지 않음
4. 1~3문장 이내, 간결하게
5. "어떤 부분이 궁금하신가요?"로 끝내는 것을 권장

회사 코드: {company_code}
(wb0001 전용: 복지카드, VPN 지원 / wb0002 전용: 복지카드 없음·장비대여·촬영룸·폰트 지원)"""),
    ("human", "질문: {question}\n\n결과(clarifying 질문 또는 NONE):"),
])

# clarifying 답변 → RAG 쿼리 확장 프롬프트
_EXPAND_PROMPT = ChatPromptTemplate.from_messages([
    ("system", "다음 clarifying 질문과 사용자 답변을 바탕으로 RAG 검색에 적합한 구체적인 한국어 질문 한 문장을 만드세요. 질문 형태로."),
    ("human", "clarifying 질문: {cq}\n사용자 답변: {ua}\n\n확장된 질문:"),
])


def is_post_clarifying(conversation_history: list) -> tuple[bool, str]:
    """
    conversationHistory에서 마지막 AI 메시지가 clarifying 질문인지 확인합니다.

    Returns:
        (True, last_clarifying_question) — post-clarifying 상태
        (False, "") — 일반 상태
    """
    if not conversation_history:
        return False, ""

    for turn in reversed(conversation_history):
        if turn.role in ("assistant", "ai"):
            content = turn.content
            if any(ending in content for ending in _CLARIFYING_ENDINGS):
                return True, content
            return False, ""

    return False, ""


def check_and_generate_clarifying(question: str, company_code: str) -> str | None:
    """
    질문이 모호하면 clarifying 질문을 생성해 반환합니다.
    명확한 질문이면 None을 반환합니다.

    LLM 호출 1회로 감지+생성을 처리합니다.
    """
    chain = _CLARIFY_PROMPT | get_llm() | StrOutputParser()
    result = chain.invoke({
        "question": question,
        "company_code": company_code or "global",
    }).strip()

    first_line = result.split("\n")[0].strip().upper()
    if first_line == "NONE" or not result:
        return None
    return result


def expand_clarifying_query(clarifying_q: str, user_answer: str) -> str:
    """
    clarifying 질문 + 사용자 답변 → RAG 검색에 적합한 확장 쿼리

    예:
        cq = "연차는 신청 방법, 남은 연차 확인으로 나뉘어요. 어떤 부분이 궁금하신가요?"
        ua = "신청 방법이요"
        → "연차 신청 방법"
    """
    chain = _EXPAND_PROMPT | get_llm() | StrOutputParser()
    return chain.invoke({"cq": clarifying_q, "ua": user_answer}).strip()
