package com.withbuddy.infrastructure.mq;

import com.withbuddy.infrastructure.mq.event.AnalyticsEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalyticsEventPublisher {

    private static final String ANALYTICS_ROUTING_KEY = "analytics.behavior";

    private final RabbitTemplate rabbitTemplate;
    private final AppRabbitMqProperties properties;
    private final MessagingMetricsService metricsService;

    public void publish(AnalyticsEvent event) {
        long start = System.currentTimeMillis();
        try {
            rabbitTemplate.convertAndSend(
                    properties.exchange(),
                    ANALYTICS_ROUTING_KEY,
                    event
            );
            metricsService.recordPublishLatency(System.currentTimeMillis() - start);
        } catch (RuntimeException ex) {
            metricsService.incrementPublishFailure();
            throw ex;
        }
    }
}

