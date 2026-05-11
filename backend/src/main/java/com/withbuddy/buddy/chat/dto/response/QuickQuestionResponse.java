package com.withbuddy.buddy.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QuickQuestionResponse {
    private String buttonText;
    private String content;
    private String eventTarget;
}
