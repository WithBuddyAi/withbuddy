package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.withbuddy.internal.api.InternalTaskApiService;
import com.withbuddy.internal.api.InternalTaskTypePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTaskAiNudgeAnalysisHandler implements InternalTaskHandler {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public boolean supports(String taskType) {
        return InternalTaskTypePolicy.AI_NUDGE_ANALYSIS.equals(taskType);
    }

    @Override
    public JsonNode handle(InternalTaskApiService.InternalTaskMessage message) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("taskId", message.taskId());
        result.put("type", message.type());
        result.set("payload", message.payload());
        result.put("processedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());

        if (!StringUtils.hasText(message.callbackUrl())) {
            result.put("callbackSent", false);
            return result;
        }
        String callbackUrl = message.callbackUrl().trim();

        ObjectNode callbackBody = objectMapper.createObjectNode();
        callbackBody.put("event", "TASK_COMPLETED");
        callbackBody.put("taskId", message.taskId());
        callbackBody.put("status", "SUCCESS");
        callbackBody.set("result", result.deepCopy());
        callbackBody.put("sentAt", OffsetDateTime.now(ZoneOffset.UTC).toString());

        try {
            String rawBody = objectMapper.writeValueAsString(callbackBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl))
                    .timeout(Duration.ofSeconds(resolveTimeoutSeconds(message.timeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(rawBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("callback failed. status=" + statusCode);
            }

            result.put("callbackSent", true);
            result.put("callbackStatus", statusCode);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("callback I/O failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("callback interrupted", e);
        }
    }

    private long resolveTimeoutSeconds(Integer timeoutSeconds) {
        if (timeoutSeconds == null) {
            return 10L;
        }
        return Math.max(1L, Math.min(timeoutSeconds, 30));
    }
}
