package com.withbuddy.buddy.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class QuickQuestionClickRequest {

    @NotBlank(message = "eventTarget는 필수입니다.")
    private String eventTarget;
}
