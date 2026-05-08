package com.withbuddy.infrastructure.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.chat.dto.ChatStreamAnswerDeltaResponse;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

@Component
@Slf4j
public class AiStreamClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration streamTimeout;

    public AiStreamClient(
            ObjectMapper objectMapper,
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ai.server.stream-timeout-ms:45000}") int streamTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.baseUri = URI.create(aiBaseUrl.endsWith("/") ? aiBaseUrl : aiBaseUrl + "/");
        this.streamTimeout = Duration.ofMillis(streamTimeoutMs);
    }

    public AiAnswerServerResponse streamAnswer(
            Long questionId,
            Long userId,
            String userName,
            String companyCode,
            String hireDate,
            String content,
            Consumer<ChatStreamAnswerDeltaResponse> onDelta
    ) {
        HttpRequest request;
        try {
            JsonNode userNode = objectMapper.createObjectNode()
                    .put("userId", userId)
                    .put("name", userName == null ? "" : userName)
                    .put("companyCode", companyCode == null ? "" : companyCode)
                    .put("hireDate", hireDate == null ? "" : hireDate);
            JsonNode body = objectMapper.createObjectNode()
                    .put("questionId", questionId)
                    .set("user", userNode);
            ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("content", content);

            request = HttpRequest.newBuilder(baseUri.resolve("chat/stream"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .timeout(streamTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("AI 스트림 요청 생성에 실패했습니다.", e);
        }

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String responseBody = readBody(response.body());
                throw new IllegalStateException(
                        "AI 스트림 호출이 실패했습니다. status=%d, body=%s".formatted(response.statusCode(), responseBody)
                );
            }

            return parseStream(questionId, response.body(), onDelta);
        } catch (HttpTimeoutException e) {
            throw new AiTimeoutException("AI 스트림 응답 시간이 초과되었습니다.", e);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                throw new IllegalStateException("AI 서버 연결에 실패했습니다.", e);
            }
            throw new IllegalStateException("AI 스트림 처리 중 오류가 발생했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 스트림 처리가 중단되었습니다.", e);
        }
    }

    private AiAnswerServerResponse parseStream(
            Long expectedQuestionId,
            InputStream bodyStream,
            Consumer<ChatStreamAnswerDeltaResponse> onDelta
    ) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
            String line;
            String eventName = null;
            StringBuilder dataBuilder = new StringBuilder();
            AiAnswerServerResponse completed = null;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    completed = dispatchEvent(expectedQuestionId, eventName, dataBuilder, onDelta, completed);
                    eventName = null;
                    dataBuilder.setLength(0);
                    continue;
                }

                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (!dataBuilder.isEmpty()) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(line.substring("data:".length()).trim());
                }
            }

            completed = dispatchEvent(expectedQuestionId, eventName, dataBuilder, onDelta, completed);
            if (completed == null) {
                throw new IllegalStateException("AI 스트림이 answer_completed 이벤트 없이 종료되었습니다.");
            }
            return completed;
        }
    }

    private AiAnswerServerResponse dispatchEvent(
            Long expectedQuestionId,
            String eventName,
            StringBuilder dataBuilder,
            Consumer<ChatStreamAnswerDeltaResponse> onDelta,
            AiAnswerServerResponse completed
    ) throws IOException {
        if ((eventName == null || eventName.isBlank()) && dataBuilder.isEmpty()) {
            return completed;
        }
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalStateException("AI SSE event 이름이 없습니다.");
        }
        if (dataBuilder.isEmpty()) {
            throw new IllegalStateException("AI SSE event data가 비어 있습니다. event=" + eventName);
        }

        String data = dataBuilder.toString();
        return switch (eventName) {
            case "answer_delta" -> {
                ChatStreamAnswerDeltaResponse delta = objectMapper.readValue(data, ChatStreamAnswerDeltaResponse.class);
                validateQuestionId(expectedQuestionId, delta.getQuestionId(), eventName);
                onDelta.accept(delta);
                yield completed;
            }
            case "answer_completed" -> {
                if (completed != null) {
                    throw new IllegalStateException("AI answer_completed 이벤트가 중복 전송되었습니다.");
                }
                AiAnswerServerResponse response = objectMapper.readValue(data, AiAnswerServerResponse.class);
                validateQuestionId(expectedQuestionId, response.getQuestionId(), eventName);
                yield response;
            }
            case "error" -> throw new IllegalStateException("AI error event: " + data);
            default -> throw new IllegalStateException("예상하지 못한 AI SSE event입니다. event=" + eventName);
        };
    }

    private void validateQuestionId(Long expectedQuestionId, Long actualQuestionId, String eventName) {
        if (!expectedQuestionId.equals(actualQuestionId)) {
            throw new IllegalStateException(
                    "AI %s 이벤트의 questionId가 일치하지 않습니다. expected=%d, actual=%d"
                            .formatted(eventName, expectedQuestionId, actualQuestionId)
            );
        }
    }

    private String readBody(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException e) {
            log.warn("Failed to read AI error response body.", e);
            return "";
        }
    }
}
