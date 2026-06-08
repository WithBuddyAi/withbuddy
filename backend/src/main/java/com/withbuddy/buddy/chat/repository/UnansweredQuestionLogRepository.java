package com.withbuddy.buddy.chat.repository;

import com.withbuddy.buddy.chat.entity.UnansweredQuestionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnansweredQuestionLogRepository extends JpaRepository<UnansweredQuestionLog, Long> {
}
