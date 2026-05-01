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

import static com.withbuddy.internal.api.InternalApiModels.JobCreateRequest;
import static com.withbuddy.internal.api.InternalApiModels.JobCreateResponse;
import static com.withbuddy.internal.api.InternalApiModels.JobStatusResponse;

@Service
@RequiredArgsConstructor
public class InternalJobApiService {

    private static final Duration JOB_TTL = Duration.ofHours(24);
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final int DEFAULT_RETRY_COUNT = 0;
    private static final String ROUTING_KEY = "internal.jobs.requested";

    private final RabbitTemplate rabbitTemplate;
    private final AppRabbitMqProperties rabbitMqProperties;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    public JobCreateResponse create(JobCreateRequest request) {
        validateCallbackUrl(request.callbackUrl());
        String normalizedType = request.type().trim();
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<JobCreateResponse> duplicated = getByIdempotencyKey(idempotencyKey);
            if (duplicated.isPresent()) {
                return duplicated.get();
            }
        }

        String now = nowUtc();
        String jobId = UUID.randomUUID().toString();
        int timeoutSeconds = request.timeoutSeconds() != null ? request.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        int retryCount = request.retryCount() != null ? request.retryCount() : DEFAULT_RETRY_COUNT;

        JobState state = new JobState(
                jobId,
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
            redisCacheService.put(idempotencyRedisKey(idempotencyKey), jobId, JOB_TTL);
        }

        InternalJobMessage jobMessage = new InternalJobMessage(
                jobId,
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
                    jobMessage
            );
        } catch (RuntimeException ex) {
            JobState failed = state.withFailure("RMQ publish failed: " + ex.getMessage(), nowUtc());
            saveState(failed);
            throw new IllegalStateException("비동기 작업 큐 등록에 실패했습니다.", ex);
        }

        return new JobCreateResponse(jobId, "QUEUED", false, now);
    }

    public JobStatusResponse getStatus(String jobId) {
        JobState state = getStateOrThrow(jobId);
        return toStatusResponse(state);
    }

    public JobStatusResponse getResult(String jobId) {
        JobState state = getStateOrThrow(jobId);
        return toStatusResponse(state);
    }

    private Optional<JobCreateResponse> getByIdempotencyKey(String idempotencyKey) {
        Optional<String> existingJobId = redisCacheService.get(idempotencyRedisKey(idempotencyKey));
        if (existingJobId.isEmpty()) {
            return Optional.empty();
        }

        Optional<JobState> state = loadState(existingJobId.get());
        if (state.isEmpty()) {
            redisCacheService.delete(idempotencyRedisKey(idempotencyKey));
            return Optional.empty();
        }

        JobState found = state.get();
        return Optional.of(new JobCreateResponse(found.jobId, found.status, true, found.createdAt));
    }

    private JobState getStateOrThrow(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            throw new IllegalArgumentException("jobId는 비어 있을 수 없습니다.");
        }
        return loadState(jobId.trim())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 jobId 입니다."));
    }

    private Optional<JobState> loadState(String jobId) {
        Optional<String> raw = redisCacheService.get(jobRedisKey(jobId));
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw.get(), JobState.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("job 상태 데이터를 파싱할 수 없습니다.", e);
        }
    }

    private void saveState(JobState state) {
        try {
            String value = objectMapper.writeValueAsString(state);
            redisCacheService.put(jobRedisKey(state.jobId), value, JOB_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("job 상태 데이터를 저장할 수 없습니다.", e);
        }
    }

    private JobStatusResponse toStatusResponse(JobState state) {
        return new JobStatusResponse(
                state.jobId,
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

    private String jobRedisKey(String jobId) {
        return "ai:jobs:job:" + jobId;
    }

    private String idempotencyRedisKey(String key) {
        return "ai:jobs:idempotency:" + key;
    }

    public record InternalJobMessage(
            String jobId,
            String type,
            JsonNode payload,
            String callbackUrl,
            Integer timeoutSeconds,
            Integer retryCount,
            String requestedAt
    ) {
    }

    public static class JobState {
        public String jobId;
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

        public JobState() {
        }

        public JobState(
                String jobId,
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
            this.jobId = jobId;
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

        public JobState withFailure(String errorMessage, String updatedAtUtc) {
            return new JobState(
                    this.jobId,
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
