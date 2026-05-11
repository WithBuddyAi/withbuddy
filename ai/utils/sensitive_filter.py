"""
민감 키워드 감지 및 응대 분기 로직 (SCRUM-167)

분기 흐름:
  1. 욕설/비속어          → 즉시 차단 (경고 문구)
  2. 극단적 위기 표현     → 항상 차단 (행정 의도 무관)
  3. 윤리적 리스크        → 피해 진술 시 항상 차단 / 행정 의도 없으면 차단
  4. 법적 리스크          → 행정 의도 없으면 차단
  5. 감정적 위기          → 행정 의도 없으면 차단
  6. 회사·사람 비방       → 소프트 리다이렉트
  7. 해당 없음            → 정상 RAG 통과
"""

# ── 카테고리 1: 법적 리스크 ──────────────────────────────────────
_LEGAL_RISK_KEYWORDS = [
    "부당해고", "해고 통보", "소송", "고소", "노동청", "진정", "노무사",
    "법적 조치", "법적으로 대응", "임금 체불", "월급을 못 받", "급여가 안 들어",
    "권고사직 거부",
]

# ── 카테고리 2: 윤리적 리스크 ────────────────────────────────────
_ETHICAL_RISK_KEYWORDS = [
    "성희롱", "성추행", "성폭력", "성적 괴롭힘", "성적 수치심",
    "괴롭힘", "직장내 괴롭힘", "직장 내 괴롭힘",
    "폭행", "따돌림", "갑질", "협박",
]
# 피해 진술 패턴 — 행정 의도 단어가 있어도 항상 차단
_VICTIM_PATTERNS = [
    "당했어요", "당하고 있어요", "당했습니다", "당하고 있습니다",
    "피해를 받", "받고 있어요", "받고 있습니다",
]

# ── 카테고리 3: 감정적 위기 ──────────────────────────────────────
_EMOTIONAL_CRISIS_KEYWORDS = [
    "퇴사하고 싶어", "그만두고 싶어", "번아웃", "우울",
    "힘들어서 못 하겠", "힘들어 못 하겠", "회사 다니기 싫어",
    "회사 오기가 너무 힘들", "너무 우울",
    "포기하고 싶",
]
# 극단적 표현 — 항상 차단 (행정 의도 무관)
_EXTREME_CRISIS_KEYWORDS = [
    "자살", "죽고 싶다", "죽고 싶어", "극단적 선택",
    "회사 다니기 싫어 죽겠", "싫어 죽겠어요",
]

# ── 서비스 에티켓: 욕설/비속어 ───────────────────────────────────
_PROFANITY_KEYWORDS = [
    "시발", "씨발", "존나", "미친", "개새끼", "병신", "지랄", "닥쳐",
    "꺼져", "죽어라", "쓸모없어", "무능해", "멍청이",
]

# ── 서비스 에티켓: 회사·사람 비방 ───────────────────────────────
_SLANDER_KEYWORDS = [
    "망해라", "쓰레기 회사", "최악이야", "싫어 죽겠",
]

# ── 성희롱 신고 방법 고정 응답 ──────────────────────────────────
# RAG를 거치면 근로기준법 단계 절차를 나열하므로 고정 응답으로 처리
_HARASSMENT_REPORT_KEYWORDS = ["신고 방법", "신고방법", "신고 절차", "신고절차", "어떻게 신고", "신고하는 방법"]

def _make_harassment_report_answer(user_name: str) -> str:
    name_part = f"{user_name}님, " if user_name else ""
    return (
        f"{name_part}많이 당황스러우시고 마음이 무거우실 것 같아요. 😥 "
        "저희 회사는 신고자의 익명성과 비밀을 철저히 보장하며, 어떠한 불이익도 발생하지 않도록 보호하고 있습니다.\n\n"
        "**[가장 빠른 사내 신고처]**\n"
        "경영지원팀 담당자에게 메일이나 유선으로 상담을 요청하시면 즉시 객관적인 조사가 시작됩니다.\n\n"
        "참고로, 근로기준법상 사용자는 신고 접수 시 지체 없이 조사를 실시하고 피해자 보호 조치를 취해야 할 의무가 있습니다.\n\n"
        "혼자 고민하지 마시고 사내 전문 창구를 통해 안전하게 보호받으시길 권장드려요."
    )

# ── 행정 의도 단어 ───────────────────────────────────────────────
_ADMIN_INTENT_WORDS = [
    "어떻게", "방법", "절차", "기준", "신청", "정산", "받을 수 있어",
    "알려줘", "확인", "얼마", "언제", "어디서", "가능해", "신청하려고",
    "규정", "조항", "가이드라인", "기한", "서류", "증빙", "절차서", "매뉴얼",
]


# ── 응대 문구 ─────────────────────────────────────────────────────

