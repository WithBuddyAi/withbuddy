package com.withbuddy.activity.service;

import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.admin.activity.dto.response.LogResponse;
import com.withbuddy.admin.activity.entity.UserActivityLog;
import com.withbuddy.admin.activity.repository.UserActivityLogRepository;
import com.withbuddy.admin.activity.service.UserActivityLogService;
import com.withbuddy.buddy.chat.service.QuickQuestionCatalog;
import com.withbuddy.global.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityLogServiceTest {

    @Mock
    private UserActivityLogRepository userActivityLogRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void savesQuickQuestionClickWithRequestedEventTarget() {
        UserActivityLogService userActivityLogService = new UserActivityLogService(
                userActivityLogRepository,
                new QuickQuestionCatalog(),
                userRepository
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(UserRole.ACTIVE)));
        when(userActivityLogRepository.save(any(UserActivityLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LogResponse response = userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_LOCATION");

        assertThat(response.isLogged()).isTrue();
        assertThat(response.getEventType()).isEqualTo("BUTTON_CLICK");
        assertThat(response.getEventTarget()).isEqualTo("QUICK_TAP_LOCATION");
    }

    @Test
    void rejectsUnsupportedEventTarget() {
        UserActivityLogService userActivityLogService = new UserActivityLogService(
                userActivityLogRepository,
                new QuickQuestionCatalog(),
                userRepository
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(UserRole.ACTIVE)));

        assertThatThrownBy(() -> userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsQuickQuestionClickForReadOnlyUser() {
        UserActivityLogService userActivityLogService = new UserActivityLogService(
                userActivityLogRepository,
                new QuickQuestionCatalog(),
                userRepository
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(UserRole.READ_ONLY)));

        assertThatThrownBy(() -> userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_LOCATION"))
                .isInstanceOf(ForbiddenException.class);
        verify(userActivityLogRepository, never()).save(any(UserActivityLog.class));
    }

    private User user(UserRole role) {
        return User.builder()
                .name("tester")
                .department("-")
                .teamName("-")
                .employeeNumber("E001")
                .hireDate(LocalDate.now())
                .role(role)
                .build();
    }
}
