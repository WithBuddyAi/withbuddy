package com.withbuddy.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "AI 서버 답변 요청")
public class AiServerRequest {

    @Schema(description = "질문 ID", example = "201")
    private Long questionId;

    @Schema(description = "회사 코드", example = "WB0001")
    private String companyCode;

    @Schema(description = "질문 내용", example = "복지카드는 어떻게 신청하나요?")
    private String content;
}