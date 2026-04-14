package com.withbuddy.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SessionStartLogResponse {
    private boolean logged;
    private String eventType;
    private String eventTarget;
    private String message;
    private String createdAt;
}
