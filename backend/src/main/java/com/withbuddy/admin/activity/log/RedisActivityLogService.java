package com.withbuddy.admin.activity.log;

import com.withbuddy.admin.activity.entity.EventTarget;
import com.withbuddy.admin.activity.entity.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisActivityLogService {

    private static final String ACTIVITY_LOG_KEY = "log:activity:events";
    private static final Duration ACTIVITY_LOG_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public void append(Long userId, EventType eventType, EventTarget eventTarget) {
        String target = eventTarget != null ? eventTarget.name() : "UNKNOWN";
        String payload = String.format(
                "{\"userId\":%d,\"eventType\":\"%s\",\"eventTarget\":\"%s\",\"loggedAt\":\"%s\"}",
                userId,
                eventType.name(),
                target,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        try {
            redisTemplate.opsForList().leftPush(ACTIVITY_LOG_KEY, payload);
            redisTemplate.opsForList().trim(ACTIVITY_LOG_KEY, 0, 999);
            redisTemplate.expire(ACTIVITY_LOG_KEY, ACTIVITY_LOG_TTL);
        } catch (RuntimeException ex) {
            log.warn("[ACTIVITY] Redis log append failed. userId={}, eventType={}, eventTarget={}",
                    userId, eventType, target, ex);
        }
    }
}

