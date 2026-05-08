package com.withbuddy.auth.dto.response;

import com.withbuddy.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Schema(description = "로그인 사용자 정보")
public class LoginUserResponse {

    @Schema(description = "사용자 식별자", example = "1")
    private Long id;

    @Schema(description = "회사 코드", example = "WB0001")
    private String companyCode;

    @Schema(description = "사용자 역할", example = "ADMIN", allowableValues = {"USER", "ADMIN", "SERVICE_ADMIN"})
    private UserRole role;

    @Schema(description = "회사명", example = "테크 주식회사")
    private String companyName;

    @Schema(description = "사번", example = "20260001")
    private String employeeNumber;

    @Schema(description = "이름", example = "김지원")
    private String name;

    @Schema(description = "입사일", example = "2026-03-01")
    private LocalDate hireDate;
}
