package com.withbuddy.infrastructure.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record AppRabbitMqProperties(
        String exchange,
        String queueReport,
        String queueDlq
) {
}
