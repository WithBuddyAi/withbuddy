package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.withbuddy.global.security.InternalApiSecurityProperties;
import com.withbuddy.internal.api.InternalTaskApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTaskAiNudgeAnalysisHandlerTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void handleSendsSignedCallbackHeaders() throws Exception {
        AtomicReference<String> signatureHeader = new AtomicReference<>();
        AtomicReference<String> timestampHeader = new AtomicReference<>();
        AtomicReference<String> requestIdHeader = new AtomicReference<>();
        AtomicReference<String> rawBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/ai/callback", exchange -> {
            capture(exchange, signatureHeader, timestampHeader, requestIdHeader, rawBody);
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        InternalApiSecurityProperties properties = new InternalApiSecurityProperties();
        properties.setToken("shared-secret");
        InternalTaskAiNudgeAnalysisHandler handler = new InternalTaskAiNudgeAnalysisHandler(
                new ObjectMapper(),
                properties
        );

        InternalTaskApiService.InternalTaskMessage message = new InternalTaskApiService.InternalTaskMessage(
                "task-1",
                "ai.nudge.analysis",
                JsonNodeFactory.instance.objectNode().put("question", "hello"),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/ai/callback",
                10,
                0,
                "2026-06-25T00:00:00Z"
        );

        var result = handler.handle(message);

        assertThat(result.get("callbackSent").asBoolean()).isTrue();
        assertThat(result.get("callbackStatus").asInt()).isEqualTo(200);
        assertThat(requestIdHeader.get()).isNotBlank();
        assertThat(timestampHeader.get()).isNotBlank();
        assertThat(signatureHeader.get()).isEqualTo(
                InternalTaskAiNudgeAnalysisHandler.signCallback(
                        "shared-secret",
                        timestampHeader.get(),
                        requestIdHeader.get(),
                        rawBody.get()
                )
        );
    }

    private void capture(
            HttpExchange exchange,
            AtomicReference<String> signatureHeader,
            AtomicReference<String> timestampHeader,
            AtomicReference<String> requestIdHeader,
            AtomicReference<String> rawBody
    ) throws IOException {
        signatureHeader.set(exchange.getRequestHeaders().getFirst("X-Callback-Signature"));
        timestampHeader.set(exchange.getRequestHeaders().getFirst("X-Callback-Timestamp"));
        requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
        rawBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }
}
