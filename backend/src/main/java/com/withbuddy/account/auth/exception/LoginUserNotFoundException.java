package com.withbuddy.account.auth.exception;

public class LoginUserNotFoundException extends LoginFailedException {

    public LoginUserNotFoundException(String message) {
        super(message);
    }
}
