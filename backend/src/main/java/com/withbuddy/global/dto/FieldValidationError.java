package com.withbuddy.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "필드 검증 오류")
public class FieldValidationError {

    @Schema(description = "오류 필드", example = "content")
    private final String field;

    @Schema(description = "오류 메시지", example = "질문 내용은 비어 있을 수 없습니다.")
    private final String message;
}
