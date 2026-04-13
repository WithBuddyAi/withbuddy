package com.withbuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private Long suggestionId;
    private String senderType;
    private String messageType;
    private String content;
    private String createdAt;
}
