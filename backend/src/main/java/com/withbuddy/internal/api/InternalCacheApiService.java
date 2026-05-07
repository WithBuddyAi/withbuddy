package com.withbuddy.internal.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.withbuddy.internal.api.InternalApiModels.CacheDeleteRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetMultiRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetMultiResponse;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetResponse;
import static com.withbuddy.internal.api.InternalApiModels.CacheSetMultiError;
import static com.withbuddy.internal.api.InternalApiModels.CacheSetMultiRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheSetMultiResponse;
import static com.withbuddy.internal.api.InternalApiModels.CacheSetRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheWriteResponse;

@Service
@RequiredArgsConstructor
public class InternalCacheApiService {

    private static final String KEY_PREFIX = "ai";
    private static final String DEFAULT_NAMESPACE = "default";
    private static final int DEFAULT_TTL_SECONDS = 300;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheGetResponse get(CacheGetRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String resolvedKey = buildRedisKey(namespace, request.key());
        String raw = redisTemplate.opsForValue().get(resolvedKey);
        boolean found = raw != null;
        JsonNode value = found ? deserializeJson(raw) : null;
        return new CacheGetResponse(request.key(), namespace, found, value);
    }

    public CacheGetMultiResponse getMulti(CacheGetMultiRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        List<String> originalKeys = request.keys();
        List<String> resolvedKeys = originalKeys.stream()
                .map(key -> buildRedisKey(namespace, key))
                .toList();
        List<String> values = redisTemplate.opsForValue().multiGet(resolvedKeys);

        List<CacheGetResponse> items = new ArrayList<>();
        for (int i = 0; i < originalKeys.size(); i++) {
            String raw = values != null && i < values.size() ? values.get(i) : null;
            boolean found = raw != null;
            JsonNode value = found ? deserializeJson(raw) : null;
            items.add(new CacheGetResponse(originalKeys.get(i), namespace, found, value));
        }
        return new CacheGetMultiResponse(namespace, items);
    }

    public CacheWriteResponse set(CacheSetRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String resolvedKey = buildRedisKey(namespace, request.key());
        String serialized = serializeJson(request.value());
        redisTemplate.opsForValue().set(resolvedKey, serialized, resolveTtl(request.ttlSeconds()));
        return new CacheWriteResponse(namespace, 1);
    }

    public CacheSetMultiResponse setMulti(CacheSetMultiRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        Duration ttl = resolveTtl(request.ttlSeconds());
        List<CacheSetMultiError> errors = new ArrayList<>();
        List<String> seenKeys = new ArrayList<>();
        int written = 0;

        for (InternalApiModels.CacheSetMultiItem item : request.items()) {
            String resolvedKey = buildRedisKey(namespace, item.key());
            if (seenKeys.contains(resolvedKey)) {
                errors.add(new CacheSetMultiError(item.key(), "중복된 key 입니다."));
                continue;
            }
            seenKeys.add(resolvedKey);
            try {
                String serialized = serializeJson(item.value());
                redisTemplate.opsForValue().set(resolvedKey, serialized, ttl);
                written += 1;
            } catch (RuntimeException ex) {
                errors.add(new CacheSetMultiError(item.key(), ex.getMessage()));
            }
        }
        return new CacheSetMultiResponse(errors.isEmpty(), written, errors);
    }

    public CacheWriteResponse delete(CacheDeleteRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String resolvedKey = buildRedisKey(namespace, request.key());
        Boolean deleted = redisTemplate.delete(resolvedKey);
        return new CacheWriteResponse(namespace, Boolean.TRUE.equals(deleted) ? 1 : 0);
    }

    private String normalizeNamespace(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return DEFAULT_NAMESPACE;
        }
        String normalized = namespace.trim();
        if (normalized.length() > 40) {
            throw new IllegalArgumentException("namespace 길이는 40자를 초과할 수 없습니다.");
        }
        if (!normalized.matches("^[a-zA-Z0-9:_-]+$")) {
            throw new IllegalArgumentException("namespace는 영문/숫자/:/_/- 문자만 사용할 수 있습니다.");
        }
        return normalized;
    }

    private String buildRedisKey(String namespace, String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("cache key는 비어 있을 수 없습니다.");
        }
        String normalizedKey = key.trim();
        if (normalizedKey.length() > 200) {
            throw new IllegalArgumentException("cache key 길이는 200자를 초과할 수 없습니다.");
        }
        if (!normalizedKey.matches("^[a-zA-Z0-9:_.-]+$")) {
            throw new IllegalArgumentException("cache key는 영문/숫자/:/_/./- 문자만 사용할 수 있습니다.");
        }
        return KEY_PREFIX + ":" + namespace + ":" + normalizedKey;
    }

    private Duration resolveTtl(Integer ttlSeconds) {
        int value = Objects.requireNonNullElse(ttlSeconds, DEFAULT_TTL_SECONDS);
        return Duration.ofSeconds(value);
    }

    private String serializeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("cache value를 JSON 문자열로 변환할 수 없습니다.", e);
        }
    }

    private JsonNode deserializeJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("cache value를 JSON으로 해석할 수 없습니다.", e);
        }
    }
}
