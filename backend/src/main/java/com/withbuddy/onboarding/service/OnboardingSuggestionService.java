package com.withbuddy.onboarding.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.dto.QuickQuestionResponse;
import com.withbuddy.chat.service.ChatMessageService;
import com.withbuddy.chat.service.QuickQuestionCatalog;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
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
    private final QuickQuestionCatalog quickQuestionCatalog;
    private final RedisCacheService redisCacheService;

    public OnboardingSuggestionListResponse getMyOnboardingSuggestions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        int dayOffset = calculateDayOffset(user.getHireDate());

        OnboardingSuggestion suggestion = onboardingSuggestionRepository.findTopByDayOffset(dayOffset)
                .orElse(null);

        if (suggestion == null) {
            return new OnboardingSuggestionListResponse(List.of());
        }

        String content = replacePlaceholders(suggestion.getContent(), user.getCompany().getName());
        List<QuickQuestionResponse> quickTaps = quickQuestionCatalog.getOnboardingQuickTaps(dayOffset);

        String nudgeSentKey = RedisCacheKeys.nudgeSent(userId, dayOffset);
        if (redisCacheService.putIfAbsent(nudgeSentKey, "1", RedisCacheTtl.NUDGE_SENT)) {
            chatMessageService.saveSuggestionMessageIfNotExists(
                    userId,
                    suggestion.getId(),
                    content
            );
        }

        OnboardingSuggestionItemResponse response = new OnboardingSuggestionItemResponse(
                suggestion.getTitle(),
                content,
                dayOffset,
                suggestion.getCreatedAt().toString(),
                quickTaps
        );

        return new OnboardingSuggestionListResponse(List.of(response));
    }

    private int calculateDayOffset(LocalDate hireDate) {
        return (int) ChronoUnit.DAYS.between(hireDate, LocalDate.now(KST));
    }

    private String replacePlaceholders(String content, String companyName) {
        return content
                .replace("{회사명}", companyName);
    }
}
