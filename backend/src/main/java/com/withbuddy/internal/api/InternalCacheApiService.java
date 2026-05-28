package com.withbuddy.internal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.withbuddy.infrastructure.cache.AppCacheProperties;
import com.withbuddy.infrastructure.cache.CacheInvalidationPublisher;
import com.withbuddy.infrastructure.cache.CacheKeyBuilder;
import com.withbuddy.infrastructure.cache.CachePayloadCodec;
import com.withbuddy.infrastructure.cache.CacheTtlPolicy;
import com.withbuddy.infrastructure.cache.InternalApiLocalCacheValue;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final String DEFAULT_NAMESPACE = "default";

    private final StringRedisTemplate redisTemplate;
    private final AppCacheProperties cacheProperties;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final CachePayloadCodec cachePayloadCodec;
    private final CacheTtlPolicy cacheTtlPolicy;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    @Qualifier("internalApiLocalCache")
    private final Cache<String, InternalApiLocalCacheValue> internalApiLocalCache;
    private final Set<String> swrRefreshInFlight = ConcurrentHashMap.newKeySet();

    public CacheGetResponse get(CacheGetRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String resolvedKey = buildRedisKey(namespace, request.key());
        InternalApiLocalCacheValue cached = resolveLocalValue(resolvedKey);
        return new CacheGetResponse(request.key(), namespace, cached.found(), cached.value());
    }

    public CacheGetMultiResponse getMulti(CacheGetMultiRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        List<String> originalKeys = request.keys();
        List<String> resolvedKeys = originalKeys.stream()
                .map(key -> buildRedisKey(namespace, key))
                .toList();

        List<CacheGetResponse> items = new ArrayList<>();
        for (int i = 0; i < originalKeys.size(); i++) {
            String resolvedKey = resolvedKeys.get(i);
            InternalApiLocalCacheValue cached = resolveLocalValue(resolvedKey);
            items.add(new CacheGetResponse(originalKeys.get(i), namespace, cached.found(), cached.value()));
        }
        return new CacheGetMultiResponse(namespace, items);
    }

    public CacheWriteResponse set(CacheSetRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String resolvedKey = buildRedisKey(namespace, request.key());
        String encoded = cachePayloadCodec.encode(request.value());
        redisTemplate.opsForValue().set(resolvedKey, encoded, cacheTtlPolicy.resolve(request.ttlSeconds()));
        if (isL1Enabled()) {
            internalApiLocalCache.put(resolvedKey, InternalApiLocalCacheValue.hit(request.value()));
        }
        cacheInvalidationPublisher.publishKeyInvalidation(resolvedKey);
        return new CacheWriteResponse(namespace, 1);
    }

    public CacheSetMultiResponse setMulti(CacheSetMultiRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        var ttl = cacheTtlPolicy.resolve(request.ttlSeconds());
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
                String encoded = cachePayloadCodec.encode(item.value());
                redisTemplate.opsForValue().set(resolvedKey, encoded, ttl);
                if (isL1Enabled()) {
                    internalApiLocalCache.put(resolvedKey, InternalApiLocalCacheValue.hit(item.value()));
                }
                cacheInvalidationPublisher.publishKeyInvalidation(resolvedKey);
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
        if (isL1Enabled()) {
            internalApiLocalCache.invalidate(resolvedKey);
        }
        cacheInvalidationPublisher.publishKeyInvalidation(resolvedKey);
        return new CacheWriteResponse(namespace, Boolean.TRUE.equals(deleted) ? 1 : 0);
    }

    private InternalApiLocalCacheValue resolveLocalValue(String resolvedKey) {
        if (!isL1Enabled()) {
            return loadFromRedis(resolvedKey);
        }

        long now = System.currentTimeMillis();
        long negativeTtlMillis = Math.max(1, cacheProperties.getL1().getNegativeTtlSeconds()) * 1000L;

        InternalApiLocalCacheValue cached = internalApiLocalCache.getIfPresent(resolvedKey);
        if (cached != null && !cached.isNegativeExpired(now, negativeTtlMillis)) {
            triggerSWRIfNeeded(resolvedKey, cached, now);
            return cached;
        }
        if (cached != null) {
            internalApiLocalCache.invalidate(resolvedKey);
        }
        return internalApiLocalCache.get(resolvedKey, this::loadFromRedis);
    }

    private InternalApiLocalCacheValue loadFromRedis(String resolvedKey) {
        String raw = redisTemplate.opsForValue().get(resolvedKey);
        if (raw == null) {
            return InternalApiLocalCacheValue.miss();
        }
        JsonNode decoded = cachePayloadCodec.decode(raw);
        return InternalApiLocalCacheValue.hit(decoded);
    }

    private void triggerSWRIfNeeded(String resolvedKey, InternalApiLocalCacheValue cached, long now) {
        if (!cached.found() || !cacheProperties.getL1().isSwrEnabled()) {
            return;
        }

        long refreshAfterMillis = Math.max(1, cacheProperties.getL1().getSwrRefreshAfterSeconds()) * 1000L;
        if (now - cached.loadedAtEpochMillis() < refreshAfterMillis) {
            return;
        }

        if (!swrRefreshInFlight.add(resolvedKey)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                InternalApiLocalCacheValue refreshed = loadFromRedis(resolvedKey);
                internalApiLocalCache.put(resolvedKey, refreshed);
            } catch (RuntimeException ignored) {
                // SWR refresh 실패는 요청 흐름에 영향 주지 않는다.
            } finally {
                swrRefreshInFlight.remove(resolvedKey);
            }
        });
    }

    private boolean isL1Enabled() {
        return cacheProperties.getL1().isEnabled();
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
        return cacheKeyBuilder.build(namespace, normalizedKey);
    }
}
