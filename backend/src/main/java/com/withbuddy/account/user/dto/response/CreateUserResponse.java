package com.withbuddy.account.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "신입 계정 생성 응답")
public record CreateUserResponse(
                @Schema(description = "사용자 ID", example = "10")
        Long id,
        @Schema(description = "회사 코드", example = "WB0001")
        String companyCode,
        @Schema(description = "회사 이름", example = "테크 주식회사")
        String companyName,
        @Schema(description = "사용자 역할", example = "USER")
        String role,
        @Schema(description = "사번", example = "20260001")
        String employeeNumber,
        @Schema(description = "이름", example = "김지원")
        String name,
        @Schema(description = "입사일", example = "2026-03-01", type = "string", format = "date")
        LocalDate hireDate,
        @Schema(description = "생성 시각", example = "2026-04-28T09:30:00")
        LocalDateTime createdAt
) {
}
