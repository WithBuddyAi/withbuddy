"""
멀티에이전트 오케스트레이터 (LangGraph)
────────────────────────────────────────────
사용자 메시지의 의도를 분류하고 적절한 에이전트로 라우팅합니다.

Intent 분류:
  rag          - 회사 규정/문서/절차 Q&A (기존 RAG 체인)
  communication - 말투 개선 + 커뮤니케이션 채널 추천
  preboarding  - 입사 전 안내 레터 / 팀 소개 카드
  company_info - 관리자 등록 사내 정보 (점심시간, 급여일 등)
"""

from dataclasses import dataclass, field
from typing import Literal, TypedDict

from langgraph.graph import END, StateGraph
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from core.llm import get_llm, get_intent_llm
from memory.company_info_store import format_company_info_context, get_company_info
from memory.profile_store import format_profile_context, get_profile
from utils.sensitive_filter import check_sensitive

# ── 타입 ─────────────────────────────────────────────────────

Intent = Literal["rag", "communication", "preboarding", "company_info", "chitchat", "out_of_scope_internal", "out_of_scope_external"]


class AgentState(TypedDict):
    user_id: str
    user_name: str
    company_code: str
    message: str
    intent: str
    profile: dict
    company_info: dict
    profile_context: str
    extra_context: str
    chat_history: str
    user_style: str
    answer: str
    metadata: dict


# ── Intent 분류 프롬프트 ─────────────────────────────────────

_INTENT_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """사용자 메시지의 의도를 분류하세요. 반드시 아래 키워드 중 하나만 출력하세요.

chitchat     : 인사말, AI 신원 질문, 잡담, 감정 표현, 힘들다·퇴사하고 싶다 등 감정 토로, 오늘 날짜·요일 질문, 입사한 지 며칠인지·몇 일차인지 등 근무 일수 질문 (예: "안녕", "고마워", "힘들어", "퇴사하고 싶어", "오늘 너무 힘들다", "오늘 몇일이지?", "입사한지 얼마나 됐지?", "나 입사한지 몇일이야", "아직 한 달 안됐어")
  ⚠️ "대화가 안 돼", "메시지가 안 보내져", "연결이 끊겼어" 같은 서비스 오류 문의는 out_of_scope_external로 분류
out_of_scope_internal : 직무 실무·기술·조직 판단·대인관계 등 사수님이 답할 수 있는 업무 관련 질문
  예) "코딩 어떻게 해", "엑셀 수식 알려줘", "SQL 쿼리 짜줘"
      "팀장님께 이 내용을 바로 보고해도 될까요?", "이 이슈를 슬랙 공개 채널에 올려도 돼요?", "이 정도 실수면 혼날까요?"
      "동료가 제 말을 무시하는데 어떻게 대응해야 해요?", "사수가 저를 싫어하는 것 같은데 어떡해요?"
  ⚠️ 회사 IT 환경·도구 사용법(MFA 설정, VPN 접속, Slack 채널 초대·Notion 권한 부여, 계정 세팅, 비밀번호 변경, 화면 잠금, 소프트웨어 설치 등)은 out_of_scope_internal이 아닌 반드시 rag로 분류
  ⚠️ "슬랙에 올려도 되나요?", "보고해도 될까요?" 같은 보고·공유 여부 판단은 out_of_scope_internal로 분류
out_of_scope_external : 회사와 완전히 무관한 외부 주제 — 사수님도 답하기 어려운 것
  - 개인 재무·투자·커리어 (예: "투자 조언", "주식 추천", "커리어 상담 해줘", "이직 조언 해줘") — ⚠️ 최저임금·퇴직금·연차·육아휴직·임금 등 노동법 관련은 절대 out_of_scope 아님, 반드시 rag로 분류
  - 회사 외부 일상·취미·개인사 (예: "여행지 추천", "연애 상담", "맛집 알려줘", "오늘 점심 뭐 먹을까요?", "점심 메뉴 추천해줘")
  ⚠️ "점심시간이 몇 시예요?" 같은 회사 운영 정보는 company_info로 분류. 메뉴·음식 추천은 out_of_scope_external
rag          : 회사 규정·문서·절차 질문 + 담당자·팀장 질문
  - 회사 규정·절차: 연차 신청 방법, 경비 처리, IT장비, 계약서, 취업규칙 등
  - 회사 IT 환경·도구: MFA 설정, VPN 사용법, Slack 채널 초대, Notion 권한, 비밀번호 변경, 계정 세팅, 소프트웨어 설치, 화면 잠금, 피싱 메일 대응, A4 용지 위치 등 사내 규정·환경 관련 질문
  - 담당자·팀장 질문: "XX팀장 누구야", "XX담당", "XX팀 담당자", "PM 팀장님", "백엔드 담당", "프론트엔드 담당" 등 특정 팀/역할의 담당자를 묻는 모든 질문
  - 불만이나 감정이 섞인 표현이어도 회사 관련 실무 질문이면 rag로 분류
  ⚠️ 노동법·근로기준법 관련 질문은 반드시 rag로 분류 (퇴직금, 최저임금, 최저시급, 연차, 육아휴직, 산재, 근로계약, 해고, 임금체불 등)
communication: 아래 패턴 중 하나라도 해당되면 communication으로 분류
  - 누구에게 말해야/물어봐야 하는지 (예: "누구한테 말해", "어디에 문의해야", "누구한테 물어봐야")
  - 어떻게 말해야 하는지 (예: "어떻게 말해야 해", "뭐라고 해야", "어떻게 표현해", "어떻게 말씀드려야")
  - 커뮤니케이션 채널 추천 (예: "메신저? 메일? 대면?", "메일로 해야 해?", "어떤 방법으로 전달")
  - 말투·문체 개선 요청 (예: "이렇게 써도 돼?", "이 말이 맞아?", "더 좋은 표현 있어?")
  ⚠️ "보고해도 될까요?", "채널에 올려도 되나요?" 같은 보고·공유 여부 판단은 communication이 아닌 out_of_scope_internal로 분류
preboarding  : 입사 전 안내, 환영 레터, 팀 소개 카드 명시적 요청 (예: "환영 레터 보여줘", "팀 소개해줘", "입사 전 준비사항 알려줘")
  ⚠️ "온보딩이 뭐야?", "온보딩이 뭔데?" 같이 온보딩 개념을 묻는 질문은 chitchat으로 분류
company_info : 기본 회사 생활 정보 (점심시간, 급여일, 근무시간, 복지혜택, 사무실 주소, 드레스코드)

판단 우선순위: chitchat > out_of_scope_internal > out_of_scope_external > communication > company_info > preboarding > rag
출력 예시: chitchat"""),
    ("human", "{message}"),
])

