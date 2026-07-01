"""
Prometheus 커스텀 메트릭 정의 (SCRUM-563)
────────────────────────────────────────────
/metrics 엔드포인트에 노출되며 Grafana에서 시각화됩니다.
"""

from prometheus_client import Counter, Histogram

answer_type_counter = Counter(
    "ai_answer_type_total",
    "AI 응답 유형별 카운터",
    ["message_type"],  # rag_answer / no_result / out_of_scope / sensitive / clarifying
)

answer_latency_histogram = Histogram(
    "ai_answer_latency_seconds",
    "AI 응답 처리 시간 (초) — p50/p90 산출 가능",
    buckets=[0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0],
)

llm_call_counter = Counter(
    "ai_llm_calls_total",
    "LLM API 호출 횟수",
    ["purpose"],  # intent / chitchat / rag
)

agent_fallback_counter = Counter(
    "ai_agent_fallback_total",
    "Agent fallback 발생 횟수 (no_result → rag_answer 업그레이드)",
)
