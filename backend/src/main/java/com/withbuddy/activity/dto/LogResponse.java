package com.withbuddy.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LogResponse {
    private boolean logged;
    private String eventType;
    private String eventTarget;
    private String message;
    private String createdAt;
}
