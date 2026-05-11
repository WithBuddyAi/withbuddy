"""
BE Internal API 클라이언트
────────────────────────────────────────────
Redis/RabbitMQ를 BE HTTP API를 통해 간접 사용합니다.
AI 서버는 REDIS_URL, RABBITMQ_URL을 직접 사용하지 않습니다.

환경변수:
  BACKEND_INTERNAL_URL  BE 내부 서버 주소 (예: http://10.0.0.81:8080)
  INTERNAL_API_KEY      X-API-Key 공유 시크릿
  AI_CALLBACK_URL       AI 서버 콜백 수신 주소 (예: https://ai.itsdev.kr)
"""

import hashlib
import hmac
import logging
import os
import time
import uuid

import requests

logger = logging.getLogger(__name__)

_BACKEND_URL = os.getenv("BACKEND_INTERNAL_URL", "http://10.0.0.81:8080")
_API_KEY = os.getenv("INTERNAL_API_KEY", "")
_AI_CALLBACK_URL = os.getenv("AI_CALLBACK_URL", "https://ai.itsdev.kr")


def _headers() -> dict:
    return {
        "Content-Type": "application/json",
        "X-API-Key": _API_KEY,
        "X-Request-Id": str(uuid.uuid4()),
    }


def _call(method: str, path: str, *, json_body=None, timeout: float = 2.0, max_retries: int = 1):
    """공통 HTTP 요청 헬퍼.
    - 429: retryAfterSeconds 대기 후 1회 재시도
    - 에러: RFC 9457 detail 필드 파싱해서 로깅
    """
    url = f"{_BACKEND_URL}{path}"
    for attempt in range(max_retries + 1):
        r = requests.request(method, url, headers=_headers(), json=json_body, timeout=timeout)

        if r.status_code == 429 and attempt < max_retries:
            try:
                retry_after = r.json().get("retryAfterSeconds", 1)
            except Exception:
                retry_after = 1
            logger.warning("BE API 429 Rate Limit [%s %s] — %s초 후 재시도", method, path, retry_after)
            time.sleep(retry_after)
            continue

        if not r.ok:
            try:
                err = r.json()
                logger.warning(
                    "BE API 오류 [%s %s] %s (%s): %s",
                    method, path,
                    err.get("title", r.status_code),
                    r.status_code,
                    err.get("detail", ""),
                )
            except Exception:
                logger.warning("BE API 오류 [%s %s] status=%s", method, path, r.status_code)

        r.raise_for_status()
        return r

    return None


# ── Cache API ────────────────────────────────────────────────

def cache_get(namespace: str, key: str):
    """캐시 단일 조회. 없으면 None 반환."""
    try:
        r = _call("POST", "/internal/v1/cache/get",
                  json_body={"namespace": namespace, "key": key, "defaultValue": None})
        data = r.json()
        return data.get("value") if data.get("found") else None
    except Exception as e:
        logger.warning("cache_get 실패 (%s:%s): %s", namespace, key, e)
        return None


def cache_get_multi(namespace: str, keys: list) -> dict:
    """캐시 다중 조회. {key: value} 형태로 반환 (found인 것만 포함)."""
    try:
        r = _call("POST", "/internal/v1/cache/get-multi",
                  json_body={"namespace": namespace, "keys": keys})
        items = r.json().get("items", [])
        return {item["key"]: item["value"] for item in items if item.get("found")}
    except Exception as e:
        logger.warning("cache_get_multi 실패 (%s): %s", namespace, e)
        return {}


def cache_set(namespace: str, key: str, value, ttl_seconds: int = 300) -> bool:
    """캐시 단일 저장. 실패 시 False 반환."""
    try:
        _call("POST", "/internal/v1/cache/set",
              json_body={"namespace": namespace, "key": key, "value": value, "ttlSeconds": ttl_seconds})
        return True
    except Exception as e:
        logger.warning("cache_set 실패 (%s:%s): %s", namespace, key, e)
        return False


def cache_set_multi(namespace: str, items: list[dict], ttl_seconds: int = 300) -> dict:
    """캐시 다중 저장. items: [{"key": ..., "value": ...}, ...]
    비원자적 — 부분 실패 가능.
    반환: {"ok": bool, "written": int, "errors": [...]}
    """
    try:
        payload = [{"key": i["key"], "value": i["value"], "ttlSeconds": ttl_seconds} for i in items]
        r = _call("POST", "/internal/v1/cache/set-multi",
                  json_body={"namespace": namespace, "items": payload})
        data = r.json()
        if not data.get("ok") and data.get("errors"):
            for err in data["errors"]:
                logger.warning("cache_set_multi 부분 실패 key=%s: %s", err.get("key"), err.get("detail"))
        return data
    except Exception as e:
        logger.warning("cache_set_multi 실패 (%s): %s", namespace, e)
        return {"ok": False, "written": 0, "errors": []}


def cache_del(namespace: str, key: str) -> bool:
    """캐시 삭제."""
    try:
        _call("POST", "/internal/v1/cache/del",
              json_body={"namespace": namespace, "key": key})
        return True
    except Exception as e:
        logger.warning("cache_del 실패 (%s:%s): %s", namespace, key, e)
        return False


# ── Task API (RabbitMQ 추상화) ───────────────────────────────

