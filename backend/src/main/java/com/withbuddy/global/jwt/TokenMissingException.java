package com.withbuddy.global.jwt;

public class TokenMissingException extends RuntimeException {

    public TokenMissingException(String message) {
        super(message);
    }
}