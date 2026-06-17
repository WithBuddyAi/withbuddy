package com.withbuddy.account.auth.exception;

public class TurnstileVerificationUnavailableException extends RuntimeException {

    public TurnstileVerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
