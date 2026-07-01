package com.withbuddy.infrastructure.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PromptCacheMetrics {

    private final MeterRegistry meterRegistry;

    public PromptCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordLookup(String namespace, boolean found, String source) {
        meterRegistry.counter(
                "withbuddy.prompt.cache.requests",
                "namespace", normalize(namespace),
                "result", found ? "hit" : "miss",
                "source", normalize(source)
        ).increment();
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? "default" : value;
    }
}
