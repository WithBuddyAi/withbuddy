package com.withbuddy.activity.repository;

import com.withbuddy.activity.entity.EventTarget;
import com.withbuddy.activity.entity.EventType;
import com.withbuddy.activity.entity.UserActivityLog;
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
