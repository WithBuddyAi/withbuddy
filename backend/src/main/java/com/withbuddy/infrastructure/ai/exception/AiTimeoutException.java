package com.withbuddy.infrastructure.ai.exception;

public class AiTimeoutException extends RuntimeException {
    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}