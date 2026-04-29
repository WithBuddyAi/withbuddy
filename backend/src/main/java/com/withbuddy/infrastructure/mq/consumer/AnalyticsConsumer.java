package com.withbuddy.infrastructure.mq.consumer;

import com.rabbitmq.client.Channel;
import com.withbuddy.infrastructure.mq.MessagingMetricsService;
import com.withbuddy.infrastructure.mq.entity.MessagingEventLog;
import com.withbuddy.infrastructure.mq.entity.MessagingEventType;
import com.withbuddy.infrastructure.mq.event.AnalyticsEvent;
import com.withbuddy.infrastructure.mq.repository.MessagingEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private final MessagingEventLogRepository eventLogRepository;
    private final MessagingMetricsService metricsService;

    @RabbitListener(queues = "${app.rabbitmq.queue-analytics}", containerFactory = "rabbitListenerContainerFactory")
    public void handleAnalytics(
            @Payload AnalyticsEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        try {
            if (event == null || !StringUtils.hasText(event.eventId())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (!eventLogRepository.existsByEventId(event.eventId())) {
                eventLogRepository.save(new MessagingEventLog(
                        event.eventId(),
                        MessagingEventType.ANALYTICS,
                        LocalDateTime.now()
                ));
            }

            if (event.publishedAt() > 0L) {
                long latency = Math.max(0L, System.currentTimeMillis() - event.publishedAt());
                metricsService.recordQueueLatency(latency);
                metricsService.recordEndToEndLatency(latency);
            }
            metricsService.incrementSuccessCount();

            channel.basicAck(deliveryTag, false);
            log.debug("[ANALYTICS] processed. eventId={}, userId={}, action={}",
                    event.eventId(), event.userId(), event.action());
        } catch (Exception ex) {
            log.error("[ANALYTICS] processing failed. deliveryTag={}", deliveryTag, ex);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("[ANALYTICS] nack failed. deliveryTag={}", deliveryTag, nackEx);
            }
        }
    }
}

