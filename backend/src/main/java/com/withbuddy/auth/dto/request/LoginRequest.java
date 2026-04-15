package com.withbuddy.auth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "로그인 요청")
public class LoginRequest {

    @Schema(description = "회사 코드", example = "WB1001")
    @NotBlank(message = "회사 코드를 입력해 주세요.")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "회사코드는 영문, 숫자를 조합하여 4~20자로 입력해 주세요.")
    private String companyCode;

    @Schema(description = "사번", example = "20260001")
    @NotBlank(message = "사원번호를 입력해 주세요.")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "사원번호는 영문, 숫자를 조합하여 4~20자로 입력해 주세요.")
    private String employeeNumber;

    @Schema(description = "이름", example = "김지원")
    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 20, message = "이름은 영문, 숫자를 조합하여 20자 이내로 입력해 주세요.")
    private String name;
}
