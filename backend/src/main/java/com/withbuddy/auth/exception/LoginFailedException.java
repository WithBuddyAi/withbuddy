package com.withbuddy.auth.exception;

public class LoginFailedException extends RuntimeException{

    public LoginFailedException(String message){
        super(message);
    }
}
