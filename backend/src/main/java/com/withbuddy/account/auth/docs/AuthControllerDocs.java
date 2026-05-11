package com.withbuddy.account.auth.docs;

import com.withbuddy.account.auth.dto.request.LoginRequest;
import com.withbuddy.account.auth.dto.response.LoginResponse;
import com.withbuddy.global.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Auth", description = "인증 API — Redis 세션 기반 단일 기기 로그인/로그아웃 관리")
public interface AuthControllerDocs {

    @Operation(
        summary = "로그인",
        description = """
            [목적] 회사코드·사번·이름으로 사용자를 인증하고 accessToken을 발급한다.
            [베네핏] 발급된 accessToken을 Redis에 저장해 단일 기기 세션을 강제한다. \
            다른 기기에서 재로그인 시 이전 세션이 자동 무효화되어 계정 보안을 강화한다. \
            이후 모든 API 호출에 이 토큰을 사용한다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "로그인 성공 — accessToken 반환",
                content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "로그인 실패 — 회사코드·사번·이름 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    LoginResponse login(LoginRequest loginRequest);

    @Operation(
        summary = "로그아웃",
        description = """
            [목적] 현재 로그인 세션을 종료하고 Redis에서 토큰을 즉시 삭제한다.
            [베네핏] 로그아웃 즉시 해당 토큰이 무효화되어 탈취된 토큰으로의 API 접근을 차단한다. \
            공용 PC나 모바일 분실 상황에서 원격으로 세션을 종료할 수 있다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "로그아웃 성공 — 세션 즉시 무효화"),
        @ApiResponse(responseCode = "401", description = "인증 실패 — 이미 만료된 토큰",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> logout(@Parameter(hidden = true) Authentication authentication);
}
