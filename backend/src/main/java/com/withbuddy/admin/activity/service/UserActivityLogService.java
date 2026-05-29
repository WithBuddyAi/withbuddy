package com.withbuddy.admin.activity.service;

import com.withbuddy.admin.activity.dto.response.LogResponse;
import com.withbuddy.admin.activity.entity.EventTarget;
import com.withbuddy.admin.activity.entity.EventType;
import com.withbuddy.admin.activity.entity.UserActivityLog;
import com.withbuddy.admin.activity.repository.UserActivityLogRepository;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.buddy.chat.service.QuickQuestionCatalog;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
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
    private final UserRepository userRepository;

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
        requireActiveUser(userId);
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
        requireActiveUser(userId);
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

    private void requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));

        if (user.getRole() != UserRole.ACTIVE && user.getRole() != UserRole.SERVICE_ADMIN) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "현재 역할에서는 이 동작을 수행할 수 없습니다.");
        }
    }
}
