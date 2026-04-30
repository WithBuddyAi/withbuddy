package com.withbuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QuickQuestionResponse {
    private String buttonText;
    private String content;
    private String eventTarget;
}
