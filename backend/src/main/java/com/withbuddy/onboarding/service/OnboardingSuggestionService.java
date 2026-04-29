package com.withbuddy.onboarding.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.service.ChatMessageService;
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
    private final RedisCacheService redisCacheService;

    public OnboardingSuggestionListResponse getMyOnboardingSuggestions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        int dayOffset = calculateDayOffset(user.getHireDate());

        Long suggestionId = resolveSuggestionId(dayOffset);

        if (suggestionId == null) {
            return new OnboardingSuggestionListResponse(List.of());
        }

        OnboardingSuggestion suggestion = onboardingSuggestionRepository.findById(suggestionId)
                .orElse(null);

        if (suggestion == null) {
            return new OnboardingSuggestionListResponse(List.of());
        }

        String content = replacePlaceholders(suggestion.getContent(), user.getName(), user.getCompany().getName(), dayOffset);

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
                suggestion.getCreatedAt()
        );

        return new OnboardingSuggestionListResponse(List.of(response));
    }

    private int calculateDayOffset(LocalDate hireDate) {
        return (int) ChronoUnit.DAYS.between(hireDate, LocalDate.now(KST));
    }

    private Long resolveSuggestionId(int dayOffset) {
        if (dayOffset >= -7 && dayOffset <= -4) return 1L;
        if (dayOffset >= -3 && dayOffset <= -1) return 2L;

        return switch (dayOffset) {
            case 0 -> 3L;
            case 1 -> 4L;
            case 3 -> 5L;
            case 5 -> 6L;
            case 7 -> 7L;
            case 10 -> 8L;
            case 14 -> 9L;
            case 21 -> 10L;
            case 30 -> 11L;
            default -> null;
        };
    }

    private String replacePlaceholders(
            String content,
            String userName,
            String companyName,
            int dayOffset
    ) {
        int displayDay = dayOffset >= 0 ? dayOffset + 1 : Math.abs(dayOffset);

        return content
                .replace("{이름}", userName)
                .replace("{N}", String.valueOf(displayDay))
                .replace("{회사명}", companyName);
    }
}
