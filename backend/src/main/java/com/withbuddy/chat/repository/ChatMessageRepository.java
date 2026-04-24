package com.withbuddy.chat.repository;


import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.entity.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<ChatMessage> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<ChatMessage> findTop10ByUserIdAndMessageTypeInOrderByCreatedAtDesc(
            Long userId,
            List<MessageType> messageTypes
    );

    boolean existsByUserIdAndSuggestionIdAndMessageType(
            Long userId,
            Long suggestionId,
            MessageType messageType
    );
}
