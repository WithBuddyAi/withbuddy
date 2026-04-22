package com.withbuddy.onboarding.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.service.ChatMessageService;
import com.withbuddy.onboarding.dto.OnboardingSuggestionItemResponse;
import com.withbuddy.onboarding.dto.OnboardingSuggestionListResponse;
import com.withbuddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OnboardingSuggestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final OnboardingSuggestionRepository onboardingSuggestionRepository;
    private final ChatMessageService chatMessageService;

    public OnboardingSuggestionListResponse getMyOnboardingSuggestions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        LocalDate hireDate = user.getHireDate();
        LocalDate today = LocalDate.now(KST);
        int dayOffset = (int) ChronoUnit.DAYS.between(hireDate, today);

        Long suggestionId = resolveSuggestionId(dayOffset);

        if (suggestionId == null) {
            return new OnboardingSuggestionListResponse(List.of());
        }

        OnboardingSuggestion suggestion = onboardingSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("온보딩 제안을 찾을 수 없습니다."));

        String content = replacePlaceholders(suggestion.getContent(), user.getName(), dayOffset);

        chatMessageService.saveSuggestionMessageIfNotExists(
                userId,
                suggestion.getId(),
                content
        );

        OnboardingSuggestionItemResponse response = new OnboardingSuggestionItemResponse(
                suggestion.getTitle(),
                content,
                dayOffset,
                suggestion.getCreatedAt()
        );

        return new OnboardingSuggestionListResponse(List.of(response));
    }

    private Long resolveSuggestionId(int dayOffset) {
        if (dayOffset >= -7 && dayOffset <= -4) return 1L;
        if (dayOffset >= -3 && dayOffset <= -1) return 2L;
        if (dayOffset == 0) return 3L;
        if (dayOffset >= 1 && dayOffset <= 7) return 4L;
        if (dayOffset >= 8) return 5L;
        return null;
    }

    private String replacePlaceholders(String content, String userName, int dayOffset) {
        String nValue;

        if (dayOffset < 0) {
            nValue = String.valueOf(Math.abs(dayOffset));
        } else {
            nValue = String.valueOf(dayOffset + 1);
        }

        return content
                .replace("{이름}", userName)
                .replace("{N}", nValue);
    }
}