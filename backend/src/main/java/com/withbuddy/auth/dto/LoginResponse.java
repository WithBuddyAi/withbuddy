package com.withbuddy.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Schema(description = "로그인 응답")
public class LoginResponse {

    @Schema(description = "사용자 식별자", example = "1")
    private Long id;

    @Schema(description = "회사 코드", example = "1001")
    private String companyCode;

    @Schema(description = "회사명", example = "테크 주식회사")
    private String companyName;

    @Schema(description = "사번", example = "20260001")
    private String employeeNumber;

    @Schema(description = "이름", example = "김지원")
    private String name;

    @Schema(description = "입사일", example = "2026-03-01")
    private LocalDate hireDate;

    public LoginResponse(Long id, String companyCode, String companyName,
                         String employeeNumber, String name, LocalDate hireDate) {
        this.id = id;
        this.companyCode = companyCode;
        this.companyName = companyName;
        this.name = name;
        this.employeeNumber = employeeNumber;
        this.hireDate = hireDate;
    }
}
