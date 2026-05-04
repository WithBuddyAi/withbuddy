package com.withbuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMessageCreateResponse {
    private ChatMessageResponse question;
    private ChatMessageResponse answer;
    private String status;

    public ChatMessageCreateResponse(ChatMessageResponse question, ChatMessageResponse answer) {
        this.question = question;
        this.answer = answer;
        this.status = "COMPLETED";
    }
}
