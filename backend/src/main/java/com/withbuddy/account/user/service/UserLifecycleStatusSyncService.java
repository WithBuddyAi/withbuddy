package com.withbuddy.account.user.service;

import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.account.user.repository.UserLifecycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserLifecycleStatusSyncService {

    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final UserLifecycleRepository userLifecycleRepository;

    @Transactional
    @Scheduled(cron = "${app.user.lifecycle-status-sync.cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void syncUserLifecycleStatuses() {
        List<User> changedUsers = userLifecycleRepository.findByRole(UserRole.USER).stream()
                .filter(user -> UserLifecycleStatusResolver.sync(user, KST_CLOCK))
                .toList();
        if (!changedUsers.isEmpty()) {
            userLifecycleRepository.saveAll(changedUsers);
        }
    }
}
