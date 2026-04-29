package com.withbuddy.infrastructure.mq;

import com.withbuddy.infrastructure.mq.event.NudgeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NudgeEventPublisher {

    private static final String NUDGE_ROUTING_KEY = "nudge.trigger";

    private final RabbitTemplate rabbitTemplate;
    private final AppRabbitMqProperties properties;
    private final MessagingMetricsService metricsService;

    public void publish(NudgeEvent event) {
        long start = System.currentTimeMillis();
        try {
            rabbitTemplate.convertAndSend(
                    properties.exchange(),
                    NUDGE_ROUTING_KEY,
                    event
            );
            metricsService.recordPublishLatency(System.currentTimeMillis() - start);
        } catch (RuntimeException ex) {
            metricsService.incrementPublishFailure();
            throw ex;
        }
    }
}

