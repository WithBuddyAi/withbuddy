package com.withbuddy.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "로그인 성공 응답")
public class LoginResponse {

    @Schema(description = "액세스 토큰")
    private String accessToken;

    @Schema(description = "로그인 사용자 정보")
    private LoginUserResponse user;
}
