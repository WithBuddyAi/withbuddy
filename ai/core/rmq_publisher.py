"""
RabbitMQ 메시지 발행 모듈
aio-pika 기반 비동기 구현.
발행 실패가 체인 흐름에 영향주지 않도록 예외를 내부에서 처리합니다.
"""

import asyncio
import json
import logging
from datetime import datetime, timezone

import aio_pika

from core.config import RMQ_URL

logger = logging.getLogger(__name__)

_NUDGE_EXCHANGE = "ex.nudge"
_NUDGE_ROUTING_KEY = "nudge"


async def publish_nudge(user_id: str, content: str) -> None:
    """q.nudge 큐로 넛지 메시지 발행. 백그라운드 태스크로 호출합니다."""
    try:
        conn = await aio_pika.connect_robust(RMQ_URL)
        async with conn:
            channel = await conn.channel()
            exchange = await channel.declare_exchange(
                _NUDGE_EXCHANGE,
                aio_pika.ExchangeType.DIRECT,
                durable=True,
            )
            body = json.dumps(
                {
                    "user_id": user_id,
                    "type": "nudge",
                    "content": content,
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                },
                ensure_ascii=False,
            ).encode()
            await exchange.publish(
                aio_pika.Message(
                    body,
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                ),
                routing_key=_NUDGE_ROUTING_KEY,
            )
    except Exception as e:
        logger.warning("Nudge 발행 실패 (무시): %s", e)
