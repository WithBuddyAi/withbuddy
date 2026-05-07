package com.withbuddy.onboarding.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.service.ChatMessageService;
import com.withbuddy.onboarding.dto.OnboardingSuggestionExposureResponse;
import com.withbuddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingSuggestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final OnboardingSuggestionRepository onboardingSuggestionRepository;
    private final ChatMessageService chatMessageService;

    public OnboardingSuggestionExposureResponse exposeMyOnboardingSuggestion(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        int dayOffset = calculateDayOffset(user.getHireDate());
        OnboardingSuggestion suggestion = onboardingSuggestionRepository.findTopByDayOffset(
                        resolveSuggestionDayOffset(dayOffset)
                )
                .orElse(null);

        if (suggestion == null) {
            log.info("[ONBOARDING] exposure skipped because suggestion not found. userId={}, dayOffset={}", userId, dayOffset);
            return new OnboardingSuggestionExposureResponse(
                    false,
                    null,
                    null,
                    "오늘 노출할 온보딩 제안이 없습니다."
            );
        }

        String content = replacePlaceholders(
                suggestion.getContent(),
                user.getName(),
                user.getCompany().getName(),
                dayOffset
        );

        ChatMessage existing = chatMessageService.findSuggestionMessage(userId, suggestion.getId());
        if (existing != null) {
            log.info("[ONBOARDING] exposure reused existing suggestion message. userId={}, suggestionId={}, messageId={}",
                    userId,
                    suggestion.getId(),
                    existing.getId()
            );
            return new OnboardingSuggestionExposureResponse(
                    false,
                    existing.getId(),
                    suggestion.getId(),
                    "이미 생성된 온보딩 제안 메시지가 있습니다."
            );
        }

        ChatMessage savedMessage = chatMessageService.saveSuggestionMessageIfNotExists(
                userId,
                suggestion.getId(),
                content
        );

        log.info("[ONBOARDING] exposure created suggestion message. userId={}, suggestionId={}, messageId={}",
                userId,
                suggestion.getId(),
                savedMessage.getId()
        );

        return new OnboardingSuggestionExposureResponse(
                true,
                savedMessage.getId(),
                suggestion.getId(),
                "온보딩 제안 메시지가 생성되었습니다."
        );
    }

    private int calculateDayOffset(LocalDate hireDate) {
        return (int) ChronoUnit.DAYS.between(hireDate, LocalDate.now(KST));
    }

    private int resolveSuggestionDayOffset(int dayOffset) {
        if (dayOffset >= -7 && dayOffset <= -4) {
            return -7;
        }
        if (dayOffset >= -3 && dayOffset <= -1) {
            return -3;
        }
        return dayOffset;
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
                .replace("{회사명}", companyName)
                .replace("{N}", String.valueOf(displayDay));
    }
}
