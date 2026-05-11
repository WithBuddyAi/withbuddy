package com.withbuddy.account.auth.exception;

public class LoginFailedException extends RuntimeException{

    public LoginFailedException(String message){
        super(message);
    }
}
