package com.withbuddy.infrastructure.mq.consumer;

import com.rabbitmq.client.Channel;
import com.withbuddy.buddy.chat.service.ChatMessageService;
import com.withbuddy.infrastructure.mq.MessagingMetricsService;
import com.withbuddy.infrastructure.mq.entity.MessagingEventLog;
import com.withbuddy.infrastructure.mq.entity.MessagingEventType;
import com.withbuddy.infrastructure.mq.event.NudgeEvent;
import com.withbuddy.infrastructure.mq.event.NudgeType;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.infrastructure.mq.repository.MessagingEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
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
public class NudgeConsumer {

    private final ChatMessageService chatMessageService;
    private final RedisCacheService redisCacheService;
    private final MessagingEventLogRepository eventLogRepository;
    private final MessagingMetricsService metricsService;

    @RabbitListener(queues = "${app.rabbitmq.queue-nudge}", containerFactory = "rabbitListenerContainerFactory")
    public void handleNudge(
            @Payload NudgeEvent event,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        String idempotencyKey = null;
        try {
            if (event == null || !StringUtils.hasText(event.eventId())) {
                log.warn("[NUDGE] invalid payload. messageId={}", message.getMessageProperties().getMessageId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            if ("__FORCE_ERROR__".equals(event.message())) {
                throw new IllegalStateException("[TEST] forced nudge consumer error");
            }

            idempotencyKey = RedisCacheKeys.nudgeIdempotency(event.eventId());
            boolean isNew = redisCacheService.putIfAbsent(
                    idempotencyKey,
                    "1",
                    RedisCacheTtl.NUDGE_IDEMPOTENCY
            );
            if (!isNew) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (eventLogRepository.existsByEventId(event.eventId())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            chatMessageService.saveNudgeMessage(event.userId(), event.suggestionId(), withActionUrl(event));
            eventLogRepository.save(new MessagingEventLog(
                    event.eventId(),
                    MessagingEventType.NUDGE,
                    LocalDateTime.now()
            ));

            if (event.publishedAt() > 0L) {
                long latency = Math.max(0L, System.currentTimeMillis() - event.publishedAt());
                metricsService.recordQueueLatency(latency);
                metricsService.recordEndToEndLatency(latency);
            }
            metricsService.incrementSuccessCount();

            channel.basicAck(deliveryTag, false);
            log.info("[NUDGE] processed. eventId={}, userId={}", event.eventId(), event.userId());
        } catch (Exception ex) {
            if (StringUtils.hasText(idempotencyKey)) {
                // Retry 전파 시 idempotency 키로 인해 재처리가 막히지 않도록 복구한다.
                redisCacheService.delete(idempotencyKey);
            }
            log.error("[NUDGE] processing failed. deliveryTag={}", deliveryTag, ex);
            throw new IllegalStateException("[NUDGE] processing failed", ex);
        }
    }

    private String withActionUrl(NudgeEvent event) {
        if (event.type() != NudgeType.FILE && event.type() != NudgeType.RAG_RESULT) {
            return event.message();
        }
        if (!StringUtils.hasText(event.actionUrl())) {
            return event.message();
        }
        return event.message() + "\n\n다운로드 링크: " + event.actionUrl();
    }
}

