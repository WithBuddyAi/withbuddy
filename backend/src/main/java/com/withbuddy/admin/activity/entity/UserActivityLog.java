package com.withbuddy.admin.activity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_activity_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_target", length = 100)
    private EventTarget eventTarget;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UserActivityLog(Long userId, EventType eventType, EventTarget eventTarget, LocalDateTime createdAt) {
        this.userId = userId;
        this.eventType = eventType;
        this.eventTarget = eventTarget;
        this.createdAt = createdAt;
    }
}
