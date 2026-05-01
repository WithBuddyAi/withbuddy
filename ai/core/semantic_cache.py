"""
Semantic Cache — 의미 기반 질문 캐싱
────────────────────────────────────────────
동일하거나 유사한 질문(코사인 유사도 0.95 이상)에 대해 캐시된 답변을 반환합니다.
company_code별로 캐시를 분리하여 멀티테넌트 격리를 유지합니다.
TTL(24시간) 이후 만료된 항목은 자동으로 제거됩니다.
"""

import time
from typing import Optional, Tuple, List

_THRESHOLD = 0.95   # 유사도 임계값
_TTL = 86400        # 캐시 유효 시간 (24시간)


class _Entry:
    __slots__ = ("embedding", "answer", "source", "related_docs", "doc_ids", "ts")

    def __init__(self, embedding, answer: str, source: str, related_docs: list, doc_ids: list):
        self.embedding = embedding
        self.answer = answer
        self.source = source
        self.related_docs = related_docs
        self.doc_ids = doc_ids
        self.ts = time.time()


class SemanticCache:
    def __init__(self):
        self._store: dict[str, list[_Entry]] = {}
        self._embeddings = None

    def _get_embeddings(self):
        if self._embeddings is None:
            from core.embeddings import get_embeddings
            self._embeddings = get_embeddings()
        return self._embeddings

    def _similarity(self, a, b) -> float:
        # normalize_embeddings=True → 이미 정규화되어 있으므로 내적 = 코사인 유사도
        return sum(x * y for x, y in zip(a, b))

    def get(self, question: str, company_code: str) -> Optional[Tuple]:
        entries = self._store.get(company_code, [])
        if not entries:
            return None
        now = time.time()
        emb = self._get_embeddings().embed_query(question)
        best_sim = 0.0
        best: Optional[_Entry] = None
        for e in entries:
            if now - e.ts > _TTL:
                continue
            sim = self._similarity(emb, e.embedding)
            if sim > best_sim:
                best_sim = sim
                best = e
        if best and best_sim >= _THRESHOLD:
            return best.answer, best.source, best.related_docs, best.doc_ids
        return None

    def set(self, question: str, company_code: str, answer: str, source: str, related_docs: list, doc_ids: list):
        emb = self._get_embeddings().embed_query(question)
        now = time.time()
        entries = self._store.get(company_code, [])
        # 만료 항목 정리
        self._store[company_code] = [e for e in entries if now - e.ts <= _TTL]
        self._store[company_code].append(_Entry(emb, answer, source, related_docs, doc_ids))


_cache = SemanticCache()


def get_semantic_cache() -> SemanticCache:
    return _cache
