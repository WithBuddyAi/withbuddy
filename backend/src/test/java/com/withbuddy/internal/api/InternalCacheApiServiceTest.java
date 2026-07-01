package com.withbuddy.internal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.withbuddy.infrastructure.cache.AppCacheProperties;
import com.withbuddy.infrastructure.cache.CacheInvalidationPublisher;
import com.withbuddy.infrastructure.cache.CacheKeyBuilder;
import com.withbuddy.infrastructure.cache.CachePayloadCodec;
import com.withbuddy.infrastructure.cache.CacheResilienceGuard;
import com.withbuddy.infrastructure.cache.CacheTtlPolicy;
import com.withbuddy.infrastructure.cache.InternalApiLocalCacheValue;
import com.withbuddy.infrastructure.cache.PromptCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalCacheApiServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CacheInvalidationPublisher cacheInvalidationPublisher;

    private SimpleMeterRegistry meterRegistry;
    private InternalCacheApiService cacheApiService;
    private CacheKeyBuilder cacheKeyBuilder;
    private CachePayloadCodec cachePayloadCodec;

    @BeforeEach
    void setUp() {
        AppCacheProperties properties = new AppCacheProperties();
        properties.setEnv("test");
        properties.setKeyPrefix("ai");
        properties.setKeyVersion("v1");
        properties.getL1().setEnabled(true);
        properties.getL1().setNegativeTtlSeconds(30);
        properties.getL2().setJitterRatio(0.0);
        properties.getInvalidation().setEnabled(false);
        properties.getResilience().setEnabled(true);

        meterRegistry = new SimpleMeterRegistry();
        CacheResilienceGuard resilienceGuard = new CacheResilienceGuard(properties, meterRegistry);
        cacheKeyBuilder = new CacheKeyBuilder(properties);
        cachePayloadCodec = new CachePayloadCodec(new ObjectMapper(), properties);
        CacheTtlPolicy cacheTtlPolicy = new CacheTtlPolicy(properties);
        PromptCacheMetrics promptCacheMetrics = new PromptCacheMetrics(meterRegistry);
        Cache<String, InternalApiLocalCacheValue> localCache = Caffeine.newBuilder().build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheApiService = new InternalCacheApiService(
                redisTemplate,
                properties,
                cacheKeyBuilder,
                cachePayloadCodec,
                cacheTtlPolicy,
                resilienceGuard,
                cacheInvalidationPublisher,
                promptCacheMetrics,
                localCache
        );
    }

    @Test
    void recordsL1HitMetric() {
        JsonNode value = new ObjectMapper().createObjectNode().put("answer", "cached");
        String resolvedKey = cacheKeyBuilder.build("prompt", "q1");
        cacheApiService.set(new InternalApiModels.CacheSetRequest("q1", "prompt", value, 60));

        InternalApiModels.CacheGetResponse response = cacheApiService.get(new InternalApiModels.CacheGetRequest("q1", "prompt"));

        assertThat(response.found()).isTrue();
        assertThat(meterRegistry.get("withbuddy.prompt.cache.requests")
                .tag("namespace", "prompt")
                .tag("result", "hit")
                .tag("source", "l1")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(resolvedKey).isNotBlank();
    }

    @Test
    void recordsL2HitMetric() {
        JsonNode value = new ObjectMapper().createObjectNode().put("answer", "redis");
        String encoded = cachePayloadCodec.encode(value);
        String resolvedKey = cacheKeyBuilder.build("prompt", "q2");
        when(valueOperations.get(resolvedKey)).thenReturn(encoded);
        when(redisTemplate.getExpire(resolvedKey, java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(30_000L);

        InternalApiModels.CacheGetResponse response = cacheApiService.get(new InternalApiModels.CacheGetRequest("q2", "prompt"));

        assertThat(response.found()).isTrue();
        assertThat(meterRegistry.get("withbuddy.prompt.cache.requests")
                .tag("namespace", "prompt")
                .tag("result", "hit")
                .tag("source", "l2")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void recordsMissMetric() {
        String resolvedKey = cacheKeyBuilder.build("prompt", "q3");
        when(valueOperations.get(resolvedKey)).thenReturn(null);

        InternalApiModels.CacheGetResponse response = cacheApiService.get(new InternalApiModels.CacheGetRequest("q3", "prompt"));

        assertThat(response.found()).isFalse();
        assertThat(meterRegistry.get("withbuddy.prompt.cache.requests")
                .tag("namespace", "prompt")
                .tag("result", "miss")
                .tag("source", "origin")
                .counter()
                .count()).isEqualTo(1.0d);
    }
}
