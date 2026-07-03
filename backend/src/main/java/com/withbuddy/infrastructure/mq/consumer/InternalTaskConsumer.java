package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.withbuddy.infrastructure.mq.AppRabbitMqProperties;
import com.withbuddy.infrastructure.mq.MessagingMetricsService;
import com.withbuddy.internal.api.InternalTaskApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.internal-task-consumer", name = "enabled", havingValue = "true")
public class InternalTaskConsumer {

    private final InternalTaskApiService taskApiService;
    private final MessagingMetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final List<InternalTaskHandler> taskHandlers;
    private final AppRabbitMqProperties rabbitMqProperties;

    @RabbitListener(
            queues = "${app.rabbitmq.queue-internal-tasks}",
            containerFactory = "rabbitListenerContainerFactory",
            concurrency = "${app.internal-task-consumer.concurrency:1}"
    )
    public void handleInternalTask(
            @Payload InternalTaskApiService.InternalTaskMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        try {
            if (message == null || !StringUtils.hasText(message.taskId()) || !StringUtils.hasText(message.type())) {
                log.warn("[INTERNAL_TASK] invalid payload. deliveryTag={}", deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }

            Optional<InternalTaskApiService.TaskState> found = taskApiService.findState(message.taskId());
            if (found.isEmpty()) {
                log.warn("[INTERNAL_TASK] state not found. taskId={}", message.taskId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            InternalTaskApiService.TaskState state = found.get();
            if (isTerminal(state.status)) {
                log.info("[INTERNAL_TASK] already terminal. taskId={}, status={}", state.taskId, state.status);
                channel.basicAck(deliveryTag, false);
                return;
            }

            taskApiService.markRunning(message.taskId());
            InternalTaskHandler handler = findHandler(message.type())
                    .orElseThrow(() -> new IllegalStateException("No handler for task type: " + message.type()));

            JsonNode result = handler.handle(message);
            taskApiService.markSuccess(message.taskId(), result != null ? result : objectMapper.createObjectNode());

            queueLatencyFrom(message.requestedAt()).ifPresent(latency -> {
                metricsService.recordQueueLatency(latency);
                metricsService.recordEndToEndLatency(latency);
            });
            metricsService.incrementSuccessCount();

            channel.basicAck(deliveryTag, false);
            log.info("[INTERNAL_TASK] processed. taskId={}, type={}", message.taskId(), message.type());
        } catch (Exception ex) {
            boolean exhausted = isRetryExhausted();
            if (exhausted) {
                safeMarkFailed(message, ex);
            }
            log.error("[INTERNAL_TASK] processing failed. deliveryTag={}, exhausted={}", deliveryTag, exhausted, ex);
            throw new IllegalStateException("[INTERNAL_TASK] processing failed", ex);
        }
    }

    private Optional<InternalTaskHandler> findHandler(String taskType) {
        List<InternalTaskHandler> candidates = taskHandlers.stream()
                .filter(handler -> handler.supports(taskType))
                .collect(Collectors.toList());
        AnnotationAwareOrderComparator.sort(candidates);
        return candidates.stream().findFirst();
    }

    private Optional<Long> queueLatencyFrom(String requestedAt) {
        if (!StringUtils.hasText(requestedAt)) {
            return Optional.empty();
        }
        try {
            long publishedAt = OffsetDateTime.parse(requestedAt).toInstant().toEpochMilli();
            long latency = Math.max(0L, System.currentTimeMillis() - publishedAt);
            return Optional.of(latency);
        } catch (Exception ex) {
            log.debug("[INTERNAL_TASK] requestedAt parse failed. requestedAt={}", requestedAt, ex);
            return Optional.empty();
        }
    }

    private void safeMarkFailed(InternalTaskApiService.InternalTaskMessage message, Exception ex) {
        if (message == null || !StringUtils.hasText(message.taskId())) {
            return;
        }
        try {
            taskApiService.markFailed(message.taskId(), ex.getMessage());
        } catch (Exception markEx) {
            log.error("[INTERNAL_TASK] failed to mark task as FAILED. taskId={}", message.taskId(), markEx);
        }
    }

    private boolean isRetryExhausted() {
        int maxAttempts = rabbitMqProperties.listenerMaxAttempts() != null
                ? rabbitMqProperties.listenerMaxAttempts()
                : 3;
        var context = RetrySynchronizationManager.getContext();
        int currentAttempt = context == null ? 1 : context.getRetryCount() + 1;
        return currentAttempt >= maxAttempts;
    }

    private boolean isTerminal(String status) {
        return "SUCCESS".equals(status)
                || "FAILED".equals(status)
                || "TIMED_OUT".equals(status)
                || "CANCELLED".equals(status);
    }
}
