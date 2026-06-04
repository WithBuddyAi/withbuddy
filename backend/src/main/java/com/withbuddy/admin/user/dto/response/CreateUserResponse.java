package com.withbuddy.admin.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Admin user creation response")
public record CreateUserResponse(

        @Schema(description = "User ID", example = "10")
        Long id,

        @Schema(description = "Company code", example = "WB0001")
        String companyCode,

        @Schema(description = "Company name", example = "WithBuddy Inc.")
        String companyName,

        @Schema(description = "User role", example = "USER")
        String role,

        @Schema(description = "User account status", example = "ACTIVE")
        String accountStatus,

        @Schema(description = "User name", example = "Kim Jiwon")
        String name,

        @Schema(description = "Department", example = "Engineering")
        String department,

        @Schema(description = "Team name", example = "Backend")
        String teamName,

        @Schema(description = "Employee number", example = "20260001")
        String employeeNumber,

        @Schema(description = "Hire date", example = "2026-03-01", type = "string", format = "date")
        LocalDate hireDate,

        @Schema(description = "Created at", example = "2026-04-28T09:30:00")
        LocalDateTime createdAt
) {
}
