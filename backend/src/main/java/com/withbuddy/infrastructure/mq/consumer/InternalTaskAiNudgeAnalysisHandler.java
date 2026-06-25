package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
 import com.withbuddy.global.security.InternalApiSecurityProperties;
import com.withbuddy.internal.api.InternalTaskApiService;
import com.withbuddy.internal.api.InternalTaskTypePolicy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTaskAiNudgeAnalysisHandler implements InternalTaskHandler {

    private final ObjectMapper objectMapper;
    private final InternalApiSecurityProperties internalApiSecurityProperties;
    private final HttpClient httpClient;

    public InternalTaskAiNudgeAnalysisHandler(
            ObjectMapper objectMapper,
            InternalApiSecurityProperties internalApiSecurityProperties
    ) {
        this(
                objectMapper,
                internalApiSecurityProperties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build()
        );
    }

    InternalTaskAiNudgeAnalysisHandler(
            ObjectMapper objectMapper,
            InternalApiSecurityProperties internalApiSecurityProperties,
            HttpClient httpClient
    ) {
        this.objectMapper = objectMapper;
        this.internalApiSecurityProperties = internalApiSecurityProperties;
        this.httpClient = httpClient;
    }

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
            String requestId = UUID.randomUUID().toString();
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature = signCallback(resolveCallbackSecret(), timestamp, requestId, rawBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl))
                    .timeout(Duration.ofSeconds(resolveTimeoutSeconds(message.timeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("X-Request-Id", requestId)
                    .header("X-Callback-Timestamp", timestamp)
                    .header("X-Callback-Signature", signature)
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

    static String signCallback(String secret, String timestamp, String requestId, String rawBody) {
        String canonical = timestamp + "\n" + requestId + "\n" + rawBody;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("callback signature generation failed", ex);
        }
    }

    private String resolveCallbackSecret() {
        String secret = internalApiSecurityProperties.getToken();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("callback secret missing");
        }
        return secret.trim();
    }

    private long resolveTimeoutSeconds(Integer timeoutSeconds) {
        if (timeoutSeconds == null) {
            return 10L;
        }
        return Math.max(1L, Math.min(timeoutSeconds, 30));
    }
}
