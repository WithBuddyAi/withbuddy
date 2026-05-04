"""
FastAPI 메인 애플리케이션 진입점
────────────────────────────────────────────
수습사원 With Buddy 서버입니다.
아래 3개 라우터를 등록합니다:
  - /chat    : RAG 기반 멀티턴 질의응답
  - /report  : 주간 온보딩 리포트 생성
  - /recommend: 담당자 추천

실행 방법:
    uvicorn main:app --reload --host 0.0.0.0 --port 8000
"""

import asyncio
import logging
import os
from contextlib import asynccontextmanager

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

# .env 파일에서 환경변수 로드 (ANTHROPIC_API_KEY, SLACK_BOT_TOKEN 등)
# 라우터 import 전에 호출해야 be_client 등 모듈 초기화 시 env var 반영됨
load_dotenv()

from routers import admin, callback, chat, docs, knowledge, leader, mybuddy, preboarding, profile, recommend, report, slack
from tasks.scheduler import start_scheduler, stop_scheduler

logger = logging.getLogger(__name__)


# ── 앱 생애주기: 스케줄러 + Slack Socket Mode 시작/종료 ─────────

@asynccontextmanager
async def lifespan(_: FastAPI):
    start_scheduler()   # 평일 17:00 Slack 자동 리포트 ON

    # Gemini Embedding 콜드 스타트 제거
    try:
        from core.embeddings import get_embeddings
        get_embeddings().embed_query("warmup")
        logger.info("임베딩 모델 웜업 완료")
    except Exception as e:
        logger.warning("임베딩 웜업 실패(무시): %s", e)

    # Slack Socket Mode (버튼·모달 인터랙션 수신)
    socket_handler = None
    app_token = os.environ.get("SLACK_APP_TOKEN", "")
    if app_token.startswith("xapp-"):
        try:
            from slack_bolt.adapter.socket_mode.async_handler import AsyncSocketModeHandler
            from core.slack_bolt_app import bolt_app
            socket_handler = AsyncSocketModeHandler(bolt_app, app_token)
            asyncio.create_task(socket_handler.start_async())
            logger.info("Slack Socket Mode 시작 — 버튼·모달 인터랙션 활성화")
        except Exception as e:
            logger.error("Slack Socket Mode 시작 실패: %s", e)
    else:
        logger.warning("SLACK_APP_TOKEN 미설정 — Slack 인터랙션 비활성화 (.env 확인)")

    yield

    stop_scheduler()    # 앱 종료 시 스케줄러 정리
    if socket_handler:
        await socket_handler.close_async()
        logger.info("Slack Socket Mode 종료")


# ── FastAPI 앱 생성 ──────────────────────────

app = FastAPI(
    title="With Buddy",
    description="수습사원 온보딩을 지원하는 RAG 기반 AI 에이전트 API",
    version="1.0.0",
    docs_url="/docs",       # Swagger UI
    redoc_url="/redoc",     # ReDoc UI
    lifespan=lifespan,
)

# ── CORS 설정 (프론트엔드 연동 허용) ────────

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],       # 운영환경에서는 도메인을 명시할 것
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── 라우터 등록 ──────────────────────────────

app.include_router(admin.router)
app.include_router(callback.router)
app.include_router(chat.router)
app.include_router(report.router)
app.include_router(recommend.router)
app.include_router(leader.router)
app.include_router(docs.router)
app.include_router(slack.router)
app.include_router(knowledge.router)
app.include_router(mybuddy.router)
app.include_router(profile.router)
app.include_router(preboarding.router)

# ── Static 파일 서빙 ─────────────────────────
app.mount("/static", StaticFiles(directory="static"), name="static")


# ── UI 및 헬스체크 엔드포인트 ────────────────

@app.get("/", tags=["ui"])
async def root():
    """채팅 UI 페이지"""
    return FileResponse("static/index.html")


@app.get("/health", tags=["health"])
async def health_check():
    """헬스체크 엔드포인트"""
    return {"status": "healthy"}


# ── 로컬 실행 진입점 ─────────────────────────

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,   # 파일 변경 시 자동 재시작 (개발용)
    )
