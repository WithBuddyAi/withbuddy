package com.withbuddy.buddy.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description =  " 질문 전송 요청")
public class ChatMessageRequest {

    @NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
    @Size(max = 500, message = "질문 내용은 500자 이하여야 합니다.")
    @Schema(description = "질문 내용", example = "복지카드는 어떻게 신청하나요?")
    private String content;
}
