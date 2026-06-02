package com.withbuddy.admin.activity.repository;

import com.withbuddy.admin.activity.entity.EventTarget;
import com.withbuddy.admin.activity.entity.EventType;
import com.withbuddy.admin.activity.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    Optional<UserActivityLog> findTopByUserIdAndEventTypeAndEventTargetAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId,
            EventType eventType,
            EventTarget eventTarget,
            LocalDateTime createdAt
    );

    @Query("""
            select l.userId as userId,
                   max(l.createdAt) as lastLoginAt
            from UserActivityLog l
            where l.userId in :userIds
              and l.eventTarget = :eventTarget
            group by l.userId
            """)
    List<LastLoginLogProjection> findLastLoginLogsByUserIdIn(
            @Param("userIds") Collection<Long> userIds,
            @Param("eventTarget") EventTarget eventTarget
    );

    interface LastLoginLogProjection {
        Long getUserId();

        LocalDateTime getLastLoginAt();
    }
}
