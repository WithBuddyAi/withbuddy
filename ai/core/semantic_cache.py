"""
응답 캐시 — Redis 해시 기반 Exact Match
────────────────────────────────────────────
Key 규칙: withbuddy:cache:{company_code}:{sha256(normalized)[:16]}
TTL    : 24시간
"""

import hashlib
import json
from typing import Optional, Tuple

import redis

from core.config import CACHE_TTL, REDIS_DB, REDIS_HOST, REDIS_PASSWORD, REDIS_PORT


class RedisResponseCache:
    def __init__(self):
        self._client = redis.Redis(
            host=REDIS_HOST,
            port=REDIS_PORT,
            password=REDIS_PASSWORD,
            db=REDIS_DB,
            decode_responses=True,
        )

    def _key(self, question: str, company_code: str) -> str:
        h = hashlib.sha256(question.strip().lower().encode()).hexdigest()[:16]
        return f"withbuddy:cache:{company_code}:{h}"

    def get(self, question: str, company_code: str) -> Optional[Tuple]:
        raw = self._client.get(self._key(question, company_code))
        if not raw:
            return None
        data = json.loads(raw)
        return data["answer"], data["source"], data["related_docs"], data["doc_ids"]

    def set(
        self,
        question: str,
        company_code: str,
        answer: str,
        source: str,
        related_docs: list,
        doc_ids: list,
    ) -> None:
        payload = json.dumps(
            {"answer": answer, "source": source, "related_docs": related_docs, "doc_ids": doc_ids},
            ensure_ascii=False,
        )
        self._client.setex(self._key(question, company_code), CACHE_TTL, payload)

    def invalidate(self, company_code: str) -> None:
        """문서 업데이트 시 해당 company_code 캐시 전체 무효화"""
        keys = self._client.keys(f"withbuddy:cache:{company_code}:*")
        if keys:
            self._client.delete(*keys)


_cache = RedisResponseCache()


def get_response_cache() -> RedisResponseCache:
    return _cache