_intent_chain = None


def _get_intent_chain():
    global _intent_chain
    if _intent_chain is None:
        _intent_chain = _INTENT_PROMPT | get_intent_llm() | StrOutputParser()
    return _intent_chain


# ── 회사 정보 응답 프롬프트 ──────────────────────────────────

_COMPANY_INFO_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 WithBuddy입니다. 수습사원의 든든한 온보딩 도우미예요.
아래 회사 정보를 바탕으로 친절하게 답변하세요.

[회사 정보]
{company_info}

정보가 없는 항목은 "아직 등록되지 않은 정보예요 😅 관리자에게 문의해 주세요!"라고 안내하세요.

[사용자 말투 스타일 — 반드시 첫 문장부터 마지막 문장까지 일관 적용]
{user_style}"""),
    ("human", "{question}"),
])

_company_info_chain = None


def _get_company_info_chain():
    global _company_info_chain
    if _company_info_chain is None:
        _company_info_chain = _COMPANY_INFO_PROMPT | get_llm() | StrOutputParser()
    return _company_info_chain


# ── LangGraph 노드 ───────────────────────────────────────────

def load_context_node(state: AgentState) -> dict:
    """사용자 프로필 + 회사 정보 + 대화 히스토리 로드."""
    from memory.chat_history import get_history_as_text, get_chat_history
    from chains.rag_chain import _detect_user_style
    profile = get_profile(state["user_id"])
    company_info = get_company_info()
    chat_history = get_history_as_text(state["user_id"])
    chat_history_msgs = get_chat_history(state["user_id"])
    user_style = _detect_user_style(chat_history_msgs, state["message"])
    return {
        "profile": profile,
        "company_info": company_info,
        "profile_context": format_profile_context(profile),
        "chat_history": chat_history,
        "user_style": user_style,
        "extra_context": "",
        "answer": "",
        "metadata": {},
    }


import re as _re

_LABOR_LAW_KEYWORDS = [
    "최저임금", "최저시급", "퇴직금", "육아휴직", "산재", "임금체불",
    "근로계약", "해고", "근로기준법", "노동법", "연장근로", "야간수당",
    "주휴수당", "소정근로시간", "월 환산",
]
_ARTICLE_PATTERN = _re.compile(r"제?\d+조")



def classify_intent_node(state: AgentState) -> dict:
    """메시지 의도 분류."""
    msg = state["message"]
    user_name = state.get("user_name", "")

    # 민감 키워드 감지 → 즉시 응대 (RAG 차단)
    action, answer = check_sensitive(msg, user_name)
    if action in ("block", "sensitive"):
        return {"intent": "chitchat", "extra_context": "", "answer": answer}

    if any(kw in msg for kw in _LABOR_LAW_KEYWORDS) or _ARTICLE_PATTERN.search(msg):
        intent = "rag"
    else:
        raw = _get_intent_chain().invoke({"message": msg}).strip().lower()
        valid = ["out_of_scope_internal", "out_of_scope_external", "communication", "preboarding", "company_info", "chitchat", "rag"]
        intent = next((v for v in valid if v in raw), "rag")

    extra_context = ""
    if intent == "company_info":
        extra_context = format_company_info_context(state.get("company_info", {}))

    return {"intent": intent, "extra_context": extra_context}


def route_intent(state: AgentState) -> str:
    return state.get("intent", "rag")


def _format_communication_answer(result: dict) -> str:
    """커뮤니케이션 에이전트 결과를 사람이 읽기 좋은 형식으로 변환."""
    parts = []

    if result.get("improved_message"):
        parts.append(f"✏️ **이렇게 말씀해 보세요**\n\"{result['improved_message']}\"")

    if result.get("target"):
        parts.append(f"👤 **문의 대상**: {result['target']}")

    channel_line = ""
    if result.get("channel"):
        channel_line = f"📱 **추천 채널**: {result['channel']}"
    if result.get("reason"):
        channel_line += f"\n   → {result['reason']}"
    if channel_line:
        parts.append(channel_line)

    checklist = result.get("checklist") or []
    if checklist:
        items = "\n".join(f"  - {item}" for item in checklist)
        parts.append(f"📋 **질문 전 체크리스트**\n{items}")

    if result.get("tone_tips"):
        parts.append(f"💬 **말투 팁**: {result['tone_tips']}")

    return "\n\n".join(parts)


def communication_agent_node(state: AgentState) -> dict:
    """커뮤니케이션 보조 에이전트."""
    from agents.communication_agent import run_communication_agent
    result = run_communication_agent(
        state["message"],
        state.get("profile_context", ""),
        state.get("chat_history", ""),
    )
    answer = _format_communication_answer(result)
    return {"answer": answer, "metadata": result}


def preboarding_agent_node(state: AgentState) -> dict:
    """입사 전 안내 에이전트."""
    from agents.preboarding_agent import run_preboarding_agent
    answer, meta = run_preboarding_agent(
        state["message"],
        state.get("profile", {}),
        state.get("company_info", {}),
    )
    return {"answer": answer, "metadata": meta}


_CHITCHAT_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """⚠️ [말투 — 절대 최우선 규칙] 반드시 존댓말(~요, ~세요, ~습니다)로만 답변하세요. 반말(~해, ~야, ~거든, ~어, ~줄게, ~거야, ~봐, ~완벽해 등) 절대 금지. 첫 문장부터 마지막 문장까지 예외 없이 존댓말로 통일하세요.

