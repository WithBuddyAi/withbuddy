package com.withbuddy.infrastructure.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record AppRabbitMqProperties(
        String exchange,
        String dlxExchange,
        String queueReport,
        String queueNudge,
        String queueAnalytics,
        String queueDlq,
        String queueDlqNudge,
        String queueDlqAnalytics,
        Integer nudgeTtlMs
) {
}
