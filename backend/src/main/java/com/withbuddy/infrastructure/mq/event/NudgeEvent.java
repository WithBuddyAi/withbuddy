package com.withbuddy.infrastructure.mq.event;

public record NudgeEvent(
        String eventId,
        Long userId,
        Long suggestionId,
        String message,
        String fileId,
        NudgeType type,
        long publishedAt
) {
}

