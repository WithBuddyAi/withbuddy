package com.withbuddy.admin.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "신입 계정 목록 항목")
public record UserListItemResponse(

        @Schema(description = "사용자 ID", example = "10")
        Long id,

        @Schema(description = "회사 코드", example = "WB0001")
        String companyCode,

        @Schema(description = "회사 이름", example = "테크 주식회사")
        String companyName,

        @Schema(description = "사번", example = "20260001")
        String employeeNumber,

        @Schema(description = "부서", example = "개발팀")
        String department,

        @Schema(description = "팀명", example = "백엔드팀")
        String teamName,

        @Schema(description = "이름", example = "김지원")
        String name,

        @Schema(description = "사용자 역할", example = "USER")
        String role,

        @Schema(description = "입사일", example = "2026-03-01", type = "string", format = "date")
        LocalDate hireDate,

        @Schema(description = "입사일차. 입사 당일은 1", example = "1")
        long hireDay,

        @Schema(description = "생성 시각", example = "2026-04-28T09:30:00")
        LocalDateTime createdAt,

        @Schema(description = "수정 시각", example = "2026-05-19T10:00:00")
        LocalDateTime updatedAt
) {
}
