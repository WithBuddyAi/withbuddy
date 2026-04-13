package com.withbuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatMessageListResponse {
    private List<ChatMessageResponse> messages;
}
