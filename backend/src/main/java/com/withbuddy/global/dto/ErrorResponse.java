package com.withbuddy.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "에러 응답")
public class ErrorResponse {

    @Schema(description = "에러 발생 시각", example = "2026-03-25T10:30:00Z")
    private final String timestamp;

    @Schema(description = "HTTP 상태 코드", example = "401")
    private final int status;

    @Schema(description = "HTTP 에러명", example = "Unauthorized")
    private final String error;

    @Schema(description = "에러 코드", example = "UNAUTHORIZED")
    private final String code;

    @Schema(description = "에러 메시지", example = "입력하신 정보를 다시 확인해 주세요")

    private final List<FieldValidationError> errors;

    @Schema(description = "요청 경로", example = "/api/v1/auth/login")
    private final String path;

    public ErrorResponse(String timestamp, int status, String error, String code, List<FieldValidationError> errors, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.code = code;
        this.errors = errors;
        this.path = path;
    }
}

