package com.withbuddy.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OnboardingSuggestionItemResponse {
    private String title;
    private String content;
    private Integer dayOffset;
    private LocalDateTime createdAt;
}
