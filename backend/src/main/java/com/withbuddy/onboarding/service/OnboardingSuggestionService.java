package com.withbuddy.onboarding.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.service.ChatMessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OnboardingSuggestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final OnboardingSuggestionRepository onboardingSuggestionRepository;
    private final ChatMessageService chatMessageService;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    public OnboardingSuggestionListResponse getMyOnboardingSuggestions(Long userId) {
        UserProfile userProfile = getUserProfile(userId);

        int dayOffset = getOrCacheDayOffset(userId, userProfile.hireDate());

        Long suggestionId = getOrCacheQuickTapSuggestionId(dayOffset);

        if (suggestionId == null) {
            return new OnboardingSuggestionListResponse(List.of());
        }

        OnboardingSuggestion suggestion = onboardingSuggestionRepository.findById(suggestionId)
                .orElse(null);

        if (suggestion == null) {
            return new OnboardingSuggestionListResponse(List.of());
        }

        String content = replacePlaceholders(suggestion.getContent(), userProfile.name(), dayOffset);

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

    private UserProfile getUserProfile(Long userId) {
        Optional<UserProfile> cached = getCachedUserProfile(userId);
        if (cached.isPresent()) {
            return cached.get();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return new UserProfile(user.getName(), user.getHireDate());
    }

    private Optional<UserProfile> getCachedUserProfile(Long userId) {
        Optional<String> cached = redisCacheService.get(RedisCacheKeys.userProfile(userId));
        if (cached.isEmpty()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(cached.get());
            JsonNode nameNode = root.get("name");
            JsonNode hireDateNode = root.get("hireDate");

            if (nameNode == null || nameNode.asText().isBlank()
                    || hireDateNode == null || hireDateNode.asText().isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new UserProfile(
                    nameNode.asText(),
                    LocalDate.parse(hireDateNode.asText())
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private int getOrCacheDayOffset(Long userId, LocalDate hireDate) {
        String key = RedisCacheKeys.buddyDay(userId);
        Optional<String> cached = redisCacheService.get(key);
        if (cached.isPresent()) {
            return Integer.parseInt(cached.get());
        }

        LocalDate today = LocalDate.now(KST);
        int dayOffset = (int) ChronoUnit.DAYS.between(hireDate, today);
        redisCacheService.put(key, String.valueOf(dayOffset), RedisCacheTtl.BUDDY_DAY);
        return dayOffset;
    }

    private Long getOrCacheQuickTapSuggestionId(int dayOffset) {
        String key = RedisCacheKeys.quickTap(dayOffset);
        Optional<String> cached = redisCacheService.get(key);
        if (cached.isPresent()) {
            return parseNullableLong(cached.get());
        }

        Long suggestionId = resolveSuggestionId(dayOffset);
        if (suggestionId == null) {
            return null;
        }

        redisCacheService.put(key, String.valueOf(suggestionId), RedisCacheTtl.QUICK_TAP);
        return suggestionId;
    }

    private Long parseNullableLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private Long resolveSuggestionId(int dayOffset) {
        // onboarding_suggestions 시드(day_offset: 1, 4, 8, 31)에 맞춘 단계 매핑
        if (dayOffset >= 31) return 4L;   // 입사 31일차 안내
        if (dayOffset >= 8) return 3L;    // 입사 8일차 안내
        if (dayOffset >= 4) return 2L;    // 입사 4일차 안내
        if (dayOffset >= 1) return 1L;    // 입사 1일차 안내
        return null;
    }

    private String replacePlaceholders(String content, String userName, int dayOffset) {
        return content
                .replace("{이름}", userName)
                .replace("{N}", String.valueOf(Math.abs(dayOffset)));
    }

    private record UserProfile(String name, LocalDate hireDate) {
    }
}
