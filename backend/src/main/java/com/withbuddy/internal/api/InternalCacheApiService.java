package com.withbuddy.internal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.withbuddy.infrastructure.cache.AppCacheProperties;
import com.withbuddy.infrastructure.cache.CacheInvalidationPublisher;
import com.withbuddy.infrastructure.cache.CacheKeyBuilder;
import com.withbuddy.infrastructure.cache.CachePayloadCodec;
import com.withbuddy.infrastructure.cache.CacheResilienceGuard;
import com.withbuddy.infrastructure.cache.CacheTtlPolicy;
import com.withbuddy.infrastructure.cache.InternalApiLocalCacheValue;
import com.withbuddy.infrastructure.cache.PromptCacheMetrics;
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
import java.util.concurrent.TimeUnit;

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
    private final CacheResilienceGuard cacheResilienceGuard;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final PromptCacheMetrics promptCacheMetrics;

    @Qualifier("internalApiLocalCache")
    private final Cache<String, InternalApiLocalCacheValue> internalApiLocalCache;

    private final Set<String> swrRefreshInFlight = ConcurrentHashMap.newKeySet();

    public CacheGetResponse get(CacheGetRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String resolvedKey = buildRedisKey(namespace, request.key());
        CacheLookupResult lookup = resolveLocalValue(resolvedKey);
        promptCacheMetrics.recordLookup(namespace, lookup.value().found(), lookup.source());
        return new CacheGetResponse(request.key(), namespace, lookup.value().found(), lookup.value().value());
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
            CacheLookupResult lookup = resolveLocalValue(resolvedKey);
            promptCacheMetrics.recordLookup(namespace, lookup.value().found(), lookup.source());
            items.add(new CacheGetResponse(originalKeys.get(i), namespace, lookup.value().found(), lookup.value().value()));
        }
        return new CacheGetMultiResponse(namespace, items);
    }

    public CacheWriteResponse set(CacheSetRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String resolvedKey = buildRedisKey(namespace, request.key());
        var ttl = cacheTtlPolicy.resolve(request.ttlSeconds());

        cacheResilienceGuard.execute(
                "set",
                () -> {
                    String encoded = cachePayloadCodec.encode(request.value());
                    redisTemplate.opsForValue().set(resolvedKey, encoded, ttl);
                    return null;
                },
                () -> null
        );

        if (isL1Enabled()) {
            internalApiLocalCache.put(resolvedKey, InternalApiLocalCacheValue.hit(request.value(), ttl.toMillis()));
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
                cacheResilienceGuard.execute(
                        "set_multi",
                        () -> {
                            String encoded = cachePayloadCodec.encode(item.value());
                            redisTemplate.opsForValue().set(resolvedKey, encoded, ttl);
                            return null;
                        },
                        () -> null
                );

                if (isL1Enabled()) {
                    internalApiLocalCache.put(resolvedKey, InternalApiLocalCacheValue.hit(item.value(), ttl.toMillis()));
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
        Boolean deleted = cacheResilienceGuard.execute(
                "delete",
                () -> redisTemplate.delete(resolvedKey),
                () -> Boolean.FALSE
        );
        if (isL1Enabled()) {
            internalApiLocalCache.invalidate(resolvedKey);
        }
        cacheInvalidationPublisher.publishKeyInvalidation(resolvedKey);
        return new CacheWriteResponse(namespace, Boolean.TRUE.equals(deleted) ? 1 : 0);
    }

    private CacheLookupResult resolveLocalValue(String resolvedKey) {
        if (!isL1Enabled()) {
            return loadFromRedis(resolvedKey);
        }

        long now = System.currentTimeMillis();
        long negativeTtlMillis = Math.max(1, cacheProperties.getL1().getNegativeTtlSeconds()) * 1000L;

        InternalApiLocalCacheValue cached = internalApiLocalCache.getIfPresent(resolvedKey);
        if (cached != null
                && !cached.isNegativeExpired(now, negativeTtlMillis)
                && !cached.isPositiveExpired(now)) {
            triggerSWRIfNeeded(resolvedKey, cached, now);
            if (cached.found()) {
                return new CacheLookupResult(cached, "l1");
            }
            return new CacheLookupResult(cached, "l1_negative");
        }
        if (cached != null) {
            internalApiLocalCache.invalidate(resolvedKey);
        }
        CacheLookupResult loaded = loadFromRedis(resolvedKey);
        internalApiLocalCache.put(resolvedKey, loaded.value());
        return loaded;
    }

    private CacheLookupResult loadFromRedis(String resolvedKey) {
        return cacheResilienceGuard.execute(
                "get",
                () -> {
                    String raw = redisTemplate.opsForValue().get(resolvedKey);
                    if (raw == null) {
                        return new CacheLookupResult(InternalApiLocalCacheValue.miss(), "origin");
                    }
                    JsonNode decoded = cachePayloadCodec.decode(raw);
                    Long ttlMillis = redisTemplate.getExpire(resolvedKey, TimeUnit.MILLISECONDS);
                    long normalizedTtl = ttlMillis == null || ttlMillis < 0 ? -1L : ttlMillis;
                    return new CacheLookupResult(InternalApiLocalCacheValue.hit(decoded, normalizedTtl), "l2");
                },
                () -> new CacheLookupResult(InternalApiLocalCacheValue.miss(), "origin")
        );
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
                InternalApiLocalCacheValue refreshed = loadFromRedis(resolvedKey).value();
                internalApiLocalCache.put(resolvedKey, refreshed);
            } catch (RuntimeException ignored) {
                // SWR refresh 실패는 요청 흐름에 영향 주지 않는다.
            } finally {
                swrRefreshInFlight.remove(resolvedKey);
            }
        });
    }

    private record CacheLookupResult(
            InternalApiLocalCacheValue value,
            String source
    ) {
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

    private boolean isL1Enabled() {
        return cacheProperties.getL1().isEnabled();
    }
}
