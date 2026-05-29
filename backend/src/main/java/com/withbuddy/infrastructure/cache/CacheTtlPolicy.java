package com.withbuddy.infrastructure.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class CacheTtlPolicy {

    private final AppCacheProperties properties;

    public CacheTtlPolicy(AppCacheProperties properties) {
        this.properties = properties;
    }

    public Duration resolve(Integer requestedTtlSeconds) {
        AppCacheProperties.L2 l2 = properties.getL2();
        int fallback = l2.getDefaultTtlSeconds();
        int raw = requestedTtlSeconds == null ? fallback : requestedTtlSeconds;
        int bounded = Math.max(l2.getMinTtlSeconds(), Math.min(raw, l2.getMaxTtlSeconds()));

        double jitterRatio = Math.max(0.0, Math.min(l2.getJitterRatio(), 1.0));
        if (jitterRatio == 0.0) {
            return Duration.ofSeconds(bounded);
        }

        double offset = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * jitterRatio;
        long jittered = Math.max(l2.getMinTtlSeconds(), Math.round(bounded * (1.0 + offset)));
        return Duration.ofSeconds(jittered);
    }
}
