"""
미답변 질문 군집화 엔드포인트 (BE 연동용)
────────────────────────────────────────────
BE가 questionContent 목록을 전송하면 AI가 임베딩 생성 → 군집화 → 요약을 반환한다.
임베딩을 BE에서 받지 않고 AI가 직접 생성해 request body 크기 문제를 회피한다.

POST /clusters/no-result/questions
"""

import asyncio
import json
import logging
from dataclasses import dataclass
from datetime import date
from typing import List, Optional

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel

from core.embeddings import get_embeddings
from core.llm import get_llm

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/clusters", tags=["clusters"])

_DEFAULT_THRESHOLD = 0.82


# ── 요청/응답 스키마 ───────────────────────────────────────────────

class ClusterItem(BaseModel):
    logId: int
    questionContent: str


@dataclass
class _ItemWithEmbedding:
    logId: int
    questionContent: str
    embedding: List[float]


class ClusterRequest(BaseModel):
    companyCode: str
    analysisDate: date
    topN: int = 5
    items: List[ClusterItem]


class ActionItem(BaseModel):
    part: str
    items: str


class AiSummary(BaseModel):
    status: str
    summary: str
    actions: List[ActionItem]
    hasSensitive: bool
    errorMessage: Optional[str] = None


class TopQuestion(BaseModel):
    rank: int
    questionContent: str
    count: int


class ClusterResponse(BaseModel):
    companyCode: str
    analysisDate: date
    sourceCount: int
    topQuestions: List[TopQuestion]
    aiSummary: AiSummary


# ── 군집화 유틸 ───────────────────────────────────────────────────

def _cosine_sim(a: List[float], b: List[float]) -> float:
    a_arr = np.array(a, dtype=np.float32)
    b_arr = np.array(b, dtype=np.float32)
    denom = np.linalg.norm(a_arr) * np.linalg.norm(b_arr)
    return float(np.dot(a_arr, b_arr) / denom) if denom > 0 else 0.0


def _greedy_cluster(items: List[_ItemWithEmbedding], threshold: float) -> List[List[_ItemWithEmbedding]]:
    used = [False] * len(items)
    clusters = []
    for i in range(len(items)):
        if used[i]:
            continue
        cluster = [items[i]]
        used[i] = True
        for j in range(i + 1, len(items)):
            if not used[j] and _cosine_sim(items[i].embedding, items[j].embedding) >= threshold:
                cluster.append(items[j])
                used[j] = True
        clusters.append(cluster)
    return clusters


def _pick_representative(cluster: List[_ItemWithEmbedding]) -> str:
    freq: dict = {}
    for item in cluster:
        q = item.questionContent
        freq[q] = freq.get(q, 0) + 1
    return max(freq, key=lambda q: freq[q])


def _generate_summary(top_questions: List[TopQuestion]) -> AiSummary:
    if not top_questions:
        return AiSummary(
            status="EMPTY",
            summary="분석할 미답변 질문이 없습니다.",
            actions=[],
            hasSensitive=False,
        )

    questions_text = "\n".join(
        f"{q.rank}. {q.questionContent} ({q.count}건)" for q in top_questions
    )

    prompt = (
        "다음은 임직원들이 자주 물어봤지만 답변받지 못한 질문 목록입니다.\n\n"
        f"{questions_text}\n\n"
        "위 질문들을 분석하여 아래 JSON 형식으로만 응답하세요 (설명 없이):\n"
        '{"summary": "전체 패턴 요약 (2~3문장, 한국어)",'
        ' "actions": [{"part": "담당 부서", "items": "개선 권고 내용"}],'
        ' "hasSensitive": false}\n\n'
        "규칙:\n"
        "- hasSensitive: 급여·징계·개인정보 등 민감 내용 포함 시 true\n"
        "- actions: 최대 3개, 담당 부서와 구체적 개선 방향 명시\n"
        "- JSON만 출력"
    )

    try:
        resp = get_llm().invoke(prompt)
        data = json.loads(resp.content.strip())
        return AiSummary(
            status="READY",
            summary=data.get("summary", ""),
            actions=[ActionItem(**a) for a in data.get("actions", [])],
            hasSensitive=bool(data.get("hasSensitive", False)),
        )
    except Exception as e:
        logger.warning("클러스터 요약 생성 실패: %s", e)
        return AiSummary(
            status="FAILED",
            summary="",
            actions=[],
            hasSensitive=False,
            errorMessage=str(e),
        )


# ── 엔드포인트 ─────────────────────────────────────────────────────

@router.post("/no-result/questions", response_model=ClusterResponse)
async def cluster_no_result_questions(req: ClusterRequest) -> ClusterResponse:
    """미답변 질문 군집화 + 요약 생성. AI가 임베딩을 직접 생성해 request body 크기 문제 회피."""
    if not req.items:
        return ClusterResponse(
            companyCode=req.companyCode,
            analysisDate=req.analysisDate,
            sourceCount=0,
            topQuestions=[],
            aiSummary=AiSummary(
                status="EMPTY",
                summary="분석할 데이터가 없습니다.",
                actions=[],
                hasSensitive=False,
            ),
        )

    # 임베딩 배치 생성 (BE에서 받지 않고 AI가 직접 생성)
    try:
        vectors = await asyncio.to_thread(
            get_embeddings().embed_documents,
            [item.questionContent for item in req.items],
        )
    except Exception as e:
        logger.error("임베딩 생성 실패: %s", e)
        return ClusterResponse(
            companyCode=req.companyCode,
            analysisDate=req.analysisDate,
            sourceCount=len(req.items),
            topQuestions=[],
            aiSummary=AiSummary(status="FAILED", summary="", actions=[], hasSensitive=False, errorMessage=str(e)),
        )

    items_with_emb = [
        _ItemWithEmbedding(logId=item.logId, questionContent=item.questionContent, embedding=vec)
        for item, vec in zip(req.items, vectors)
    ]

    raw = _greedy_cluster(items_with_emb, _DEFAULT_THRESHOLD)
    raw.sort(key=lambda c: len(c), reverse=True)

    top = raw[: req.topN]
    top_questions = [
        TopQuestion(
            rank=idx + 1,
            questionContent=_pick_representative(cluster),
            count=len(cluster),
        )
        for idx, cluster in enumerate(top)
    ]

    ai_summary = await asyncio.to_thread(_generate_summary, top_questions)

    return ClusterResponse(
        companyCode=req.companyCode,
        analysisDate=req.analysisDate,
        sourceCount=len(req.items),
        topQuestions=top_questions,
        aiSummary=ai_summary,
    )
