"""
With Buddy - Streamlit 데모
──────────────────────────────────────────────
수습사원 온보딩을 지원하는 RAG 기반 AI 에이전트 (Streamlit 버전)
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from dotenv import load_dotenv
load_dotenv()

import streamlit as st

st.set_page_config(page_title="With Buddy", page_icon="🤝", layout="wide")

# ── 사이드바 ───────────────────────────────────────────────────────
with st.sidebar:
    st.title("🤝 With Buddy")
    st.caption("수습사원 온보딩 AI 어시스턴트")
    st.divider()

    user_id = st.text_input("사용자 ID", value="user_001")

    active_page = st.radio(
        "메뉴",
        [
            "💬 채팅",
            "📊 리포트",
            "🔍 담당자 추천",
            "✅ 체크리스트",
            "🗺️ 로드맵",
            "📋 마이버디",
            "📄 문서함",
            "🎉 온보딩",
            "👤 내 프로필",
            "──────────────",
            "👔 사수 페이지",
            "🔧 관리자",
        ],
        label_visibility="collapsed",
    )

    admin_ok = True
    leader_ok = True


if active_page == "──────────────":
    st.stop()

# ── 💬 채팅 ───────────────────────────────────────────────────────
if active_page == "💬 채팅":
    st.header("💬 AI 채팅")

    if "messages" not in st.session_state:
        st.session_state.messages = []

    for msg in st.session_state.messages:
        with st.chat_message(msg["role"]):
            st.write(msg["content"])
            if msg.get("source"):
                st.caption(f"출처: {msg['source']}")
            if msg.get("docs"):
                st.caption("관련 문서: " + " / ".join(
                    d["icon"] + " " + d["filename"] for d in msg["docs"]
                ))

    if prompt := st.chat_input("질문을 입력하세요..."):
        st.session_state.messages.append({"role": "user", "content": prompt})
        with st.chat_message("user"):
            st.write(prompt)

        with st.chat_message("assistant"):
            with st.spinner("생각 중..."):
                try:
                    from agents.orchestrator import run_orchestrator
                    from chains.rag_chain import run_rag_chain
                    from routers.docs import find_related_docs

                    ctx = run_orchestrator(user_id, "", prompt)

                    if ctx.intent == "rag":
                        answer, source, docs = run_rag_chain(
                            user_id, prompt, "",
                            profile_context=ctx.profile_context,
                            extra_context=ctx.extra_context,
                        )
                    else:
                        answer = ctx.answer
                        source = ""
                        docs = []

                    st.write(answer)
                    if source:
                        st.caption(f"출처: {source}")
                    if docs:
                        st.caption("관련 문서: " + " / ".join(
                            d["icon"] + " " + d["filename"] for d in docs
                        ))

                    st.session_state.messages.append({
                        "role": "assistant",
                        "content": answer,
                        "source": source,
                        "docs": docs,
                    })
                except Exception as e:
                    st.error(f"오류: {e}")

    st.divider()
    col1, col2 = st.columns([1, 6])
    with col1:
        if st.button("대화 요약"):
            with st.spinner("요약 중..."):
                try:
                    from memory.chat_history import get_history_as_text
                    from langchain_core.output_parsers import StrOutputParser
                    from langchain_core.prompts import ChatPromptTemplate
                    from core.llm import get_llm

                    history_text = get_history_as_text(user_id)
                    if history_text == "대화 내역이 없습니다.":
                        st.info("아직 대화 내역이 없습니다.")
                    else:
                        _PROMPT = ChatPromptTemplate.from_messages([
                            ("system", """당신은 With Buddy입니다.
아래 대화를 한국어로 간결하게 정리해주세요:
1. **주요 질문 요약** – bullet로 정리
2. **해결된 내용** – 핵심 안내 정보
3. **아직 확인이 필요한 사항**

