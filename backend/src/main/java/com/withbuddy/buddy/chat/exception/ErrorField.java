package com.withbuddy.buddy.chat.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorField {
    private String field;
    private String message;
}
