package com.withbuddy.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "question_saved SSE 이벤트 데이터")
public class ChatStreamQuestionSavedResponse {

    @Schema(description = "저장된 사용자 질문 메시지")
    private ChatMessageResponse question;
}
