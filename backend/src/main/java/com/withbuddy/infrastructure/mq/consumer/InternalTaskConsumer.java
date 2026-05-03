package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.Channel;
import com.withbuddy.infrastructure.mq.MessagingMetricsService;
import com.withbuddy.infrastructure.mq.NudgeEventPublisher;
import com.withbuddy.internal.api.InternalTaskApiService;
import com.withbuddy.internal.api.InternalTaskCallbackPublisher;
import com.withbuddy.infrastructure.mq.event.NudgeEvent;
import com.withbuddy.infrastructure.mq.event.NudgeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalTaskConsumer {

    private final InternalTaskApiService internalTaskApiService;
    private final InternalTaskCallbackPublisher callbackPublisher;
    private final MessagingMetricsService metricsService;
    private final NudgeEventPublisher nudgeEventPublisher;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue-internal-tasks}", containerFactory = "rabbitListenerContainerFactory")
    public void handleInternalTask(
            @Payload InternalTaskApiService.InternalTaskMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        if (message == null || !StringUtils.hasText(message.taskId())) {
            ackQuietly(channel, deliveryTag);
            return;
        }

        String taskId = message.taskId();
        try {
            if (isTimedOut(message)) {
                InternalTaskApiService.TaskState timedOut = internalTaskApiService
                        .markTimedOut(taskId, "task timeout exceeded before worker processing");
                callbackPublisher.publishTaskCompleted(timedOut);
                ackQuietly(channel, deliveryTag);
                return;
            }

            InternalTaskApiService.TaskState running = internalTaskApiService.markRunning(taskId);
            if ("SUCCESS".equals(running.status) || "FAILED".equals(running.status)
                    || "TIMED_OUT".equals(running.status) || "CANCELLED".equals(running.status)) {
                ackQuietly(channel, deliveryTag);
                return;
            }

            JsonNode result = processTask(message);
            InternalTaskApiService.TaskState success = internalTaskApiService.markSuccess(taskId, result);
            callbackPublisher.publishTaskCompleted(success);
            recordLatency(message.requestedAt());
            metricsService.incrementSuccessCount();
            ackQuietly(channel, deliveryTag);
            log.info("[INTERNAL-TASK] processed. taskId={}, type={}", taskId, message.type());
        } catch (Exception ex) {
            try {
                InternalTaskApiService.TaskState failed = internalTaskApiService.markFailed(taskId, ex.getMessage());
                callbackPublisher.publishTaskCompleted(failed);
            } catch (Exception markEx) {
                log.error("[INTERNAL-TASK] markFailed/callback failed. taskId={}", taskId, markEx);
            }
            ackQuietly(channel, deliveryTag);
            log.error("[INTERNAL-TASK] processing failed. taskId={}, type={}", taskId, message.type(), ex);
        }
    }

    private JsonNode processTask(InternalTaskApiService.InternalTaskMessage message) {
        if (!"ai.nudge.analysis".equals(message.type())) {
            throw new IllegalArgumentException("unsupported task type: " + message.type());
        }
        Long userId = readRequiredUserId(message.payload());
        String question = readOptionalText(message.payload(), "question");
        String companyCode = readOptionalText(message.payload(), "companyCode");
        String questionId = readOptionalText(message.payload(), "questionId");
        String actionUrl = readOptionalText(message.payload(), "actionUrl");
        NudgeType nudgeType = readOptionalNudgeType(message.payload());
        String nudgeText = buildNudgeMessage(companyCode, question);

        NudgeEvent nudgeEvent = new NudgeEvent(
                message.taskId(),
                userId,
                null,
                nudgeText,
                null,
                actionUrl,
                nudgeType,
                System.currentTimeMillis()
        );
        nudgeEventPublisher.publish(nudgeEvent);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("taskType", message.type());
        result.put("handled", true);
        result.put("action", "nudge-message-dispatched");
        result.put("nudgeEventId", message.taskId());
        result.put("userId", userId);
        if (StringUtils.hasText(questionId)) {
            result.put("questionId", questionId);
        }
        if (StringUtils.hasText(actionUrl)) {
            result.put("actionUrl", actionUrl);
        }
        result.put("nudgeType", nudgeType.name());
        result.put("message", nudgeText);
        if (StringUtils.hasText(question)) {
            result.put("questionPreview", trimForPreview(question, 80));
        }
        return result;
    }

    private Long readRequiredUserId(JsonNode payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        JsonNode node = payload.get("userId");
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("payload.userId is required");
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.parseLong(node.asText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("payload.userId is invalid", ex);
        }
    }

    private String readOptionalText(JsonNode payload, String field) {
        if (payload == null) {
            return "";
        }
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        String text = node.asText("");
        return text == null ? "" : text.trim();
    }

    private NudgeType readOptionalNudgeType(JsonNode payload) {
        String type = readOptionalText(payload, "nudgeType");
        if (!StringUtils.hasText(type)) {
            return NudgeType.GENERAL;
        }
        try {
            return NudgeType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NudgeType.GENERAL;
        }
    }

    private String buildNudgeMessage(String companyCode, String question) {
        Map<String, String> teamNameByCompany = Map.of(
                "WB0001", "경영지원팀",
                "WB0002", "운영팀"
        );
        String team = teamNameByCompany.getOrDefault(companyCode, "담당 부서");
        String preview = StringUtils.hasText(question) ? "질문: \"" + trimForPreview(question, 60) + "\"\n" : "";
        return "[안내] 문의 주신 내용을 " + team + "에서 확인 중이에요.\n"
                + preview
                + "확인 후 필요한 안내를 이어서 도와드릴게요.";
    }

    private String trimForPreview(String value, int maxLen) {
        if (!StringUtils.hasText(value) || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private boolean isTimedOut(InternalTaskApiService.InternalTaskMessage message) {
        if (!StringUtils.hasText(message.requestedAt()) || message.timeoutSeconds() == null) {
            return false;
        }
        try {
            OffsetDateTime requestedAt = OffsetDateTime.parse(message.requestedAt());
            long elapsed = Duration.between(requestedAt, OffsetDateTime.now()).toSeconds();
            return elapsed > message.timeoutSeconds();
        } catch (Exception ex) {
            log.debug("[INTERNAL-TASK] requestedAt parse failed. requestedAt={}", message.requestedAt(), ex);
            return false;
        }
    }

    private void recordLatency(String requestedAtRaw) {
        if (!StringUtils.hasText(requestedAtRaw)) {
            return;
        }
        try {
            OffsetDateTime requestedAt = OffsetDateTime.parse(requestedAtRaw);
            long latencyMs = Math.max(0L, Duration.between(requestedAt, OffsetDateTime.now()).toMillis());
            metricsService.recordQueueLatency(latencyMs);
            metricsService.recordEndToEndLatency(latencyMs);
        } catch (Exception ex) {
            log.debug("[INTERNAL-TASK] latency parse failed. requestedAt={}", requestedAtRaw, ex);
        }
    }

    private void ackQuietly(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("[INTERNAL-TASK] ack failed. deliveryTag={}", deliveryTag, ex);
        }
    }
}
