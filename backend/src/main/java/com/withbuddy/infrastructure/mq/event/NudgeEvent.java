package com.withbuddy.infrastructure.mq.event;

public record NudgeEvent(
        String eventId,
        Long userId,
        String message,
        String fileId,
        NudgeType type,
        long publishedAt
) {
}

