package com.withbuddy.admin.activity.repository;

import com.withbuddy.admin.activity.entity.EventTarget;
import com.withbuddy.admin.activity.entity.EventType;
import com.withbuddy.admin.activity.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    Optional<UserActivityLog> findTopByUserIdAndEventTypeAndEventTargetAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId,
            EventType eventType,
            EventTarget eventTarget,
            LocalDateTime createdAt
    );
}
