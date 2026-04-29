package com.withbuddy.infrastructure.mq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messaging_event_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessagingEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 120, unique = true)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private MessagingEventType eventType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MessagingEventLog(String eventId, MessagingEventType eventType, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.createdAt = createdAt;
    }
}

