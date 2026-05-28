package com.withbuddy.infrastructure.cache;

import com.fasterxml.jackson.databind.JsonNode;

public record InternalApiLocalCacheValue(
        boolean found,
        JsonNode value,
        long loadedAtEpochMillis,
        long expiresAtEpochMillis
) {

    public static InternalApiLocalCacheValue hit(JsonNode value, long ttlMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = ttlMillis > 0 ? now + ttlMillis : Long.MAX_VALUE;
        return new InternalApiLocalCacheValue(true, value, now, expiresAt);
    }

    public static InternalApiLocalCacheValue miss() {
        return new InternalApiLocalCacheValue(false, null, System.currentTimeMillis(), Long.MAX_VALUE);
    }

    public boolean isNegativeExpired(long nowEpochMillis, long negativeTtlMillis) {
        if (found) {
            return false;
        }
        return nowEpochMillis - loadedAtEpochMillis >= negativeTtlMillis;
    }

    public boolean isPositiveExpired(long nowEpochMillis) {
        if (!found) {
            return false;
        }
        return nowEpochMillis >= expiresAtEpochMillis;
    }
}
