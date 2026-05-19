package com.withbuddy.account.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Schema(description = "신입 계정 생성 요청")
public class CreateUserRequest {

    @Schema(description = "신입 사원 이름", example = "김지원")
    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 1, max = 20, message = "이름은 1~20자여야 합니다.")
    @Pattern(regexp = "^[가-힣A-Za-z]{1,20}$", message = "이름은 한글 또는 영문만 입력할 수 있습니다.")
    private String name;

    @Schema(description = "신입 사원 사번", example = "20260001")
    @NotBlank(message = "사번은 필수입니다.")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "사번은 영문과 숫자로 4~20자여야 합니다.")
    private String employeeNumber;

    @Schema(description = "입사일", example = "2026-03-01", type = "string", format = "date")
    @NotNull(message = "입사일은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate hireDate;
}
