package com.withbuddy.account.auth.dto.response;

import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
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

    @Schema(description = "회사명", example = "테크 주식회사")
    private String companyName;

    @Schema(description = "사번", example = "20260001")
    private String employeeNumber;

    @Schema(description = "이름", example = "김지원")
    private String name;

    @Schema(description = "부서", example = "개발팀")
    private String department;

    @Schema(description = "팀명", example = "백엔드팀")
    private String teamName;

    @Schema(description = "사용자", example = "USER", allowableValues = {"USER", "ADMIN", "SERVICE_ADMIN"})
    private UserRole role;

    @Schema(description = "계정 상태. USER는 이용 상태, ADMIN은 관리자 기능 사용 가능 여부 판단에 사용한다.", example = "ACTIVE", allowableValues = {"ACTIVE", "READ_ONLY", "INACTIVE"})
    private UserAccountStatus accountStatus;

    @Schema(description = "입사일", example = "2026-03-01")
    private LocalDate hireDate;
}
