package com.withbuddy.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessagingMetricsService {

    private static final String PUBLISH_LATENCY_KEY = "metrics:publish:latency";
    private static final String QUEUE_LATENCY_KEY = "metrics:queue:latency";
    private static final String E2E_LATENCY_KEY = "metrics:e2e:latency";
    private static final String PUBLISH_FAILURE_KEY = "metrics:publish:failure";
    private static final String SUCCESS_COUNT_KEY = "metrics:success:count";
    private static final Duration METRIC_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public void recordPublishLatency(long latencyMs) {
        pushLatency(PUBLISH_LATENCY_KEY, latencyMs);
    }

    public void recordQueueLatency(long latencyMs) {
        pushLatency(QUEUE_LATENCY_KEY, latencyMs);
    }

    public void recordEndToEndLatency(long latencyMs) {
        pushLatency(E2E_LATENCY_KEY, latencyMs);
    }

    public void incrementPublishFailure() {
        redisTemplate.opsForValue().increment(PUBLISH_FAILURE_KEY);
        redisTemplate.expire(PUBLISH_FAILURE_KEY, METRIC_TTL);
    }

    public void incrementSuccessCount() {
        redisTemplate.opsForValue().increment(SUCCESS_COUNT_KEY);
        redisTemplate.expire(SUCCESS_COUNT_KEY, METRIC_TTL);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("publishLatencyAvgMs", calcAvg(PUBLISH_LATENCY_KEY));
        metrics.put("queueLatencyAvgMs", calcAvg(QUEUE_LATENCY_KEY));
        metrics.put("e2eLatencyAvgMs", calcAvg(E2E_LATENCY_KEY));
        metrics.put("publishFailureCount", readLong(PUBLISH_FAILURE_KEY));
        metrics.put("successCount", readLong(SUCCESS_COUNT_KEY));
        return metrics;
    }

    private void pushLatency(String key, long latencyMs) {
        redisTemplate.opsForList().leftPush(key, String.valueOf(latencyMs));
        redisTemplate.opsForList().trim(key, 0, 99);
        redisTemplate.expire(key, METRIC_TTL);
    }

    private double calcAvg(String key) {
        List<String> values = redisTemplate.opsForList().range(key, 0, 99);
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream()
                .mapToLong(Long::parseLong)
                .average()
                .orElse(0.0);
    }

    private long readLong(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }
}

