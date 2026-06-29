"""
미답변 질문 의미 군집화 (SCRUM-551)
────────────────────────────────────────────
Greedy cosine similarity 기반 군집화.
임베딩은 unanswered.json에 저장, 결과는 24시간 캐시.
"""

import json
from datetime import datetime, timedelta
from pathlib import Path

import numpy as np

from core.embeddings import get_embeddings
from memory.unanswered_store import get_all, save_embeddings_batch

_DATA_DIR = Path(__file__).parent.parent / "data"
_CACHE_TTL = timedelta(hours=24)
_DEFAULT_THRESHOLD = 0.82


def _cache_path(company_code: str) -> Path:
    return _DATA_DIR / f"clusters_{company_code}.json"


def _load_cache(company_code: str) -> list | None:
    path = _cache_path(company_code)
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if datetime.now() - datetime.fromisoformat(data["cached_at"]) > _CACHE_TTL:
            return None
        return data["clusters"]
    except Exception:
        return None


def _write_cache(company_code: str, clusters: list) -> None:
    _DATA_DIR.mkdir(parents=True, exist_ok=True)
    _cache_path(company_code).write_text(
        json.dumps(
            {"cached_at": datetime.now().isoformat(timespec="seconds"), "clusters": clusters},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def _cosine_sim(a: list, b: list) -> float:
    a_arr = np.array(a, dtype=np.float32)
    b_arr = np.array(b, dtype=np.float32)
    denom = np.linalg.norm(a_arr) * np.linalg.norm(b_arr)
    return float(np.dot(a_arr, b_arr) / denom) if denom > 0 else 0.0


def _fill_embeddings(items: list) -> list:
    """임베딩 없는 항목 생성 후 unanswered.json에 저장. 실패한 항목은 건너뜀.
    배치 처리 우선, 실패 시 개별 처리로 폴백."""
    missing = [i for i in items if not i.get("embedding")]
    if not missing:
        return [i for i in items if i.get("embedding")]

    emb_model = get_embeddings()
    updates = {}

    try:
        vectors = emb_model.embed_documents([i["question"] for i in missing])
        for item, vec in zip(missing, vectors):
            item["embedding"] = vec
            updates[item["id"]] = vec
    except Exception:
        # 배치 실패 시 개별 처리로 폴백
        for item in missing:
            try:
                vec = emb_model.embed_query(item["question"])
                item["embedding"] = vec
                updates[item["id"]] = vec
            except Exception:
                pass

    if updates:
        try:
            save_embeddings_batch(updates)
        except Exception:
            pass

    return [i for i in items if i.get("embedding")]


def _greedy_cluster(items: list, threshold: float) -> list:
    """
    첫 번째 항목을 기준점으로 greedy 군집화.
    threshold 이상이면 같은 클러스터로 묶음.
    """
    used = [False] * len(items)
    clusters = []
    for i in range(len(items)):
        if used[i]:
            continue
        cluster = [items[i]]
        used[i] = True
        for j in range(i + 1, len(items)):
            if not used[j] and _cosine_sim(items[i]["embedding"], items[j]["embedding"]) >= threshold:
                cluster.append(items[j])
                used[j] = True
        clusters.append(cluster)
    return clusters


def _pick_representative(cluster: list) -> str:
    """빈도 최다 표현 우선, 동률이면 가장 최근 질문 선택."""
    freq: dict[str, int] = {}
    latest: dict[str, str] = {}
    for item in cluster:
        q = item["question"]
        freq[q] = freq.get(q, 0) + 1
        if q not in latest or item["timestamp"] > latest[q]:
            latest[q] = item["timestamp"]
    return max(freq, key=lambda q: (freq[q], latest[q]))


def compute_clusters(company_code: str, top_n: int = 5, threshold: float = _DEFAULT_THRESHOLD) -> list:
    """
    미답변 질문 의미 군집화. 24시간 캐시 사용.

    Returns:
        totalCount 내림차순 정렬된 상위 top_n 클러스터 목록.
        각 항목: {representative, variantCount, totalCount, variants}
    """
    cached = _load_cache(company_code)
    if cached is not None:
        return cached[:top_n]

    items = [
        i for i in get_all()
        if i.get("company_code") == company_code and i.get("status") == "pending"
    ]
    if not items:
        _write_cache(company_code, [])
        return []

    items = _fill_embeddings(items)
    raw = _greedy_cluster(items, threshold)

    clusters = []
    for group in raw:
        rep = _pick_representative(group)
        variants = list({i["question"] for i in group if i["question"] != rep})
        clusters.append({
            "representative": rep,
            "variantCount": len(variants),
            "totalCount": len(group),
            "variants": variants,
        })

    clusters.sort(key=lambda c: c["totalCount"], reverse=True)
    _write_cache(company_code, clusters)
    return clusters[:top_n]


def invalidate_cache(company_code: str) -> None:
    """캐시 강제 무효화 (새 질문 추가 시 호출 가능)."""
    path = _cache_path(company_code)
    if path.exists():
        path.unlink()
