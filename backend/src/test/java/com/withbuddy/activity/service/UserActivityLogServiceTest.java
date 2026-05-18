package com.withbuddy.activity.service;

import com.withbuddy.admin.activity.dto.response.LogResponse;
import com.withbuddy.admin.activity.entity.UserActivityLog;
import com.withbuddy.admin.activity.repository.UserActivityLogRepository;
import com.withbuddy.admin.activity.service.UserActivityLogService;
import com.withbuddy.buddy.chat.service.QuickQuestionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityLogServiceTest {

    @Mock
    private UserActivityLogRepository userActivityLogRepository;

    @Test
    void savesQuickQuestionClickWithRequestedEventTarget() {
        UserActivityLogService userActivityLogService = new UserActivityLogService(
                userActivityLogRepository,
                new QuickQuestionCatalog()
        );

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
                new QuickQuestionCatalog()
        );

        assertThatThrownBy(() -> userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 eventTarget입니다.");
    }
}
