package com.withbuddy.global.jwt;

public class SessionNotActiveException extends RuntimeException {

    public SessionNotActiveException(String message) {
        super(message);
    }
}
