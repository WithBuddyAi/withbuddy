package com.withbuddy.infrastructure.cache;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
public class CacheResilienceGuard {

    private final AppCacheProperties properties;
    private final MeterRegistry meterRegistry;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntilEpochMillis = new AtomicLong(0);
    private final AtomicInteger circuitOpenGauge = new AtomicInteger(0);

    private final AtomicLong tokens;
    private final AtomicLong lastRefillEpochMillis;

    public CacheResilienceGuard(AppCacheProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.tokens = new AtomicLong(Math.max(1, properties.getResilience().getFallbackBurst()));
        this.lastRefillEpochMillis = new AtomicLong(System.currentTimeMillis());

        Gauge.builder("cache.redis.circuit.open", circuitOpenGauge, AtomicInteger::get).register(meterRegistry);
        Gauge.builder("cache.redis.fallback.tokens", tokens, AtomicLong::get).register(meterRegistry);
    }

    public <T> T execute(String operation, Supplier<T> redisCall, Supplier<T> fallback) {
        if (!properties.getResilience().isEnabled()) {
            return redisCall.get();
        }

        long now = System.currentTimeMillis();
        if (isCircuitOpen(now)) {
            meterRegistry.counter("cache.redis.requests", "op", operation, "result", "circuit_open").increment();
            return fallbackWithRateLimit(operation, "circuit_open", fallback);
        }

        try {
            T result = redisCall.get();
            onRedisSuccess(operation);
            return result;
        } catch (RuntimeException ex) {
            onRedisFailure(operation, now);
            return fallbackWithRateLimit(operation, "redis_error", fallback);
        }
    }

    private <T> T fallbackWithRateLimit(String operation, String reason, Supplier<T> fallback) {
        if (!tryAcquireFallbackToken()) {
            meterRegistry.counter("cache.redis.fallback", "op", operation, "result", "rate_limited").increment();
            throw new CacheFallbackRateLimitException("캐시 fallback 처리율 제한을 초과했습니다.");
        }
        meterRegistry.counter("cache.redis.fallback", "op", operation, "result", reason).increment();
        return fallback.get();
    }

    private void onRedisSuccess(String operation) {
        consecutiveFailures.set(0);
        circuitOpenGauge.set(0);
        meterRegistry.counter("cache.redis.requests", "op", operation, "result", "success").increment();
    }

    private void onRedisFailure(String operation, long nowEpochMillis) {
        int failures = consecutiveFailures.incrementAndGet();
        meterRegistry.counter("cache.redis.requests", "op", operation, "result", "failure").increment();

        int threshold = Math.max(1, properties.getResilience().getCircuitFailureThreshold());
        if (failures >= threshold) {
            long openMillis = Math.max(1, properties.getResilience().getCircuitOpenSeconds()) * 1000L;
            circuitOpenUntilEpochMillis.set(nowEpochMillis + openMillis);
            circuitOpenGauge.set(1);
            consecutiveFailures.set(0);
            meterRegistry.counter("cache.redis.circuit.transitions", "state", "open").increment();
        }
    }

    private boolean isCircuitOpen(long nowEpochMillis) {
        long openUntil = circuitOpenUntilEpochMillis.get();
        if (openUntil <= nowEpochMillis) {
            if (openUntil > 0) {
                circuitOpenUntilEpochMillis.compareAndSet(openUntil, 0);
                circuitOpenGauge.set(0);
                meterRegistry.counter("cache.redis.circuit.transitions", "state", "closed").increment();
            }
            return false;
        }
        return true;
    }

    private boolean tryAcquireFallbackToken() {
        refillTokens();
        while (true) {
            long current = tokens.get();
            if (current <= 0) {
                return false;
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    private void refillTokens() {
        long now = System.currentTimeMillis();
        long previous = lastRefillEpochMillis.get();
        if (now <= previous) {
            return;
        }

        int ratePerSec = Math.max(1, properties.getResilience().getFallbackRatePerSecond());
        int burst = Math.max(1, properties.getResilience().getFallbackBurst());
        long elapsedMillis = now - previous;
        long refill = (elapsedMillis * ratePerSec) / 1000L;
        if (refill <= 0) {
            return;
        }

        if (lastRefillEpochMillis.compareAndSet(previous, now)) {
            tokens.updateAndGet(current -> Math.min(burst, current + refill));
        }
    }
}
