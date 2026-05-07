package com.withbuddy.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OnboardingSuggestionExposureResponse {
    private boolean created;
    private Long messageId;
    private Long suggestionId;
    private String message;
}
