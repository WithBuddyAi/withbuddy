package com.withbuddy.account.user.service;

import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;

import java.time.Clock;
import java.time.LocalDate;

public final class UserLifecycleStatusResolver {

    private UserLifecycleStatusResolver() {
    }

    public static UserAccountStatus resolve(User user, Clock clock) {
        if (user.getRole() != UserRole.USER) {
            return null;
        }

        int probationPeriod = user.getCompany().getProbationPeriod() == null
                ? 90
                : user.getCompany().getProbationPeriod();
        LocalDate today = LocalDate.now(clock);
        LocalDate readOnlyStartDate = user.getHireDate().plusDays(probationPeriod);
        LocalDate inactiveStartDate = readOnlyStartDate.plusDays(30);

        if (!today.isBefore(inactiveStartDate)) {
            return UserAccountStatus.INACTIVE;
        }
        if (!today.isBefore(readOnlyStartDate)) {
            return UserAccountStatus.READ_ONLY;
        }
        return UserAccountStatus.ACTIVE;
    }

    public static boolean sync(User user, Clock clock) {
        UserAccountStatus resolved = resolve(user, clock);
        if (user.getRole() == UserRole.USER && user.getAccountStatus() != resolved) {
            user.updateAccountStatus(resolved);
            return true;
        }
        return false;
    }
}
