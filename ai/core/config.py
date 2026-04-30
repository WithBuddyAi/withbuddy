import os
from urllib.parse import quote
from dotenv import load_dotenv

load_dotenv()

# Redis
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD") or None
REDIS_DB = int(os.getenv("REDIS_DB", "0"))

_redis_pw_enc = quote(REDIS_PASSWORD, safe="") if REDIS_PASSWORD else ""
REDIS_URL = os.getenv(
    "REDIS_URL",
    f"redis://:{_redis_pw_enc}@{REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}",
)

# RabbitMQ
RMQ_HOST = os.getenv("RMQ_HOST", "localhost")
RMQ_PORT = int(os.getenv("RMQ_PORT", "5672"))
RMQ_USERNAME = os.getenv("RMQ_USERNAME", "withbuddy_app")
RMQ_PASSWORD = os.getenv("RMQ_PASSWORD") or None
RMQ_VHOST = os.getenv("RMQ_VHOST", "/")

_rmq_pw_enc = quote(RMQ_PASSWORD, safe="") if RMQ_PASSWORD else ""
_rmq_vh_enc = quote(RMQ_VHOST, safe="")
RMQ_URL = os.getenv(
    "RABBITMQ_URL",
    f"amqp://{RMQ_USERNAME}:{_rmq_pw_enc}@{RMQ_HOST}:{RMQ_PORT}/{_rmq_vh_enc}",
)

# Chat history
HISTORY_TTL = 60 * 60 * 24 * 7  # 7일
HISTORY_MAX_TURNS = 5

# Cache
CACHE_TTL = 60 * 60 * 24  # 24시간
