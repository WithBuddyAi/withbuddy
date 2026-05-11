package com.withbuddy.buddy.activity.repository;

import com.withbuddy.buddy.activity.entity.EventTarget;
import com.withbuddy.buddy.activity.entity.EventType;
import com.withbuddy.buddy.activity.entity.UserActivityLog;
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
