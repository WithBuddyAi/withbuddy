package com.withbuddy.admin.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Admin user list item")
public record UserListItemResponse(

        @Schema(description = "User ID", example = "10")
        Long id,

        @Schema(description = "Company code", example = "WB0001")
        String companyCode,

        @Schema(description = "Company name", example = "WithBuddy Inc.")
        String companyName,

        @Schema(description = "Employee number", example = "20260001")
        String employeeNumber,

        @JsonProperty("부서(팀)")
        @Schema(name = "부서(팀)", description = "Department and team", example = "Engineering(Backend)")
        String departmentTeam,

        @Schema(description = "User name", example = "Kim Jiwon")
        String name,

        @Schema(description = "User role", example = "USER", allowableValues = {"USER"})
        String role,

        @Schema(description = "User account status", example = "ACTIVE", allowableValues = {"ACTIVE", "READ_ONLY", "INACTIVE"})
        String accountStatus,

        @Schema(description = "Hire date", example = "2026-03-01", type = "string", format = "date")
        LocalDate hireDate,

        @Schema(description = "Hire day. Hire date is day 1.", example = "1")
        long hireDay,

        @Schema(description = "Total user question count", example = "7")
        long questionCount,

        @Schema(description = "Last login date", example = "2026-05-19", type = "string", format = "date")
        LocalDate lastLoginDate,

        @Schema(description = "Created at", example = "2026-04-28T09:30:00")
        LocalDateTime createdAt,

        @Schema(description = "Updated at", example = "2026-05-19T10:00:00")
        LocalDateTime updatedAt
) {
}
