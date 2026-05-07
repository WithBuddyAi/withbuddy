package com.withbuddy.global.jwt;

public class SessionRevokedException extends RuntimeException {

    public SessionRevokedException(String message) {
        super(message);
    }
}
