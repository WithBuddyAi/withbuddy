package com.withbuddy.buddy.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "error SSE 이벤트 데이터")
public class ChatStreamErrorResponse {

    @Schema(description = "AI 스트리밍 처리 중 발생한 오류 코드", example = "AI_STREAM_FAILED")
    private String code;

    @Schema(description = "사용자에게 표시할 오류 메시지", example = "AI 답변 생성 중 오류가 발생했습니다.")
    private String message;

    @Schema(description = "저장된 사용자 질문 메시지 ID. 질문 저장 전 오류가 발생한 경우 null일 수 있음", example = "201", nullable = true)
    private Long questionId;
}
