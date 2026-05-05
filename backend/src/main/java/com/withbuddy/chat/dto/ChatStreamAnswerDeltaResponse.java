package com.withbuddy.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "answer_delta SSE 이벤트 데이터")
public class ChatStreamAnswerDeltaResponse {

    @Schema(description = "사용자 질문이 저장된 채팅 메시지 ID", example = "201")
    private Long questionId;

    @Schema(description = "AI 답변 스트리밍 중 전달되는 답변 조각", example = "복지카드는")
    private String content;
}
