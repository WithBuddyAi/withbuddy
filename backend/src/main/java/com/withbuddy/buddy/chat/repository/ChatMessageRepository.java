package com.withbuddy.buddy.chat.repository;


import com.withbuddy.buddy.chat.entity.ChatMessage;
import com.withbuddy.buddy.chat.entity.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    boolean existsByUserIdAndMessageTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId,
            MessageType messageType,
            LocalDateTime start,
            LocalDateTime end
    );

    Optional<ChatMessage> findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
            Long userId,
            Long suggestionId,
            MessageType messageType
    );
}
