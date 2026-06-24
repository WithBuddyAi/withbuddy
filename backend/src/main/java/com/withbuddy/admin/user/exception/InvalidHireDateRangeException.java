package com.withbuddy.admin.user.exception;

public class InvalidHireDateRangeException extends RuntimeException {

    public InvalidHireDateRangeException() {
        super("입사일은 오늘 기준 ±6개월 이내로 입력해 주세요.");
    }
}
