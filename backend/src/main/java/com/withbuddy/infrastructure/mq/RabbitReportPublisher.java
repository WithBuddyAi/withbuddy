package com.withbuddy.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitReportPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AppRabbitMqProperties properties;

    public void publish(Object payload) {
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.queueReport(),
                payload
        );
    }
}
