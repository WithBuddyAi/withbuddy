package com.withbuddy.storage.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class StorageException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;
    private final Map<String, Object> details;

    public StorageException(HttpStatus status, String code, String field, String message) {
        this(status, code, field, message, null);
    }

    public StorageException(HttpStatus status, String code, String field, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.details = details;
    }
}

