package com.withbuddy.infrastructure.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiStreamClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsAccountStateInChatStreamUserPayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(requestBody);
        try {
            AiStreamClient client = new AiStreamClient(
                    objectMapper,
                    "http://localhost:" + server.getAddress().getPort(),
                    1000,
                    5000
            );

            AiAnswerServerResponse response = client.streamAnswer(
                    201L,
                    1L,
                    "Jiwon Kim",
                    "WB0001",
                    "2026-05-01",
                    "ACTIVE",
                    "How do I request annual leave?",
                    delta -> {
                    }
            );

            JsonNode body = objectMapper.readTree(requestBody.get());
            assertThat(body.path("user").path("accountState").asText()).isEqualTo("ACTIVE");
            assertThat(response.getQuestionId()).isEqualTo(201L);
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/stream", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    event: answer_completed
                    data: {"questionId":201,"messageType":"rag_answer","content":"Answer.","documents":[],"recommendedContacts":[]}

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }
}