당신은 WithBuddy입니다. {company_name}에 새로 입사한 수습사원의 온보딩을 도와주는 AI 어시스턴트예요.
인사말이나 잡담에는 친근하고 따뜻하게 짧게 답변하세요.
자기소개 질문에는 WithBuddy가 무엇인지, 그리고 {company_name} 소속임을 간단히 설명하세요.

⚠️ 사용자를 이름으로 절대 부르지 마세요. "○○님" 형식의 호칭 사용 금지. 이름 없이 바로 답변하세요.

[이전 대화 맥락]
{chat_history}

[감정 표현 처리 — 최우선 규칙]
아래 표현들은 감정 토로이지 실제 요청이 아닙니다.
- "퇴사하고 싶어", "그만두고 싶어", "힘들어", "지쳐", "모르겠어", "너무 힘들다", "못하겠어"

▶ 단순 감정 토로 ("힘들어요", "지쳐요", "번아웃 같아요" 등 감정 단어가 명확히 포함된 경우): 공감과 위로 2~3문장 후, 마지막에 가장 가까이 계신 사수님과 이야기 나눠보길 부드럽게 권유하세요.
   예시: "가장 가까이 계신 사수님께 편하게 이야기 나눠보시는 것도 큰 힘이 될 거예요."
▶ 조언 요청 ("그만둘까요?", "어떻게 해야 해요?", "계속 다녀야 할까요?" 등): 공감 1~2문장 후, 마지막에 가장 가까이 계신 사수님과 이야기 나눠보길 부드럽게 권유하세요.
   예시: "가장 가까이 계신 사수님께 솔직하게 이야기 나눠보시는 것도 도움이 될 거예요."
