package com.withbuddy.internal.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.infrastructure.mq.AppRabbitMqProperties;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.withbuddy.internal.api.InternalApiModels.TaskCreateRequest;
import static com.withbuddy.internal.api.InternalApiModels.TaskCreateResponse;
import static com.withbuddy.internal.api.InternalApiModels.TaskStatusResponse;

@Service
@RequiredArgsConstructor
public class InternalTaskApiService {

    private static final Duration TASK_TTL = Duration.ofHours(24);
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final int DEFAULT_RETRY_COUNT = 0;
    private static final String ROUTING_KEY = "internal.jobs.requested";

    private final RabbitTemplate rabbitTemplate;
    private final AppRabbitMqProperties rabbitMqProperties;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    public TaskCreateResponse create(TaskCreateRequest request) {
        validateCallbackUrl(request.callbackUrl());
        String normalizedType = request.type().trim();
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<TaskCreateResponse> duplicated = getByIdempotencyKey(idempotencyKey);
            if (duplicated.isPresent()) {
                return duplicated.get();
            }
        }

        String now = nowUtc();
        String taskId = UUID.randomUUID().toString();
        int timeoutSeconds = request.timeoutSeconds() != null ? request.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        int retryCount = request.retryCount() != null ? request.retryCount() : DEFAULT_RETRY_COUNT;

        TaskState state = new TaskState(
                taskId,
                normalizedType,
                "QUEUED",
                request.payload(),
                null,
                null,
                request.callbackUrl(),
                timeoutSeconds,
                retryCount,
                now,
                now
        );
        saveState(state);
        if (idempotencyKey != null) {
            redisCacheService.put(idempotencyRedisKey(idempotencyKey), taskId, TASK_TTL);
        }

        InternalTaskMessage taskMessage = new InternalTaskMessage(
                taskId,
                normalizedType,
                request.payload(),
                request.callbackUrl(),
                timeoutSeconds,
                retryCount,
                now
        );

        try {
            rabbitTemplate.convertAndSend(
                    rabbitMqProperties.exchange(),
                    ROUTING_KEY,
                    taskMessage
            );
        } catch (RuntimeException ex) {
            TaskState failed = state.withFailure("RMQ publish failed: " + ex.getMessage(), nowUtc());
            saveState(failed);
            throw new IllegalStateException("비동기 작업 큐 등록에 실패했습니다.", ex);
        }

        return new TaskCreateResponse(taskId, "QUEUED", false, now);
    }

    public TaskStatusResponse getStatus(String taskId) {
        TaskState state = getStateOrThrow(taskId);
        return toStatusResponse(state);
    }

    public TaskStatusResponse getResult(String taskId) {
        TaskState state = getStateOrThrow(taskId);
        return toStatusResponse(state);
    }

    private Optional<TaskCreateResponse> getByIdempotencyKey(String idempotencyKey) {
        Optional<String> existingTaskId = redisCacheService.get(idempotencyRedisKey(idempotencyKey));
        if (existingTaskId.isEmpty()) {
            return Optional.empty();
        }

        Optional<TaskState> state = loadState(existingTaskId.get());
        if (state.isEmpty()) {
            redisCacheService.delete(idempotencyRedisKey(idempotencyKey));
            return Optional.empty();
        }

        TaskState found = state.get();
        return Optional.of(new TaskCreateResponse(found.taskId, found.status, true, found.createdAt));
    }

    private TaskState getStateOrThrow(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("taskId는 비어 있을 수 없습니다.");
        }
        return loadState(taskId.trim())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 taskId 입니다."));
    }

    private Optional<TaskState> loadState(String taskId) {
        Optional<String> raw = redisCacheService.get(taskRedisKey(taskId));
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw.get(), TaskState.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("task 상태 데이터를 파싱할 수 없습니다.", e);
        }
    }

    private void saveState(TaskState state) {
        try {
            String value = objectMapper.writeValueAsString(state);
            redisCacheService.put(taskRedisKey(state.taskId), value, TASK_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("task 상태 데이터를 저장할 수 없습니다.", e);
        }
    }

    private TaskStatusResponse toStatusResponse(TaskState state) {
        return new TaskStatusResponse(
                state.taskId,
                state.type,
                state.status,
                state.payload,
                state.result,
                state.error,
                state.callbackUrl,
                state.timeoutSeconds,
                state.retryCount,
                state.createdAt,
                state.updatedAt
        );
    }

    private String normalizeIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("idempotencyKey 길이는 100자를 초과할 수 없습니다.");
        }
        if (!normalized.matches("^[a-zA-Z0-9:_\\-.]+$")) {
            throw new IllegalArgumentException("idempotencyKey는 영문/숫자/:/_/./- 문자만 사용할 수 있습니다.");
        }
        return normalized;
    }

    private void validateCallbackUrl(String callbackUrl) {
        if (!StringUtils.hasText(callbackUrl)) {
            return;
        }
        try {
            URI uri = new URI(callbackUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("callbackUrl은 http/https URL이어야 합니다.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("callbackUrl 형식이 올바르지 않습니다.");
        }
    }

    private String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private String taskRedisKey(String taskId) {
        return "ai:tasks:task:" + taskId;
    }

    private String idempotencyRedisKey(String key) {
        return "ai:tasks:idempotency:" + key;
    }

    public record InternalTaskMessage(
            String taskId,
            String type,
            JsonNode payload,
            String callbackUrl,
            Integer timeoutSeconds,
            Integer retryCount,
            String requestedAt
    ) {
    }

    public static class TaskState {
        public String taskId;
        public String type;
        public String status;
        public JsonNode payload;
        public JsonNode result;
        public String error;
        public String callbackUrl;
        public Integer timeoutSeconds;
        public Integer retryCount;
        public String createdAt;
        public String updatedAt;

        public TaskState() {
        }

        public TaskState(
                String taskId,
                String type,
                String status,
                JsonNode payload,
                JsonNode result,
                String error,
                String callbackUrl,
                Integer timeoutSeconds,
                Integer retryCount,
                String createdAt,
                String updatedAt
        ) {
            this.taskId = taskId;
            this.type = type;
            this.status = status;
            this.payload = payload;
            this.result = result;
            this.error = error;
            this.callbackUrl = callbackUrl;
            this.timeoutSeconds = timeoutSeconds;
            this.retryCount = retryCount;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public TaskState withFailure(String errorMessage, String updatedAtUtc) {
            return new TaskState(
                    this.taskId,
                    this.type,
                    "FAILED",
                    this.payload,
                    this.result,
                    errorMessage,
                    this.callbackUrl,
                    this.timeoutSeconds,
                    this.retryCount,
                    this.createdAt,
                    updatedAtUtc
            );
        }
    }
}
