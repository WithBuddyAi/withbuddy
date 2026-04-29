package com.withbuddy.infrastructure.mq.event;

public record AnalyticsEvent(
        String eventId,
        Long userId,
        String action,
        String target,
        long publishedAt
) {
}

