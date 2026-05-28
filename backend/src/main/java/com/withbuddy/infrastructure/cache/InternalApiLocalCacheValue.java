package com.withbuddy.infrastructure.cache;

import com.fasterxml.jackson.databind.JsonNode;

public record InternalApiLocalCacheValue(
        boolean found,
        JsonNode value,
        long loadedAtEpochMillis
) {

    public static InternalApiLocalCacheValue hit(JsonNode value) {
        return new InternalApiLocalCacheValue(true, value, System.currentTimeMillis());
    }

    public static InternalApiLocalCacheValue miss() {
        return new InternalApiLocalCacheValue(false, null, System.currentTimeMillis());
    }

    public boolean isNegativeExpired(long nowEpochMillis, long negativeTtlMillis) {
        if (found) {
            return false;
        }
        return nowEpochMillis - loadedAtEpochMillis >= negativeTtlMillis;
    }
}
