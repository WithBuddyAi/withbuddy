"""
AI API 비용 서킷브레이커
────────────────────────────────────────────
시간당/일당 비용 임계치 초과 시 Slack 알림 + 자동 차단.
임계치는 .env 환경변수로 조정 가능.
"""

import logging
import os
import threading
import time

from langchain_core.callbacks import BaseCallbackHandler

logger = logging.getLogger(__name__)

# Haiku 단가 ($/1K tokens)
_INPUT_COST_PER_1K = 0.0008
_OUTPUT_COST_PER_1K = 0.004

# Slack 알림 쿨다운 (초) — 같은 구간 알림 반복 방지
_ALERT_COOLDOWN = 600


class _CircuitBreakerState:
    def __init__(self):
        self._lock = threading.Lock()
        self.hourly_cost: float = 0.0
        self.daily_cost: float = 0.0
        self.hour_start: float = time.time()
        self.day_start: float = time.time()
        self.is_blocked: bool = False
        self.block_reason: str = ""
        self._last_hourly_warn: float = 0.0
        self._last_daily_warn: float = 0.0

    def _thresholds(self):
        return {
            "warn_hourly": float(os.getenv("CB_WARN_HOURLY", "3.0")),
            "block_hourly": float(os.getenv("CB_BLOCK_HOURLY", "5.0")),
            "warn_daily": float(os.getenv("CB_WARN_DAILY", "15.0")),
            "block_daily": float(os.getenv("CB_BLOCK_DAILY", "25.0")),
        }

    def _auto_reset(self, now: float):
        t = self._thresholds()
        if now - self.hour_start >= 3600:
            self.hourly_cost = 0.0
            self.hour_start = now
            if self.is_blocked and "시간당" in self.block_reason:
                self.is_blocked = False
                self.block_reason = ""
                logger.warning("[CircuitBreaker] 시간당 카운터 리셋 → 차단 해제")
        if now - self.day_start >= 86400:
            self.daily_cost = 0.0
            self.day_start = now
            if self.is_blocked:
                self.is_blocked = False
                self.block_reason = ""
                logger.warning("[CircuitBreaker] 일당 카운터 리셋 → 차단 해제")

    def add_cost(self, input_tokens: int, output_tokens: int):
        cost = (input_tokens * _INPUT_COST_PER_1K / 1000) + (output_tokens * _OUTPUT_COST_PER_1K / 1000)
        now = time.time()
        with self._lock:
            self._auto_reset(now)
            self.hourly_cost += cost
            self.daily_cost += cost
            t = self._thresholds()

            if self.hourly_cost >= t["block_hourly"] and not self.is_blocked:
                self.is_blocked = True
                self.block_reason = f"시간당 비용 ${self.hourly_cost:.2f} 초과"
                _slack(f"🚫 *AI 서킷브레이커 차단*\n시간당 비용 *${self.hourly_cost:.2f}* 초과 (한도: ${t['block_hourly']})\n`POST /internal/circuit-breaker/reset` 으로 해제")
            elif self.hourly_cost >= t["warn_hourly"] and now - self._last_hourly_warn > _ALERT_COOLDOWN:
                self._last_hourly_warn = now
                _slack(f"⚠️ *AI 비용 경고*\n시간당 *${self.hourly_cost:.2f}* / 한도 ${t['block_hourly']}")

            if self.daily_cost >= t["block_daily"] and not self.is_blocked:
                self.is_blocked = True
                self.block_reason = f"일당 비용 ${self.daily_cost:.2f} 초과"
                _slack(f"🚫 *AI 서킷브레이커 차단*\n일당 비용 *${self.daily_cost:.2f}* 초과 (한도: ${t['block_daily']})\n`POST /internal/circuit-breaker/reset` 으로 해제")
            elif self.daily_cost >= t["warn_daily"] and now - self._last_daily_warn > _ALERT_COOLDOWN:
                self._last_daily_warn = now
                _slack(f"⚠️ *AI 비용 경고*\n일당 *${self.daily_cost:.2f}* / 한도 ${t['block_daily']}")

    def check(self) -> tuple[bool, str]:
        with self._lock:
            self._auto_reset(time.time())
            return self.is_blocked, self.block_reason

    def reset(self):
        with self._lock:
            self.hourly_cost = 0.0
            self.is_blocked = False
            self.block_reason = ""
            logger.warning("[CircuitBreaker] 수동 리셋")

    def status(self) -> dict:
        t = self._thresholds()
        with self._lock:
            self._auto_reset(time.time())
            return {
                "is_blocked": self.is_blocked,
                "block_reason": self.block_reason,
                "hourly_cost_usd": round(self.hourly_cost, 4),
                "daily_cost_usd": round(self.daily_cost, 4),
                "thresholds": t,
            }


_state = _CircuitBreakerState()


def _slack(message: str):
    """Slack 알림을 백그라운드 스레드에서 전송 (응답 지연 없음)"""
    token = os.getenv("SLACK_BOT_TOKEN", "")
    channel = os.getenv("CB_SLACK_CHANNEL", "")
    if not token or not channel:
        logger.warning("[CircuitBreaker] Slack 알림 미전송 (CB_SLACK_CHANNEL 미설정): %s", message)
        return

    def _send():
        try:
            import requests as _req
            _req.post(
                "https://slack.com/api/chat.postMessage",
                json={"channel": channel, "text": message},
                headers={"Authorization": f"Bearer {token}"},
                timeout=5,
            )
        except Exception as e:
            logger.warning("[CircuitBreaker] Slack 전송 실패: %s", e)

    threading.Thread(target=_send, daemon=True).start()


class CostTrackerCallback(BaseCallbackHandler):
    """LLM 응답 후 토큰 집계 콜백"""

    def on_llm_end(self, response, **kwargs):
        try:
            usage = (response.llm_output or {}).get("usage", {})
            _state.add_cost(
                input_tokens=usage.get("input_tokens", 0),
                output_tokens=usage.get("output_tokens", 0),
            )
        except Exception:
            pass


def check_circuit_breaker():
    """엔드포인트 진입 시 호출 — 차단 상태면 HTTPException 429 발생"""
    from fastapi import HTTPException
    blocked, reason = _state.check()
    if blocked:
        raise HTTPException(
            status_code=429,
            detail=f"AI 서비스가 비용 한도로 임시 차단되었습니다. ({reason}) 담당자가 /internal/circuit-breaker/reset 으로 해제할 수 있습니다.",
        )


def get_state() -> _CircuitBreakerState:
    return _state
