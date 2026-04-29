package com.withbuddy.onboarding.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.service.ChatMessageService;
import com.withbuddy.infrastructure.mq.NudgeEventPublisher;
import com.withbuddy.infrastructure.mq.event.NudgeEvent;
import com.withbuddy.infrastructure.mq.event.NudgeType;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.onboarding.dto.OnboardingSuggestionItemResponse;
import com.withbuddy.onboarding.dto.OnboardingSuggestionListResponse;
import com.withbuddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingSuggestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final OnboardingSuggestionRepository onboardingSuggestionRepository;
    private final ChatMessageService chatMessageService;
    private final RedisCacheService redisCacheService;
    private final NudgeEventPublisher nudgeEventPublisher;

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

        String content = replacePlaceholders(suggestion.getContent(), user.getName(), dayOffset);

        String nudgeSentKey = RedisCacheKeys.nudgeSent(userId, dayOffset);
        if (redisCacheService.putIfAbsent(nudgeSentKey, "1", RedisCacheTtl.NUDGE_SENT)) {
            NudgeEvent nudgeEvent = new NudgeEvent(
                    UUID.randomUUID().toString(),
                    userId,
                    content,
                    null,
                    NudgeType.GENERAL,
                    System.currentTimeMillis()
            );
            try {
                nudgeEventPublisher.publish(nudgeEvent);
            } catch (RuntimeException ex) {
                log.warn("[NUDGE] publish failed. fallback to direct save. userId={}, dayOffset={}", userId, dayOffset, ex);
                chatMessageService.saveSuggestionMessageIfNotExists(
                        userId,
                        suggestion.getId(),
                        content
                );
            }
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
        if (dayOffset == 0) return 3L;
        if (dayOffset >= 1 && dayOffset <= 7) return 4L;
        if (dayOffset >= 8) return 5L;
        return null;
    }

    private String replacePlaceholders(String content, String userName, int dayOffset) {
        int displayDay = dayOffset >= 0 ? dayOffset + 1 : Math.abs(dayOffset);

        return content
                .replace("{이름}", userName)
                .replace("{N}", String.valueOf(displayDay));
    }
}
