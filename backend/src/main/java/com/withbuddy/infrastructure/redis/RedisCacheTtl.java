package com.withbuddy.infrastructure.redis;

import java.time.Duration;

public final class RedisCacheTtl {

    private RedisCacheTtl() {
    }

    public static final Duration SESSION_TOKEN = Duration.ofHours(9);
    public static final Duration USER_PROFILE = Duration.ofMinutes(30);
    public static final Duration SSE_SESSION = Duration.ofMinutes(32);
    public static final Duration SSE_MISSED = Duration.ofMinutes(5);
    public static final Duration RAG_STATUS = Duration.ofMinutes(5);
    public static final Duration BUDDY_DAY = Duration.ofHours(24);
    public static final Duration NUDGE_SENT = Duration.ofHours(48);
    public static final Duration QUICK_TAP = Duration.ofHours(1);
    public static final Duration CONVERSATION = Duration.ofMinutes(30);
    public static final Duration PRESIGNED_URL = Duration.ofMinutes(10);
    public static final Duration DOCS_LIST_FIRST = Duration.ofMinutes(5);
}
