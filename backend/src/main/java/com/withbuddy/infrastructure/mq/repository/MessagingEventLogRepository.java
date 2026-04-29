package com.withbuddy.infrastructure.mq.repository;

import com.withbuddy.infrastructure.mq.entity.MessagingEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessagingEventLogRepository extends JpaRepository<MessagingEventLog, Long> {

    boolean existsByEventId(String eventId);
}

