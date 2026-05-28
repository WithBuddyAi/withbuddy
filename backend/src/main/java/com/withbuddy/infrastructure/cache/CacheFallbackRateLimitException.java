package com.withbuddy.infrastructure.cache;

public class CacheFallbackRateLimitException extends RuntimeException {

    public CacheFallbackRateLimitException(String message) {
        super(message);
    }
}
