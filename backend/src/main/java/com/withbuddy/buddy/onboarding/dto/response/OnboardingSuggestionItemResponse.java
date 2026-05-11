package com.withbuddy.buddy.onboarding.dto.response;

import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OnboardingSuggestionItemResponse {
    private String title;
    private String content;
    private Integer dayOffset;
    private String createdAt;
    private List<QuickQuestionResponse> quickTaps;
}