[대화 내역]
{history}"""),
                            ("human", "위 대화를 요약 정리해주세요."),
                        ])
                        chain = _PROMPT | get_llm() | StrOutputParser()
                        summary = chain.invoke({"history": history_text})
                        st.info(summary)
                except Exception as e:
                    st.error(f"오류: {e}")
    with col2:
        if st.button("대화 초기화"):
            st.session_state.messages = []
            st.rerun()


# ── 📊 리포트 ──────────────────────────────────────────────────────
elif active_page == "📊 리포트":
    st.header("📊 주간 온보딩 리포트")

    if st.button("리포트 생성"):
        with st.spinner("분석 중..."):
            try:
                from chains.summary_chain import run_summary_chain

                report = run_summary_chain(user_id)
                st.markdown(report)
            except Exception as e:
                st.error(f"오류: {e}")


# ── 🔍 담당자 추천 ─────────────────────────────────────────────────
elif active_page == "🔍 담당자 추천":
    st.header("🔍 담당자 추천")

    DEPT_CHIPS = [
        ("👩 인사팀 · 김지수",   "연차·급여·복리후생·근태",   "인사 연차 급여 복리후생 관련 문의"),
        ("👨‍💻 IT팀 · 이민준",    "장비·계정·보안·인프라",     "IT 장비 계정 보안 시스템 문의"),
        ("🏢 총무팀 · 박서연",   "사무용품·시설·교통비·주차", "사무용품 시설 교통비 법인카드 문의"),
        ("💰 재무팀 · 최준혁",   "경비·예산·정산·세금계산서", "경비 처리 예산 세금계산서 정산 문의"),
        ("⚖️ 법무팀 · 김철수",   "계약·규정·개인정보·NDA",    "계약서 검토 법적 사내규정 개인정보 문의"),
        ("🤖 AI개발팀 · 김준수", "생성AI·LLM·AI에이전트",     "생성AI LLM AI 에이전트 개발 기술 지원 문의"),
        ("📚 교육팀 · 이수진",   "온보딩교육·사내연수·자격증", "온보딩 교육 사내 연수 자격증 문의"),
        ("📣 마케팅팀 · 정다은", "브랜드·SNS·콘텐츠·홍보",    "브랜드 SNS 콘텐츠 홍보 마케팅 문의"),
        ("🤝 영업팀 · 한승우",   "고객관리·계약·제안서",       "고객 계약 제안서 영업 문의"),
        ("💻 개발팀 · 오지현",   "서비스개발·배포·코드리뷰",   "서비스 개발 배포 코드 리뷰 기술 문의"),
        ("📊 데이터팀 · 윤재호", "데이터분석·BI·대시보드",     "데이터 분석 BI 대시보드 통계 문의"),
        ("🎧 고객지원팀 · 장미래","고객문의·VOC·CS처리",       "고객 문의 VOC CS 처리 문의"),
    ]

    if "recommend_input" not in st.session_state:
        st.session_state.recommend_input = ""

    st.write("**부서별 담당자 바로가기**")
    cols = st.columns(3)
    for i, (name, desc, query) in enumerate(DEPT_CHIPS):
        with cols[i % 3]:
            if st.button(f"{name}\n{desc}", key=f"chip_{i}", use_container_width=True):
                st.session_state.recommend_input = query

    st.divider()
    question = st.text_area("문의 내용을 입력하세요",
                            value=st.session_state.recommend_input,
                            placeholder="예: 연차 신청은 어디다 해요?")

    if st.button("추천받기") and question:
        with st.spinner("분석 중..."):
            try:
                import re
                from langchain_core.output_parsers import StrOutputParser
                from core.llm import get_llm
                from utils.prompts import RECOMMEND_PROMPT

                chain = RECOMMEND_PROMPT | get_llm() | StrOutputParser()
                raw = chain.invoke({"message": question})

                json_match = re.search(r'\{[^{}]+\}', raw, re.DOTALL)
                parsed = json.loads(json_match.group()) if json_match else {}

                department = parsed.get("department", "인사팀")
                person = parsed.get("person", "담당자")
                reason = parsed.get("reason", "")

                st.success(f"**{department}** - {person}")
                if reason:
                    st.caption(reason)
            except Exception as e:
                st.error(f"오류: {e}")


# ── ✅ 체크리스트 ──────────────────────────────────────────────────
elif active_page == "✅ 체크리스트":
    st.header("✅ 온보딩 체크리스트")

    department = st.text_input("부서명 입력", placeholder="예: 개발팀")

    if st.button("체크리스트 생성") and department:
        with st.spinner("생성 중..."):
            try:
                from chains.checklist_chain import run_checklist_chain

                checklist = run_checklist_chain(department)
                st.markdown(checklist)
            except Exception as e:
                st.error(f"오류: {e}")


# ── 🗺️ 로드맵 ─────────────────────────────────────────────────────
elif active_page == "🗺️ 로드맵":
    st.header("🗺️ 온보딩 로드맵")
    st.caption("주차별 온보딩 할 일을 확인하고 완료 항목을 체크하세요.")

    ROADMAP = [
        {
            "week": "1주차", "title": "입사 첫째 주 — 환경 세팅", "desc": "기본 장비·계정·공간 확보",
            "tasks": [
                "IT 장비 수령 (노트북, 모니터 등)",
                "사내 이메일 계정 활성화 확인",
                "협업 툴 계정 설정 (Slack, Notion 등)",
                "사원증·출입증 발급",
                "근로계약서·서약서 제출",
                "팀 소개 및 자리 배치 확인",
                "명함 신청",
                "사내 온보딩 가이드 정독",
            ],
        },
        {
            "week": "2주차", "title": "둘째 주 — 업무 파악", "desc": "팀 업무·주요 시스템 이해",
            "tasks": [
                "팀장·사수와 업무 범위 미팅",
                "주요 사내 시스템 계정 완료 (HR·재무 등)",
                "부서별 담당자 연락처 파악",
                "복리후생 내용 확인 (식대·교육비·건강검진)",
                "연차 부여 기준 및 신청 방법 확인",
                "경비 처리 프로세스 숙지",
                "팀 정기 회의 일정 파악 및 참석",
                "AI 에이전트로 궁금한 사항 3개 이상 질의",
            ],
        },
        {
            "week": "3주차", "title": "셋째 주 — 실무 적응", "desc": "실제 업무 참여 및 협업 시작",
            "tasks": [
                "첫 번째 실무 과제 수행",
                "관련 팀·부서 담당자 미팅 (필요 시)",
                "업무 관련 사내 규정·정책 문서 확인",
                "사내 교육 프로그램 신청 (필요 시)",
                "사수와 중간 점검 미팅",
                "법인카드·경비 사용 기준 확인",
                "재택근무 기준 및 신청 방법 확인",
            ],
        },
        {
            "week": "4주차", "title": "넷째 주 — 자리 잡기", "desc": "독립 업무 수행 및 1개월 회고",
            "tasks": [
                "1개월 업무 성과 정리 및 공유",
                "팀장과 1:1 피드백 미팅",
                "온보딩 AI 주간 리포트 확인",
                "미흡한 온보딩 항목 보완",
                "다음 달 개인 업무 목표 설정",
                "사내 커뮤니티·동호회 가입 (선택)",
            ],
        },
    ]

    if "roadmap_done" not in st.session_state:
        st.session_state.roadmap_done = {}

    total = sum(len(w["tasks"]) for w in ROADMAP)
    done_count = sum(1 for v in st.session_state.roadmap_done.values() if v)
    progress = done_count / total if total else 0

    st.progress(progress, text=f"전체 진행률 {done_count}/{total} ({int(progress*100)}%)")
    st.divider()

    for wi, week in enumerate(ROADMAP):
        with st.expander(f"**{week['week']}** — {week['title']}　*{week['desc']}*", expanded=(wi == 0)):
            for task in week["tasks"]:
                key = f"road_{wi}_{task}"
                checked = st.checkbox(task, value=st.session_state.roadmap_done.get(key, False), key=key)
                st.session_state.roadmap_done[key] = checked


# ── 📋 마이버디 (태스크) ───────────────────────────────────────────
elif active_page == "📋 마이버디":
    st.header("📋 마이버디 - 오늘 할 일")

    try:
        from memory.task_store import get_today_pending, get_all_tasks, mark_done

        pending = get_today_pending(user_id)
        all_tasks = get_all_tasks(user_id)

        if pending:
            st.info(f"오늘 해야 할 항목이 {len(pending)}개 있어요!")
            for task in pending:
                col1, col2 = st.columns([5, 1])
                with col1:
                    st.write(f"- {task['title']}")
                    if task.get("description"):
                        st.caption(task["description"])
                with col2:
                    if st.button("완료", key=f"done_{task['id']}"):
                        mark_done(user_id, task["id"], True)
                        st.rerun()
        else:
            st.success("오늘 할 일을 모두 완료했어요!")

        st.divider()
        st.subheader("전체 태스크")
        if all_tasks:
            for task in all_tasks:
                done = task.get("done", False)
                status = "✅" if done else "⬜"
                st.write(f"{status} **{task['title']}** ({task.get('due_date', '')})")
                if task.get("description"):
                    st.caption(task["description"])
        else:
            st.info("등록된 태스크가 없습니다.")

    except Exception as e:
        st.error(f"오류: {e}")


# ── 📄 문서함 ──────────────────────────────────────────────────────
elif active_page == "📄 문서함":
    st.header("📄 문서함")

    try:
        from routers.docs import list_docs, _DOCS_DIR

        result = list_docs()
        docs = result.docs

        if not docs:
            st.info("docs/ 폴더에 문서가 없습니다.")
        else:
            if "viewing_doc" not in st.session_state:
                st.session_state.viewing_doc = None

            for doc in docs:
                col1, col2, col3, col4, col5 = st.columns([4, 1, 1, 1, 1])
                with col1:
                    st.write(f"{doc.icon} {doc.filename}")
                with col2:
                    st.caption(doc.label)
                with col3:
                    st.caption(f"{doc.size_kb} KB")
                with col4:
                    if doc.viewable:
                        if st.button("보기", key=f"view_{doc.filename}"):
                            st.session_state.viewing_doc = doc.filename
                with col5:
                    st.caption(doc.updated)

            if st.session_state.viewing_doc:
                st.divider()
                col_back, col_title = st.columns([1, 8])
                with col_back:
                    if st.button("닫기"):
                        st.session_state.viewing_doc = None
                        st.rerun()
                with col_title:
                    st.subheader(st.session_state.viewing_doc)

                path = _DOCS_DIR / st.session_state.viewing_doc
                try:
                    content = path.read_text(encoding="utf-8")
                    st.markdown(content)
                except Exception as e:
                    st.error(f"파일 읽기 오류: {e}")
    except Exception as e:
        st.error(f"문서함 로드 오류: {e}")


# ── 🎉 온보딩 ──────────────────────────────────────────────────────
elif active_page == "🎉 온보딩":
    st.header("🎉 온보딩")

    tab1, tab2, tab3 = st.tabs(["환영 레터", "팀 카드", "말투 개선"])

    with tab1:
        st.subheader("개인화 환영 레터")
        if st.button("환영 레터 생성"):
            with st.spinner("생성 중..."):
                try:
                    from agents.preboarding_agent import generate_welcome_letter
                    from memory.company_info_store import get_company_info
                    from memory.profile_store import get_profile

                    profile = get_profile(user_id)
                    company_info = get_company_info()
                    letter = generate_welcome_letter(profile, company_info)
                    st.markdown(letter)
                except Exception as e:
                    st.error(f"오류: {e}")

    with tab2:
        st.subheader("팀 소개 카드")
        try:
            from agents.preboarding_agent import get_team_cards

            cards = get_team_cards()
            if not cards:
                st.info("팀 구성 정보가 없습니다. 관리자 페이지에서 설정해주세요.")
            else:
                cols = st.columns(min(len(cards), 3))
                for i, card in enumerate(cards):
                    with cols[i % 3]:
                        with st.container(border=True):
                            if card.get("photo_url"):
                                try:
                                    st.image(card["photo_url"], width=80)
                                except Exception:
                                    pass
                            st.write(f"**{card.get('name', '이름 없음')}**")
                            if card.get("role"):
                                st.caption(card["role"])
                            if card.get("department"):
                                st.caption(card["department"])
                            if card.get("mbti"):
                                st.caption(f"MBTI: {card['mbti']}")
                            if card.get("intro"):
                                st.write(card["intro"])
                            if card.get("favorite_restaurant"):
                                st.caption(f"맛집: {card['favorite_restaurant']}")
        except Exception as e:
            st.error(f"오류: {e}")

    with tab3:
        st.subheader("메시지 말투 개선")
        message = st.text_area("개선할 메시지를 입력하세요")
        if st.button("개선하기") and message:
            with st.spinner("개선 중..."):
                try:
                    from agents.communication_agent import run_communication_agent
                    from memory.profile_store import format_profile_context, get_profile

                    profile = get_profile(user_id)
                    profile_context = format_profile_context(profile)
                    result = run_communication_agent(message, profile_context)

                    st.success("개선된 메시지")
                    st.write(result.get("improved_message", message))

                    col1, col2 = st.columns(2)
                    with col1:
                        if result.get("target"):
                            st.info(f"문의 대상: {result['target']}")
                        if result.get("channel"):
                            st.info(f"추천 채널: {result['channel']}")
                        if result.get("reason"):
                            st.caption(result["reason"])
                    with col2:
                        if result.get("checklist"):
                            st.write("**확인 체크리스트**")
                            for item in result["checklist"]:
                                st.write(f"- {item}")
                        if result.get("tone_tips"):
                            st.caption(f"tip: {result['tone_tips']}")
                except Exception as e:
                    st.error(f"오류: {e}")


# ── 👤 내 프로필 ────────────────────────────────────────────────────
elif active_page == "👤 내 프로필":
    st.header("👤 내 프로필")

    from memory.profile_store import get_profile, save_profile

    profile = get_profile(user_id)

    with st.form("profile_form"):
        col1, col2 = st.columns(2)
        with col1:
            name = st.text_input("이름", value=profile.get("name", ""))
            department = st.text_input("부서", value=profile.get("department", ""))
            job_role = st.text_input("직무", value=profile.get("job_role", ""))
            start_date = st.text_input("입사일 (YYYY-MM-DD)", value=profile.get("start_date", ""))
            mbti = st.text_input("MBTI", value=profile.get("mbti", ""))
        with col2:
            comm_style = st.text_input("선호 커뮤니케이션", value=profile.get("comm_style", ""))
            interests_raw = st.text_input(
                "관심사 (쉼표 구분)",
                value=", ".join(profile.get("interests", [])),
            )
            favorite_restaurant = st.text_input(
                "좋아하는 맛집", value=profile.get("favorite_restaurant", "")
            )
            intro = st.text_input("한 줄 소개", value=profile.get("intro", ""))

        notes = st.text_area("메모", value=profile.get("notes", ""))

        if st.form_submit_button("저장"):
            updates = {}
            if name: updates["name"] = name
            if department: updates["department"] = department
            if job_role: updates["job_role"] = job_role
            if start_date: updates["start_date"] = start_date
            if mbti: updates["mbti"] = mbti
            if comm_style: updates["comm_style"] = comm_style
            if interests_raw:
                updates["interests"] = [i.strip() for i in interests_raw.split(",") if i.strip()]
            if favorite_restaurant: updates["favorite_restaurant"] = favorite_restaurant
            if intro: updates["intro"] = intro
            if notes: updates["notes"] = notes

            save_profile(user_id, updates)
            st.success("프로필이 저장되었습니다!")
            st.rerun()


# ── 👔 사수 페이지 ─────────────────────────────────────────────────
elif active_page == "👔 사수 페이지":
    if not leader_ok:
        st.warning("사이드바에서 사수 비밀번호를 입력해주세요.")
        st.stop()

    st.header("👔 사수 페이지")

    tab1, tab2, tab3 = st.tabs(["수습사원 통계", "진척도 리포트", "태스크 관리"])

    with tab1:
        st.subheader("수습사원 대화 통계")
        target_id = st.text_input("수습사원 ID", value="user_001", key="stats_uid")
        if st.button("통계 조회"):
            try:
                from memory.chat_history import get_chat_history

                history = get_chat_history(target_id)
                human_msgs = [m.content for m in history if m.type == "human"]

                keywords = ["연차", "휴가", "IT", "장비", "계정", "경비", "급여", "복리후생",
                            "사무용품", "계약", "규정", "교육", "온보딩", "시스템", "AI"]
                all_text = " ".join(human_msgs)
                found_topics = [kw for kw in keywords if kw in all_text]

                col1, col2 = st.columns(2)
                with col1:
                    st.metric("총 질문 수", len(human_msgs))
                with col2:
                    st.metric("관련 키워드", len(found_topics))

                if found_topics:
                    st.write("**주요 키워드:** " + ", ".join(found_topics))

                if human_msgs:
                    st.write("**최근 질문 (최신순)**")
                    for q in list(reversed(human_msgs))[:5]:
                        st.write(f"- {q}")
                else:
                    st.info("아직 대화 내역이 없습니다.")
            except Exception as e:
                st.error(f"오류: {e}")

    with tab2:
        st.subheader("AI 진척도 리포트")
        target_id2 = st.text_input("수습사원 ID", value="user_001", key="report_uid")
        if st.button("리포트 생성", key="leader_report_btn"):
            with st.spinner("분석 중..."):
                try:
                    from chains.summary_chain import run_summary_chain

                    report = run_summary_chain(target_id2)
                    st.markdown(report)
                except Exception as e:
                    st.error(f"오류: {e}")

    with tab3:
        st.subheader("태스크 관리")
        target_id3 = st.text_input("수습사원 ID", value="user_001", key="task_uid")

        with st.form("add_task_form"):
            task_title = st.text_input("태스크 제목")
            task_due = st.date_input("수행 날짜")
            task_desc = st.text_area("상세 설명")
            if st.form_submit_button("태스크 추가"):
                if task_title:
                    try:
                        from memory.task_store import add_task

                        add_task(target_id3, task_title, str(task_due), task_desc)
                        st.success("태스크가 추가되었습니다!")
                        st.rerun()
                    except Exception as e:
                        st.error(f"오류: {e}")

        st.divider()
        try:
            from memory.task_store import get_all_tasks

            tasks = get_all_tasks(target_id3)
            if tasks:
                for task in tasks:
                    done = task.get("done", False)
                    status = "✅" if done else "⬜"
                    st.write(f"{status} **{task['title']}** ({task.get('due_date', '')})")
            else:
                st.info("등록된 태스크가 없습니다.")
        except Exception as e:
            st.error(f"오류: {e}")


# ── 🔧 관리자 ──────────────────────────────────────────────────────
elif active_page == "🔧 관리자":
    if not admin_ok:
        st.warning("사이드바에서 관리자 비밀번호를 입력해주세요.")
        st.stop()

    st.header("🔧 관리자 페이지")

    tab1, tab2, tab3 = st.tabs(["회사 정보", "팀 구성", "미답변 질문"])

    with tab1:
        st.subheader("회사 정보 편집")
        from memory.company_info_store import get_company_info, save_company_info

        info = get_company_info()

        with st.form("company_form"):
            col1, col2 = st.columns(2)
            with col1:
                lunch_time = st.text_input("점심시간", value=info.get("lunch_time", ""))
                payday = st.text_input("급여일", value=info.get("payday", ""))
                work_hours = st.text_input("근무시간", value=info.get("work_hours", ""))
                dress_code = st.text_input("복장 규정", value=info.get("dress_code", ""))
            with col2:
                office_address = st.text_input("사무실 주소", value=info.get("office_address", ""))
                tools_raw = st.text_input(
                    "주요 업무 툴 (쉼표 구분)",
                    value=", ".join(info.get("tools", [])),
                )
                vacation_policy = st.text_area("연차 정책", value=info.get("vacation_policy", ""))
                welfare = st.text_area("복지혜택", value=info.get("welfare", ""))

            if st.form_submit_button("저장"):
                save_company_info({
                    "lunch_time": lunch_time,
                    "payday": payday,
                    "work_hours": work_hours,
                    "dress_code": dress_code,
                    "office_address": office_address,
                    "tools": [t.strip() for t in tools_raw.split(",") if t.strip()],
                    "vacation_policy": vacation_policy,
                    "welfare": welfare,
                })
                st.success("저장되었습니다!")

    with tab2:
        st.subheader("팀 구성 편집")

        _TEAM_CONFIG_PATH = Path(__file__).parent / "data" / "team_config.json"

        if _TEAM_CONFIG_PATH.exists():
            config = json.loads(_TEAM_CONFIG_PATH.read_text(encoding="utf-8"))
        else:
            config = {"teams": []}

        with st.expander("현재 팀 구성 JSON 보기"):
            st.json(config)

        st.divider()
        st.write("**팀원 카드 추가/수정**")
        with st.form("member_form"):
            col1, col2 = st.columns(2)
            with col1:
                m_name = st.text_input("이름 *")
                m_department = st.text_input("부서")
                m_mbti = st.text_input("MBTI")
            with col2:
                m_restaurant = st.text_input("좋아하는 맛집")
                m_intro = st.text_area("한 줄 소개")

            if st.form_submit_button("저장"):
                if not m_name:
                    st.error("이름은 필수입니다.")
                else:
                    if not config.get("teams"):
                        config["teams"] = [{"team_name": "팀", "members": []}]

                    updates = {k: v for k, v in {
                        "department": m_department or None,
                        "mbti": m_mbti or None,
                        "favorite_restaurant": m_restaurant or None,
                        "intro": m_intro or None,
                    }.items() if v}

                    found = False
                    for team in config["teams"]:
                        if team.get("leader_name") == m_name:
                            if updates.get("department"): team["leader_department"] = updates["department"]
                            if updates.get("mbti"): team["leader_mbti"] = updates["mbti"]
                            if updates.get("favorite_restaurant"): team["leader_restaurant"] = updates["favorite_restaurant"]
                            if updates.get("intro"): team["leader_intro"] = updates["intro"]
                            found = True
                            break
                        for m in team.get("members", []):
                            if m.get("name") == m_name:
                                m.update(updates)
                                found = True
                                break
                        if found:
                            break

                    if not found:
                        config["teams"][0].setdefault("members", []).append(
                            {"name": m_name, "role": "팀원", **updates}
                        )

                    _TEAM_CONFIG_PATH.write_text(
                        json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8"
                    )
                    st.success(f"'{m_name}' 카드가 저장되었습니다!")
                    st.rerun()

        st.divider()
        st.write("**팀원 카드 삭제**")
        with st.form("delete_member_form"):
            del_name = st.text_input("삭제할 팀원 이름")
            if st.form_submit_button("삭제"):
                if del_name:
                    removed = False
                    for team in config.get("teams", []):
                        before = len(team.get("members", []))
                        team["members"] = [m for m in team.get("members", []) if m.get("name") != del_name]
                        if len(team["members"]) < before:
                            removed = True
                    if removed:
                        _TEAM_CONFIG_PATH.write_text(
                            json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8"
                        )
                        st.success(f"'{del_name}' 카드가 삭제되었습니다.")
                        st.rerun()
                    else:
                        st.error(f"'{del_name}' 멤버를 찾을 수 없습니다.")

    with tab3:
        st.subheader("미답변 질문 처리")
        from memory.unanswered_store import get_all, answer_question
        from routers.knowledge import _validate_answer, _refine_answer, _QA_DOC_PATH
        from langchain_core.documents import Document
        from langchain_text_splitters import RecursiveCharacterTextSplitter
        from core.vectorstore import get_vectorstore

        items = get_all()
        pending = [i for i in items if i.get("status") == "pending"]
        answered = [i for i in items if i.get("status") == "answered"]

        st.write(f"미답변: **{len(pending)}개** / 답변 완료: **{len(answered)}개**")

        if not pending:
            st.success("미답변 질문이 없습니다!")
        else:
            for item in pending:
                with st.expander(f"❓ {item['question']}"):
                    st.caption(f"ID: {item['id']} | 등록: {item.get('timestamp', '')}")
                    answer_text = st.text_area("답변 입력", key=f"ans_{item['id']}")
                    if st.button("답변 저장", key=f"save_{item['id']}"):
                        if not answer_text:
                            st.warning("답변을 입력해주세요.")
                        elif not _validate_answer(answer_text):
                            st.error("부적절하거나 너무 짧은 답변입니다.")
                        else:
                            with st.spinner("저장 중..."):
                                try:
                                    refined = _refine_answer(answer_text, item["question"])
                                    answer_question(item["id"], refined)

                                    _QA_DOC_PATH.parent.mkdir(parents=True, exist_ok=True)
                                    if not _QA_DOC_PATH.exists():
                                        _QA_DOC_PATH.write_text(
                                            "# 사수 직접 답변 지식 모음\n\n",
                                            encoding="utf-8",
                                        )
                                    with open(_QA_DOC_PATH, "a", encoding="utf-8") as f:
                                        f.write(f"\n\n## Q: {item['question']}\n\n**A:** {refined}\n")

                                    doc = Document(
                                        page_content=f"질문: {item['question']}\n답변: {refined}",
                                        metadata={"source": "qa_knowledge.md"},
                                    )
                                    splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
                                    get_vectorstore().add_documents(splitter.split_documents([doc]))

                                    st.success("답변이 저장되고 AI 지식에 즉시 반영되었습니다!")
                                    st.rerun()
                                except Exception as e:
                                    st.error(f"오류: {e}")

        if answered:
            st.divider()
            with st.expander(f"답변 완료 목록 ({len(answered)}개)"):
                for item in answered:
                    st.write(f"**Q. {item['question']}**")
                    st.write(f"A. {item.get('answer', '')}")
                    st.caption(f"답변일: {item.get('answered_at', '')}")
                    st.divider()
