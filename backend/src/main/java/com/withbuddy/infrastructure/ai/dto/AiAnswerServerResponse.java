package com.withbuddy.infrastructure.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "AI 서버 답변 응답")
public class AiAnswerServerResponse {

    @Schema(description = "질문 ID", example = "201")
    private Long questionId;

    @Schema(description = "메시지 타입", example = "rag_answer")
    private String messageType;

    @Schema(description = "답변 내용", example = "복지카드는 관련 안내 문서를 기준으로 신청할 수 있습니다.")
    private String content;
}
