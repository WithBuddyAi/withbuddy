package com.withbuddy.infrastructure.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record AppRabbitMqProperties(
        String exchange,
        String queueNudge,
        String queueAnalytics,
        String queueInternalTasks,
        String queueInternalTasksDlq,
        Integer nudgeTtlMs,
        Integer analyticsTtlMs,
        Integer listenerPrefetch,
        Integer listenerMaxAttempts
) {
}
