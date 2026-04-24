package com.withbuddy.infrastructure.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대화 이력 턴")
public record ConversationTurn(
        @Schema(description = "대화 주체", example = "user", allowableValues = {"user", "assistant"})
        String role,
        @Schema(description = "대화 내용", example = "연차는 어떻게 쓰나요?")
        String content
) {
}