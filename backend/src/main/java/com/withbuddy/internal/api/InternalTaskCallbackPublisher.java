package com.withbuddy.internal.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.withbuddy.global.security.InternalApiSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalTaskCallbackPublisher {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final InternalApiSecurityProperties internalApiSecurityProperties;

    @Value("${app.internal-task.callback-connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${app.internal-task.callback-read-timeout-ms:5000}")
    private int readTimeoutMs;

    public void publishTaskCompleted(InternalTaskApiService.TaskState state) {
        if (state == null || !StringUtils.hasText(state.callbackUrl)) {
            return;
        }

        String secret = internalApiSecurityProperties.getToken();
        if (!StringUtils.hasText(secret)) {
            log.warn("[INTERNAL-TASK] callback secret missing. taskId={}", state.taskId);
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("event", "TASK_COMPLETED");
        payload.put("taskId", state.taskId);
        payload.put("type", state.type);
        payload.put("status", state.status);
        if (state.result != null) {
            payload.set("result", state.result);
        }
        if (StringUtils.hasText(state.error)) {
            payload.put("error", state.error);
        }
        payload.put("sentAt", OffsetDateTime.now(ZoneOffset.UTC).toString());

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("[INTERNAL-TASK] callback payload serialize failed. taskId={}", state.taskId, e);
            return;
        }

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String requestId = UUID.randomUUID().toString();
        String canonical = timestamp + "\n" + requestId + "\n" + body;
        String signature;
        try {
            signature = "sha256=" + signHex(secret, canonical);
        } catch (GeneralSecurityException e) {
            log.warn("[INTERNAL-TASK] callback signature generate failed. taskId={}", state.taskId, e);
            return;
        }

        String internalHeaderName = StringUtils.hasText(internalApiSecurityProperties.getHeaderName())
                ? internalApiSecurityProperties.getHeaderName()
                : "X-API-Key";

        try {
            restClient().post()
                    .uri(state.callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Callback-Signature", signature)
                    .header("X-Callback-Timestamp", timestamp)
                    .header("X-Request-Id", requestId)
                    .header(internalHeaderName, secret)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[INTERNAL-TASK] callback delivered. taskId={}, status={}", state.taskId, state.status);
        } catch (Exception ex) {
            log.warn("[INTERNAL-TASK] callback delivery failed. taskId={}, callbackUrl={}",
                    state.taskId, state.callbackUrl, ex);
        }
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().requestFactory(factory).build();
    }

    private String signHex(String secret, String canonical) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
