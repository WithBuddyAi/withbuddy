package com.withbuddy.buddy.activity.service;

import com.withbuddy.buddy.activity.dto.response.LogResponse;
import com.withbuddy.buddy.activity.entity.EventTarget;
import com.withbuddy.buddy.activity.entity.EventType;
import com.withbuddy.buddy.activity.entity.UserActivityLog;
import com.withbuddy.buddy.activity.repository.UserActivityLogRepository;
import com.withbuddy.buddy.chat.service.QuickQuestionCatalog;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserActivityLogService {

    private final UserActivityLogRepository userActivityLogRepository;
    private final QuickQuestionCatalog quickQuestionCatalog;

    @Transactional
    public void saveLoginSessionStart(Long userId) {
        UserActivityLog log = new UserActivityLog(
            userId,
            EventType.SESSION_START,
            EventTarget.LOGIN,
            LocalDateTime.now()
        );

        userActivityLogRepository.save(log);
    }

    @Transactional
    public LogResponse saveChatSessionStart(Long userId) {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

        Optional<UserActivityLog> recentLog =
            userActivityLogRepository
                .findTopByUserIdAndEventTypeAndEventTargetAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    userId,
                    EventType.SESSION_START,
                    EventTarget.CHAT,
                    thirtyMinutesAgo
                );

        if (recentLog.isPresent()) {
            return new LogResponse(
                false,
                EventType.SESSION_START.name(),
                EventTarget.CHAT.name(),
                "30분 이내 동일 사용자 채팅 진입 기록이 이미 존재합니다.",
                null
            );
        }

        UserActivityLog savedLog = userActivityLogRepository.save(
            new UserActivityLog(
                userId,
                EventType.SESSION_START,
                EventTarget.CHAT,
                LocalDateTime.now()
            )
        );

        return new LogResponse(
            true,
            savedLog.getEventType().name(),
            savedLog.getEventTarget().name(),
            null,
            savedLog.getCreatedAt().toString()
        );
    }

    @Transactional
    public LogResponse saveQuickQuestionClick(Long userId, String eventTarget) {
        EventTarget resolvedTarget = quickQuestionCatalog.resolveEventTarget(eventTarget)
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 eventTarget입니다."));

        UserActivityLog log = new UserActivityLog(
            userId,
            EventType.BUTTON_CLICK,
            resolvedTarget,
            LocalDateTime.now()
        );

        UserActivityLog savedLog = userActivityLogRepository.save(log);

        return new LogResponse(
            true,
            savedLog.getEventType().name(),
            savedLog.getEventTarget().name(),
            null,
            savedLog.getCreatedAt().toString()
        );
    }
}
