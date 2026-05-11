package com.withbuddy.buddy.onboarding.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OnboardingSuggestionListResponse {
    private List<OnboardingSuggestionItemResponse> suggestions;
}
