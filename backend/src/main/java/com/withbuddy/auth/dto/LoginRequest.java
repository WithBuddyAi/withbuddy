package com.withbuddy.auth.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "로그인 요청")
public class LoginRequest {

    @Schema(description = "회사 코드", example = "1001")
    @NotBlank(message = "회사 코드는 필수입니다.")
    @Size(max = 20, message = "회사 코드는 최대 20자까지 입력할 수 있습니다.")
    private String companyCode;

    @Schema(description = "사번", example = "20260001")
    @NotBlank(message = "사번은 필수입니다.")
    @Size(max = 20, message = "사번은 최대 50자까지 입력할 수 있습니다.")
    private String employeeNumber;

    @Schema(description = "이름", example = "김지원")
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 20, message = "이름은 최대 100자까지 입력할 수 있습니다.")
    private String name;
}
