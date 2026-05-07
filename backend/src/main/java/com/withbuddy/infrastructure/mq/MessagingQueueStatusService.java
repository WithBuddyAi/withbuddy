package com.withbuddy.infrastructure.mq;

import com.rabbitmq.client.AMQP;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingQueueStatusService {

    private final RabbitTemplate rabbitTemplate;
    private final AppRabbitMqProperties properties;
    private final MessagingMetricsService metricsService;

    public Map<String, Object> getStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("exchange", properties.exchange());
        response.put("queues", Map.of(
                properties.queueNudge(), queueSnapshot(properties.queueNudge()),
                properties.queueAnalytics(), queueSnapshot(properties.queueAnalytics()),
                properties.queueInternalTasks(), queueSnapshot(properties.queueInternalTasks())
        ));
        response.put("metrics", metricsService.snapshot());
        return response;
    }

    private Map<String, Object> queueSnapshot(String queueName) {
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("name", queueName);
        try {
            AMQP.Queue.DeclareOk declareOk = rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queueName));
            if (declareOk == null) {
                queue.put("readyMessages", 0);
                queue.put("consumers", 0);
            } else {
                queue.put("readyMessages", declareOk.getMessageCount());
                queue.put("consumers", declareOk.getConsumerCount());
            }
        } catch (Exception ex) {
            log.warn("[RMQ] queue status unavailable: {}", queueName, ex);
            queue.put("error", ex.getMessage());
        }
        return queue;
    }
}
