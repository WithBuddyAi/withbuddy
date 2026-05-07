package com.withbuddy.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "AI 답변 완료 SSE 이벤트 응답 데이터")
public class ChatStreamAnswerCompletedResponse {

    @Schema(description = "저장된 사용자 질문 메시지 ID", example = "201")
    private Long questionId;

    @Schema(description = "저장된 AI 답변 메시지")
    private ChatMessageResponse answer;
}
