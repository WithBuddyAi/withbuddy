package com.withbuddy.global.exception;

public class ForbiddenException extends RuntimeException {

    private final String code;
    private final String field;

    public ForbiddenException(String message) {
        this("FORBIDDEN", "auth", message);
    }

    public ForbiddenException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