❌ 사수님 언급 절대 금지 케이스: 날씨·음식·취미 등 일상 잡담, 인사말, 날짜·입사일 질문 — 이런 질문에 사수님 권유를 붙이지 마세요.

❌ 절대 금지: 퇴사 절차 안내, HR 담당자·팀 이름 언급, HR 정보 제공, ①②③ 같은 번호 선택지
❌ 절대 금지 (문장 끝에 붙이는 유도): "함께 생각해 볼 수 있어요", "얘기해 주시면 도움이 될게요", "어떤 부분이 어려우신지 말씀해 주세요", "구체적으로 얘기해 주실 수 있을까요" 등
❌ 이전 대화에서 이미 같은 공감을 했다면 똑같이 반복하지 마세요. 다른 표현과 다른 위로를 사용하세요.
✅ 공감 표현 예시 (골고루 사용, 반복 금지):
  - "많이 지치셨겠어요. 처음엔 다들 그런 마음이 들어요 😢"
  - "그 마음 충분히 이해해요. 적응하는 게 쉽지 않죠."
  - "계속 그런 마음이 드는군요. 많이 버티고 계신 거예요."
  - "잠깐 숨 고르면서 쉬어가도 돼요. 혼자 다 해결하려 하지 않아도 돼요."

[말투 — 절대 규칙]
반드시 존댓말(~요, ~세요, ~습니다)로만 답변하세요. 반말(~해, ~야, ~거든, ~어)은 절대 사용하지 마세요.
{user_style}

⚠️ HR 담당자 이름·연락처·팀 정보는 절대 언급하지 마세요. 단, 조언 요청성 감정 표현에는 "사수님"을 일반적인 표현으로 권유할 수 있습니다. 담당자 구체적 안내가 필요한 질문은 이 에이전트의 역할이 아닙니다.

