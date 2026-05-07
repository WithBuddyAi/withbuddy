"""
BE Task 콜백 수신 엔드포인트
────────────────────────────────────────────
BE가 Task 완료 시 AI로 콜백을 전송합니다.
HMAC-SHA256 서명 검증 후 이벤트를 처리합니다.
"""

import json
import logging

from fastapi import APIRouter, HTTPException, Request

from core.be_client import cache_get, cache_set, verify_callback_signature

router = APIRouter(prefix="/internal/ai", tags=["internal-callback"])
logger = logging.getLogger(__name__)


@router.post("/callback")
async def receive_callback(request: Request):
    """BE Task 완료 콜백 수신"""
    body = await request.body()
    signature = request.headers.get("X-Callback-Signature", "")
    timestamp = request.headers.get("X-Callback-Timestamp", "")
    request_id = request.headers.get("X-Request-Id", "")

    # Nonce 중복 확인 (600초 TTL)
    nonce_key = f"nonce:{request_id}"
    if cache_get("callback", nonce_key) is not None:
        return {"ok": True}

    # HMAC 검증
    if not verify_callback_signature(body, signature, timestamp, request_id):
        raise HTTPException(status_code=401, detail="Invalid signature")

    # Nonce 저장
    cache_set("callback", nonce_key, 1, ttl_seconds=600)

    # 이벤트 처리
    try:
        event = json.loads(body)
        event_type = event.get("event")
        task_id = event.get("taskId")
        status = event.get("status")

        logger.info("Callback: event=%s taskId=%s status=%s", event_type, task_id, status)

        # 이벤트 타입별 처리 (필요 시 확장)
        if event_type == "TASK_COMPLETED" and status == "SUCCESS":
            pass  # 추후 필요한 처리 추가

    except Exception as e:
        logger.error("Callback 처리 오류: %s", e)

    return {"ok": True}
