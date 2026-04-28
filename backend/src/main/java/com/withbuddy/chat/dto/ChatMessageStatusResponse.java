package com.withbuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMessageStatusResponse {
    private String status;              // PENDING, COMPLETED, TIMEOUT, UNKNOWN
    private ChatMessageResponse answer; // COMPLETED일 때만 값이 있음, 나머지는 null
}