def _make_sensitive_answer(user_name: str, contact: str = "경영지원팀") -> str:
    name_part = f"{user_name}님, " if user_name else ""
    return (
        f"{name_part}말씀하신 내용은 위드버디가 직접 도움드리기 어려운 사안이에요. 😥\n\n"
        f"정확하고 안전한 해결을 위해 **{contact}**에 직접 문의하시거나\n"
        "상담을 요청해 보시는 걸 권장드려요."
    )


def _make_wage_answer(user_name: str) -> str:
    name_part = f"{user_name}님, " if user_name else ""
    return (
        f"{name_part}급여 지급 관련해서 걱정이 많으시겠어요. 😥\n\n"
        "임금은 정해진 날짜에 전액 지급되는 것이 원칙입니다. "
        "행정상의 실수나 전산 오류로 누락되는 경우도 있으니 아래 순서대로 확인해 보세요.\n\n"
        "**1단계: 경영지원팀 확인**\n"
        "급여 명세서와 실제 입금 내역을 대조한 뒤, 경영지원팀에 누락 여부를 먼저 확인하세요.\n\n"
        "**2단계: 증빙 자료 준비**\n"
        "근로계약서와 급여 명세서를 미리 챙겨두시면 상담이 수월합니다."
    )


def _make_profanity_answer(user_name: str) -> str:
    name_part = f"{user_name}님을 " if user_name else ""
    return (
        f"원활한 대화를 위해 조금 더 고운 말을 사용해 주실 수 있을까요? 😌\n\n"
        f"위드버디는 예의 바른 대화 속에서 {name_part}더 잘 도와드릴 수 있어요.\n"
        "다시 한번 질문해 주시면 정성껏 답변해 드릴게요."
    )

_SLANDER_ANSWER = (
    "속상하시군요. 하지만 제가 답해드릴 수 있는 건 사내 규정뿐이에요.\n\n"
    "궁금한 규정이 있으시면 말씀해 주세요."
)


# ── 메인 함수 ─────────────────────────────────────────────────────

def check_global_block(message: str, user_name: str = "") -> tuple[str, str | None]:
    """욕설·극단적 위기만 확인. 복합 질문 처리 전 전체 차단용."""
    if any(kw in message for kw in _PROFANITY_KEYWORDS):
        return ("block", _make_profanity_answer(user_name))
    if any(kw in message for kw in _EXTREME_CRISIS_KEYWORDS):
        return ("block", _make_sensitive_answer(user_name))
    return ("pass", None)


def check_sensitive(message: str, user_name: str = "") -> tuple[str, str | None]:
    """
    민감 키워드 감지 및 응대 분기.

    Returns:
        ("block", answer_text)  — 민감 응대 반환, RAG 차단
        ("pass",  None)         — 정상 RAG 통과
    """
    # 1. 욕설/비속어 → 즉시 차단
    if any(kw in message for kw in _PROFANITY_KEYWORDS):
        return ("block", _make_profanity_answer(user_name))

    # 2. 극단적 위기 → 항상 차단 (행정 의도 무관)
    if any(kw in message for kw in _EXTREME_CRISIS_KEYWORDS):
        return ("block", _make_sensitive_answer(user_name))

    has_admin_intent = any(kw in message for kw in _ADMIN_INTENT_WORDS)

    # 3. 윤리적 리스크
    if any(kw in message for kw in _ETHICAL_RISK_KEYWORDS):
        # 피해 진술 → 항상 차단
        if any(p in message for p in _VICTIM_PATTERNS):
            return ("block", _make_sensitive_answer(user_name))
        # 행정 의도 없음 → 차단
        if not has_admin_intent:
            return ("block", _make_sensitive_answer(user_name))
        # 신고 방법/절차 질문 → 고정 응답 (RAG 통과 시 법령 단계 나열 방지)
        if any(kw in message for kw in _HARASSMENT_REPORT_KEYWORDS):
            return ("block", _make_harassment_report_answer(user_name))
        # 그 외 행정 의도 → 정상 RAG
        return ("pass", None)

    # 4. 법적 리스크
    if any(kw in message for kw in _LEGAL_RISK_KEYWORDS):
        if not has_admin_intent:
            # 임금 체불 관련은 별도 안내 문구 적용
            wage_keywords = ["임금 체불", "월급을 못 받", "급여가 안 들어"]
            if any(kw in message for kw in wage_keywords):
                return ("block", _make_wage_answer(user_name))
            return ("block", _make_sensitive_answer(user_name))
        return ("pass", None)

    # 5. 감정적 위기 — 행정 의도 무관하게 항상 민감 응대
    if any(kw in message for kw in _EMOTIONAL_CRISIS_KEYWORDS):
        return ("sensitive", _make_sensitive_answer(user_name))

    # 6. 회사·사람 비방 → 소프트 리다이렉트
    if any(kw in message for kw in _SLANDER_KEYWORDS):
        return ("block", _SLANDER_ANSWER)

    return ("pass", None)