def enqueue_nudge(user_id: str, company_code: str, question: str, question_id: str) -> dict | None:
    """미답변 질문 nudge Task를 BE RMQ에 등록."""
    try:
        r = _call("POST", "/internal/v1/tasks",
                  json_body={
                      "type": "ai.nudge.analysis",
                      "payload": {
                          "userId": user_id,
                          "companyCode": company_code,
                          "question": question,
                          "questionId": question_id,
                      },
                      "priority": "normal",
                      "idempotencyKey": f"nudge-{question_id}-v1",
                      "timeoutSeconds": 600,
                      "retryCount": 3,
                      "callbackUrl": f"{_AI_CALLBACK_URL}/internal/ai/callback",
                  },
                  timeout=3.0,
                  max_retries=1)
        return r.json()
    except Exception as e:
        logger.warning("enqueue_nudge 실패 (user=%s): %s", user_id, e)
        return None


def get_task_status(task_id: str) -> dict | None:
    """Task 상태 조회."""
    try:
        r = _call("GET", f"/internal/v1/tasks/{task_id}")
        return r.json()
    except Exception as e:
        logger.warning("get_task_status 실패 (taskId=%s): %s", task_id, e)
        return None


def get_task_result(task_id: str) -> dict | None:
    """Task 결과 조회. TTL 만료(24h) 후 404 반환됨."""
    try:
        r = _call("GET", f"/internal/v1/tasks/{task_id}/result")
        return r.json()
    except Exception as e:
        logger.warning("get_task_result 실패 (taskId=%s): %s", task_id, e)
        return None


def cancel_task(task_id: str) -> dict | None:
    """Task 취소. SUCCESS/FAILED/TIMED_OUT/CANCELLED 상태에서 호출 시 409."""
    try:
        r = _call("DELETE", f"/internal/v1/tasks/{task_id}")
        return r.json()
    except Exception as e:
        logger.warning("cancel_task 실패 (taskId=%s): %s", task_id, e)
        return None


def retry_task(task_id: str, reason: str = "manual-retry") -> dict | None:
    """Task 재시도. RUNNING 상태 재시도 시 409, SUCCESS 재시도 시 새 taskId 발급."""
    try:
        r = _call("POST", f"/internal/v1/tasks/{task_id}/retry",
                  json_body={"reason": reason})
        return r.json()
    except Exception as e:
        logger.warning("retry_task 실패 (taskId=%s): %s", task_id, e)
        return None


# ── Callback 검증 ────────────────────────────────────────────

_template_cache: dict[str, tuple[float, list]] = {}  # {company_code: (timestamp, docs)}
_TEMPLATE_CACHE_TTL = 3600  # 1시간


def get_template_docs(company_code: str) -> list[dict]:
    """BE에서 TEMPLATE 타입 문서 목록 조회 (1시간 캐시). [{documentId, title, fileName}]"""
    now = time.time()
    cached = _template_cache.get(company_code)
    if cached and now - cached[0] < _TEMPLATE_CACHE_TTL:
        return cached[1]
    try:
        r = _call("GET", "/api/v1/documents?size=100&page=0&documentType=TEMPLATE")
        docs = r.json().get("content", [])

        def _infer_cc(d: dict) -> str:
            # BE 응답에 companyCode 필드가 있으면 우선 사용 (BE 패치 후 자동 적용)
            if "companyCode" in d:
                return d["companyCode"] or ""
            fn = d.get("fileName", "").lower()
            title = d.get("title", "")
            if fn.startswith("prism_") or "프리즘" in title or "prism" in title.lower():
                return "WB0002"
            return "WB0001"

        result = [
            {"documentId": d["documentId"], "title": d.get("title", ""), "fileName": d.get("fileName", "")}
            for d in docs
            if _infer_cc(d) == company_code
        ]
        _template_cache[company_code] = (now, result)
        logger.info("get_template_docs (company=%s): %d개 반환", company_code, len(result))
        return result
    except Exception as e:
        logger.warning("get_template_docs 실패 (company=%s): %s", company_code, e)
        return []


def get_presigned_url(doc_id: int) -> str | None:
    """문서 presigned URL 조회. AI 서버 API key(globalAccess)로 회사 체크 없이 호출."""
    try:
        r = _call("GET", f"/api/v1/documents/{doc_id}/download", timeout=3.0)
        data = r.json()
        if isinstance(data, list):
            return data[0].get("downloadUrl")
        return data.get("downloadUrl")
    except Exception as e:
        logger.warning("get_presigned_url 실패 (doc_id=%s): %s", doc_id, e)
        return None


def verify_callback_signature(body: bytes, signature: str, timestamp: str, request_id: str) -> bool:
    """BE 콜백 HMAC-SHA256 서명 검증.
    1. X-Callback-Timestamp ±300초 확인
    2. Canonical String 재조합 → HMAC-SHA256 비교
    """
    secret = _API_KEY
    if not secret:
        return False

    try:
        ts = int(timestamp)
        if abs(time.time() - ts) > 300:
            logger.warning("Callback timestamp 만료: %s", timestamp)
            return False
    except (ValueError, TypeError):
        return False

    canonical = f"{timestamp}\n{request_id}\n{body.decode()}"
    expected = "sha256=" + hmac.new(secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)
