package com.withbuddy.account.auth.dto.response;

import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Schema(description = "Login user information")
public class LoginUserResponse {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Company code", example = "WB0001")
    private String companyCode;

    @Schema(description = "User role", example = "USER", allowableValues = {"USER", "ADMIN", "SERVICE_ADMIN"})
    private UserRole role;

    @Schema(description = "User account status", example = "ACTIVE", allowableValues = {"ACTIVE", "READ_ONLY", "INACTIVE"})
    private UserAccountStatus accountStatus;

    @Schema(description = "Company name", example = "WithBuddy Inc.")
    private String companyName;

    @Schema(description = "Employee number", example = "20260001")
    private String employeeNumber;

    @Schema(description = "User name", example = "Kim Jiwon")
    private String name;

    @Schema(description = "Hire date", example = "2026-03-01")
    private LocalDate hireDate;
}
