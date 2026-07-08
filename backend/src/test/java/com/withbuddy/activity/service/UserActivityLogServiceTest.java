package com.withbuddy.activity.service;

import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityLogServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 7, 10).atStartOfDay(KST).toInstant(),
            KST
    );

    @Mock
    private UserActivityLogRepository userActivityLogRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void savesQuickQuestionClickWithRequestedEventTarget() {
        UserActivityLogService userActivityLogService = new UserActivityLogService(
                userActivityLogRepository,
                new QuickQuestionCatalog(),
                userRepository,
                FIXED_CLOCK
        );

        User activeUser = user(UserAccountStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
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
                userRepository,
                FIXED_CLOCK
        );

        User activeUser = user(UserAccountStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsQuickQuestionClickForReadOnlyUser() {
        UserActivityLogService userActivityLogService = new UserActivityLogService(
                userActivityLogRepository,
                new QuickQuestionCatalog(),
                userRepository,
                FIXED_CLOCK
        );

        User readOnlyUser = user(UserAccountStatus.READ_ONLY);
        when(userRepository.findById(1L)).thenReturn(Optional.of(readOnlyUser));

        assertThatThrownBy(() -> userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_LOCATION"))
                .isInstanceOf(ForbiddenException.class);
        verify(userActivityLogRepository, never()).save(any(UserActivityLog.class));
    }

    @Test
    void allowsPreUserToClickAllPreQuickQuestionTargets() {
        UserActivityLogService userActivityLogService = new UserActivityLogService(
                userActivityLogRepository,
                new QuickQuestionCatalog(),
                userRepository,
                FIXED_CLOCK
        );

        User preUser = user(UserAccountStatus.PRE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(preUser));
        when(userActivityLogRepository.save(any(UserActivityLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_LOCATION").getEventTarget())
                .isEqualTo("QUICK_TAP_LOCATION");
        assertThat(userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_WORK_HOUR").getEventTarget())
                .isEqualTo("QUICK_TAP_WORK_HOUR");
        assertThat(userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_DRESSCODE").getEventTarget())
                .isEqualTo("QUICK_TAP_DRESSCODE");
        assertThat(userActivityLogService.saveQuickQuestionClick(1L, "QUICK_TAP_FIRST_DAY").getEventTarget())
                .isEqualTo("QUICK_TAP_FIRST_DAY");
    }

    private User user(UserAccountStatus accountStatus) {
        Company company = mock(Company.class);
        when(company.getProbationPeriod()).thenReturn(90);

        LocalDate hireDate = switch (accountStatus) {
            case PRE -> LocalDate.of(2026, 7, 14);
            case READ_ONLY -> LocalDate.of(2026, 4, 11);
            case INACTIVE -> LocalDate.of(2026, 3, 1);
            default -> LocalDate.of(2026, 7, 10);
        };

        return User.builder()
                .company(company)
                .name("tester")
                .department("-")
                .teamName("-")
                .employeeNumber("E001")
                .hireDate(hireDate)
                .role(UserRole.USER)
                .accountStatus(accountStatus)
                .build();
    }
}
