package com.withbuddy.account.user.exception;

public class DuplicateEmployeeNumberException extends RuntimeException {

    public DuplicateEmployeeNumberException() {
        super("이미 등록된 사번입니다.");
    }
}
