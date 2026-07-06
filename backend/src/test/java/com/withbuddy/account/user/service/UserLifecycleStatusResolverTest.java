package com.withbuddy.account.user.service;

import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class UserLifecycleStatusResolverTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), KST);

    @Test
    void resolvesPreFromSevenDaysBeforeHireDate() {
        User user = user(LocalDate.of(2026, 6, 24));

        UserAccountStatus status = UserLifecycleStatusResolver.resolve(user, FIXED_CLOCK);

        assertThat(status).isEqualTo(UserAccountStatus.PRE);
    }

    @Test
    void resolvesPreOneDayBeforeHireDate() {
        User user = user(LocalDate.of(2026, 6, 18));

        UserAccountStatus status = UserLifecycleStatusResolver.resolve(user, FIXED_CLOCK);

        assertThat(status).isEqualTo(UserAccountStatus.PRE);
    }

    @Test
    void resolvesInactiveBeforePreOnboardingWindow() {
        User user = user(LocalDate.of(2026, 6, 25));

        UserAccountStatus status = UserLifecycleStatusResolver.resolve(user, FIXED_CLOCK);

        assertThat(status).isEqualTo(UserAccountStatus.INACTIVE);
    }

    @Test
    void resolvesActiveOnHireDate() {
        User user = user(LocalDate.of(2026, 6, 17));

        UserAccountStatus status = UserLifecycleStatusResolver.resolve(user, FIXED_CLOCK);

        assertThat(status).isEqualTo(UserAccountStatus.ACTIVE);
    }

    @Test
    void resolvesReadOnlyAfterProbationPeriod() {
        User user = user(LocalDate.of(2026, 3, 19));

        UserAccountStatus status = UserLifecycleStatusResolver.resolve(user, FIXED_CLOCK);

        assertThat(status).isEqualTo(UserAccountStatus.READ_ONLY);
    }

    @Test
    void resolvesInactiveAfterReadOnlyPeriod() {
        User user = user(LocalDate.of(2026, 2, 18));

        UserAccountStatus status = UserLifecycleStatusResolver.resolve(user, FIXED_CLOCK);

        assertThat(status).isEqualTo(UserAccountStatus.INACTIVE);
    }

    private User user(LocalDate hireDate) {
        return User.builder()
                .company(company())
                .name("user")
                .department("department")
                .teamName("team")
                .employeeNumber("20260001")
                .hireDate(hireDate)
                .role(UserRole.USER)
                .accountStatus(UserAccountStatus.ACTIVE)
                .build();
    }

    private Company company() {
        Company company = mock(Company.class);
        lenient().when(company.getProbationPeriod()).thenReturn(90);
        return company;
    }
}
