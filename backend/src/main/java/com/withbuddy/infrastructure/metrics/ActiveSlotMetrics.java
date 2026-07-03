package com.withbuddy.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Component
public class ActiveSlotMetrics {

    private static final String METRIC_NAME = "withbuddy_active_slot";
    private static final String BLUE = "blue";
    private static final String GREEN = "green";

    private final Path activeSlotStateFile;

    public ActiveSlotMetrics(
            @Value("${app.monitoring.active-slot-state-file:/home/ubuntu/withbuddy/active-slot-state.env}")
            String activeSlotStateFile,
            MeterRegistry meterRegistry
    ) {
        this.activeSlotStateFile = Path.of(activeSlotStateFile);

        Gauge.builder(METRIC_NAME, () -> metricValue(BLUE))
                .description("Current active production slot on the router host")
                .tag("slot", BLUE)
                .register(meterRegistry);

        Gauge.builder(METRIC_NAME, () -> metricValue(GREEN))
                .description("Current active production slot on the router host")
                .tag("slot", GREEN)
                .register(meterRegistry);
    }

    private double metricValue(String slot) {
        return slot.equals(readActiveSlot()) ? 1.0d : 0.0d;
    }

    private String readActiveSlot() {
        if (!Files.isRegularFile(activeSlotStateFile)) {
            return "";
        }

        try {
            List<String> lines = Files.readAllLines(activeSlotStateFile);
            for (String line : lines) {
                String normalized = line == null ? "" : line.trim();
                if (normalized.isEmpty() || normalized.startsWith("#")) {
                    continue;
                }
                if (normalized.startsWith("ACTIVE_SLOT=")) {
                    return normalizeSlot(normalized.substring("ACTIVE_SLOT=".length()));
                }
                String directSlot = normalizeSlot(normalized);
                if (!directSlot.isEmpty()) {
                    return directSlot;
                }
            }
        } catch (IOException ignored) {
            return "";
        }

        return "";
    }

    private String normalizeSlot(String candidate) {
        String normalized = candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT);
        if (BLUE.equals(normalized) || GREEN.equals(normalized)) {
            return normalized;
        }
        return "";
    }
}
