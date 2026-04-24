package com.withbuddy.infrastructure.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "AI 서버 답변 요청")
public class AiAnswerServerRequest {

    @Schema(description = "질문 ID", example = "201")
    private Long questionId;

    @Schema(description = "답변 생성에 사용할 사용자 정보")
    private AiUserContext user;

    @Schema(description = "질문 내용", example = "복지카드는 어떻게 신청하나요?")
    private String content;

    @Schema(description = "이전 대화 이력 (없으면 빈 배열)")
    private List<ConversationTurn> conversationHistory;
}
