package com.withbuddy.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.Map;

@Getter
@Schema(description = "필드 검증 오류")
public class FieldValidationError {

    @Schema(description = "오류 필드", example = "content")
    private final String field;

    @Schema(description = "오류 메시지", example = "질문 내용은 비어 있을 수 없습니다.")
    private final String message;

    @Schema(description = "오류 부가 정보")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, Object> details;

    public FieldValidationError(String field, String message) {
        this(field, message, null);
    }

    public FieldValidationError(String field, String message, Map<String, Object> details) {
        this.field = field;
        this.message = message;
        this.details = details;
    }
}
