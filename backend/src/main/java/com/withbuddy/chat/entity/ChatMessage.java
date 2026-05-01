package com.withbuddy.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "suggestion_id")
    private Long suggestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private MessageType messageType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "recommended_contacts_json", columnDefinition = "TEXT")
    private String recommendedContactsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatMessage(
            Long userId,
            Long suggestionId,
            SenderType senderType,
            MessageType messageType,
            String content,
            String recommendedContactsJson
    ) {
        this.userId = userId;
        this.suggestionId = suggestionId;
        this.senderType = senderType;
        this.messageType = messageType;
        this.content = content;
        this.recommendedContactsJson = recommendedContactsJson;
    }

    public static ChatMessage createSuggestionMessage(Long userId, Long suggestionId, String content) {
        ChatMessage message = new ChatMessage();
        message.userId = userId;
        message.suggestionId = suggestionId;
        message.senderType = SenderType.BOT;
        message.messageType = MessageType.suggestion;
        message.content = content;
        message.recommendedContactsJson = null;
        return message;
    }
}
