package com.withbuddy.admin.activity.log;

import com.withbuddy.admin.activity.entity.EventTarget;
import com.withbuddy.admin.activity.entity.EventType;
import com.withbuddy.infrastructure.mq.AnalyticsEventPublisher;
import com.withbuddy.infrastructure.mq.event.AnalyticsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RmqActivityLogService {

    private final AnalyticsEventPublisher analyticsEventPublisher;

    public void publish(Long userId, EventType eventType, EventTarget eventTarget) {
        AnalyticsEvent event = new AnalyticsEvent(
                UUID.randomUUID().toString(),
                userId,
                eventType.name(),
                eventTarget != null ? eventTarget.name() : "UNKNOWN",
                System.currentTimeMillis()
        );
        try {
            analyticsEventPublisher.publish(event);
        } catch (RuntimeException ex) {
            log.warn("[ANALYTICS] RMQ publish failed. userId={}, eventType={}, eventTarget={}",
                    userId, eventType, eventTarget, ex);
        }
    }
}

