package com.withbuddy.account.auth.exception;

public class TurnstileVerificationFailedException extends RuntimeException {

    public TurnstileVerificationFailedException(String message) {
        super(message);
    }
}
