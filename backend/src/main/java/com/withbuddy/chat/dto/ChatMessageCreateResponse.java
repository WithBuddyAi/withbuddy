package com.withbuddy.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "질문 저장 및 AI 응답 상태")
public class ChatMessageCreateResponse {
    @Schema(description = "저장된 사용자 질문")
    private ChatMessageResponse question;

    @Schema(description = "동기 응답이 완료된 경우 AI 답변. 비동기 폴백(PENDING) 시 null")
    private ChatMessageResponse answer;

    @Schema(description = "응답 처리 상태", example = "COMPLETED", allowableValues = {"COMPLETED", "PENDING"})
    private String status;

    public ChatMessageCreateResponse(ChatMessageResponse question, ChatMessageResponse answer) {
        this.question = question;
        this.answer = answer;
        this.status = "COMPLETED";
    }
}