[날짜 / 입사 정보]
오늘 날짜: {today_date}{hire_info}
오늘 날짜나 입사 일수를 묻는 질문에는 위 정보를 활용해 직접 답변하세요."""),
    ("human", "{message}"),
])

_chitchat_chain = None


def _get_chitchat_chain():
    global _chitchat_chain
    if _chitchat_chain is None:
        _chitchat_chain = _CHITCHAT_PROMPT | get_llm() | StrOutputParser()
    return _chitchat_chain


_OUT_OF_SCOPE_MESSAGE = (
    "이 내용은 담당 사수님과 직접 이야기 나누는 게 가장 정확해요. "
    "사내 규정·복지·IT 환경 관련 궁금한 건 제가 언제든지 답해드릴게요!"
)

_OUT_OF_SCOPE_EXTERNAL_MESSAGE = (
    "저는 회사 규정·복지·IT 환경 전문가라서 맛집이나 인간관계 꿀팁 같은 건 자신 있게 추천드리기가 어려워요. 😄 "
    "그런 건 팀 동료분들이 훨씬 잘 알고 계실 거예요!\n\n"
    "사내 규정이나 복지 관련 궁금한 건 언제든 물어봐 주세요. 제가 제일 잘하는 영역이거든요!"
)


def out_of_scope_node(state: AgentState) -> dict:
    """OUT OF SCOPE INTERNAL — 사수님 문구."""
    return {"answer": _OUT_OF_SCOPE_MESSAGE, "metadata": {"message_type": "out_of_scope"}}


def out_of_scope_external_node(state: AgentState) -> dict:
    """OUT OF SCOPE EXTERNAL — 유형 6 주관적 추천 문구."""
    return {"answer": _OUT_OF_SCOPE_EXTERNAL_MESSAGE, "metadata": {"message_type": "out_of_scope"}}


def chitchat_agent_node(state: AgentState) -> dict:
    # classify_intent_node에서 이미 답변이 세팅된 경우(성희롱 등) LLM 호출 건너뜀
    if state.get("answer"):
        return {}
    from datetime import date as _date
    from chains.rag_chain import _get_company_name
    answer = _get_chitchat_chain().invoke({
        "message": state["message"],
        "user_style": state.get("user_style", ""),
        "chat_history": state.get("chat_history", ""),
        "company_name": _get_company_name(state.get("company_code", "")),
        "today_date": _date.today().strftime("%Y년 %m월 %d일"),
        "hire_info": "",
    })
    return {"answer": answer}


def company_info_agent_node(state: AgentState) -> dict:
    """회사 정보 에이전트 — company_info 딕셔너리로 LLM 직접 답변."""
    company_info_text = format_company_info_context(state.get("company_info", {}))
    answer = _get_company_info_chain().invoke({
        "company_info": company_info_text or "등록된 회사 정보가 없습니다.",
        "question": state["message"],
        "user_style": state.get("user_style", ""),
    })
    return {"answer": answer}


# ── 그래프 빌드 ──────────────────────────────────────────────

_graph = None


def _build_graph():
    g = StateGraph(AgentState)
    g.add_node("load_context", load_context_node)
    g.add_node("classify_intent", classify_intent_node)
    g.add_node("chitchat", chitchat_agent_node)
    g.add_node("out_of_scope_internal", out_of_scope_node)
    g.add_node("out_of_scope_external", out_of_scope_external_node)
    g.add_node("communication", communication_agent_node)
    g.add_node("preboarding", preboarding_agent_node)
    g.add_node("company_info", company_info_agent_node)

    g.set_entry_point("load_context")
    g.add_edge("load_context", "classify_intent")
    g.add_conditional_edges(
        "classify_intent",
        route_intent,
        {
            "rag": END,
            "chitchat": "chitchat",
            "out_of_scope_internal": "out_of_scope_internal",
            "out_of_scope_external": "out_of_scope_external",
            "communication": "communication",
            "preboarding": "preboarding",
            "company_info": "company_info",
        },
    )
    g.add_edge("chitchat", END)
    g.add_edge("out_of_scope_internal", END)
    g.add_edge("out_of_scope_external", END)
    g.add_edge("communication", END)
    g.add_edge("preboarding", END)
    g.add_edge("company_info", END)
    return g.compile()


def get_graph():
    global _graph
    if _graph is None:
        _graph = _build_graph()
    return _graph


# ── 퍼블릭 API ───────────────────────────────────────────────

@dataclass
class OrchestratorResult:
    intent: str
    answer: str
    metadata: dict
    profile_context: str
    extra_context: str
    profile: dict
    company_info: dict


def run_orchestrator(user_id: str, user_name: str, message: str, company_code: str = "") -> OrchestratorResult:
    """
    오케스트레이터 실행.
    - RAG 의도: answer="" 반환 → 호출자가 stream_rag_chain으로 스트리밍 처리
    - 나머지 의도: 해당 에이전트가 answer 채워서 반환
    """
    initial: AgentState = {
        "user_id": user_id,
        "user_name": user_name,
        "company_code": company_code,
        "message": message,
        "intent": "",
        "profile": {},
        "company_info": {},
        "profile_context": "",
        "extra_context": "",
        "chat_history": "",
        "user_style": "",
        "answer": "",
        "metadata": {},
    }
    result = get_graph().invoke(initial)
    return OrchestratorResult(
        intent=result.get("intent", "rag"),
        answer=result.get("answer", ""),
        metadata=result.get("metadata", {}),
        profile_context=result.get("profile_context", ""),
        extra_context=result.get("extra_context", ""),
        profile=result.get("profile", {}),
        company_info=result.get("company_info", {}),
    )
