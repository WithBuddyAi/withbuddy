"""
차트 생성 모듈
────────────────────────────────────────────
Plotly를 사용하여 온보딩 리포트용 차트를 PNG 이미지로 생성합니다.
"""

from __future__ import annotations

import re
from typing import List


_TOPIC_LABELS = {
    "연차": "연차/휴가",
    "휴가": "연차/휴가",
    "IT": "IT/장비",
    "장비": "IT/장비",
    "계정": "IT/장비",
    "경비": "경비/재무",
    "급여": "급여/복리후생",
    "복리후생": "급여/복리후생",
    "사무용품": "총무",
    "계약": "법무/계약",
    "규정": "사내 규정",
    "교육": "교육/온보딩",
    "온보딩": "교육/온보딩",
    "시스템": "IT/장비",
    "AI": "AI/기술",
}


def _merge_topics(topics: List[str]) -> dict[str, int]:
    """키워드 목록을 카테고리별로 병합하여 빈도 반환."""
    counts: dict[str, int] = {}
    for t in topics:
        label = _TOPIC_LABELS.get(t, t)
        counts[label] = counts.get(label, 0) + 1
    return counts


def generate_topic_chart(topics: List[str]) -> bytes:
    """토픽 분포 가로 막대 차트 → PNG bytes."""
    import plotly.graph_objects as go

    counts = _merge_topics(topics)
    if not counts:
        counts = {"질문 없음": 1}

    labels = list(counts.keys())
    values = list(counts.values())

    fig = go.Figure(go.Bar(
        x=values,
        y=labels,
        orientation="h",
        marker=dict(
            color=values,
            colorscale="Blues",
            showscale=False,
        ),
        text=values,
        textposition="outside",
    ))
    fig.update_layout(
        title=dict(text="주요 질문 토픽 분포", font=dict(size=16, family="Arial")),
        xaxis=dict(title="언급 횟수", showgrid=True, gridcolor="#eee"),
        yaxis=dict(autorange="reversed"),
        plot_bgcolor="white",
        paper_bgcolor="white",
        margin=dict(l=20, r=40, t=50, b=40),
        width=520,
        height=max(200, 60 * len(labels) + 80),
        font=dict(family="Arial", size=12),
    )
    return fig.to_image(format="png", scale=2)


def generate_activity_gauge(question_count: int) -> bytes:
    """활동 지수 게이지 차트 → PNG bytes."""
    import plotly.graph_objects as go

    # 20문항 기준 100%
    pct = min(int(question_count / 20 * 100), 100)
    color = "#2196F3" if pct >= 50 else ("#FF9800" if pct >= 20 else "#F44336")

    fig = go.Figure(go.Indicator(
        mode="gauge+number+delta",
        value=question_count,
        title=dict(text="총 질문 수", font=dict(size=15)),
        gauge=dict(
            axis=dict(range=[0, 20], tickwidth=1),
            bar=dict(color=color),
            bgcolor="white",
            steps=[
                dict(range=[0, 5],  color="#FFEBEE"),
                dict(range=[5, 10], color="#FFF9C4"),
                dict(range=[10, 20], color="#E8F5E9"),
            ],
            threshold=dict(
                line=dict(color="gray", width=2),
                thickness=0.75,
                value=10,
            ),
        ),
        number=dict(suffix="건", font=dict(size=28)),
    ))
    fig.update_layout(
        width=320,
        height=240,
        margin=dict(l=20, r=20, t=40, b=20),
        paper_bgcolor="white",
        font=dict(family="Arial"),
    )
    return fig.to_image(format="png", scale=2)


def generate_onboarding_progress(question_count: int, topics: List[str]) -> bytes:
    """온보딩 진행도 요약 막대 → PNG bytes."""
    import plotly.graph_objects as go

    categories = ["활동량", "토픽 다양성", "참여도"]
    activity_score  = min(question_count / 15 * 100, 100)
    diversity_score = min(len(set(_TOPIC_LABELS.get(t, t) for t in topics)) / 5 * 100, 100)
    engagement_score = min((activity_score + diversity_score) / 2, 100)

    values = [round(activity_score), round(diversity_score), round(engagement_score)]
    colors = ["#42A5F5", "#66BB6A", "#FFA726"]

    fig = go.Figure(go.Bar(
        x=categories,
        y=values,
        marker_color=colors,
        text=[f"{v}%" for v in values],
        textposition="outside",
    ))
    fig.update_layout(
        title=dict(text="온보딩 진행도", font=dict(size=16, family="Arial")),
        yaxis=dict(range=[0, 115], ticksuffix="%", showgrid=True, gridcolor="#eee"),
        plot_bgcolor="white",
        paper_bgcolor="white",
        margin=dict(l=20, r=20, t=50, b=40),
        width=420,
        height=280,
        font=dict(family="Arial", size=12),
    )
    return fig.to_image(format="png", scale=2)
