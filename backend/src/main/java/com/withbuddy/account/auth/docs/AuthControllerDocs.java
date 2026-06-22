package com.withbuddy.account.auth.docs;

import com.withbuddy.account.auth.dto.request.LoginRequest;
import com.withbuddy.account.auth.dto.response.LoginResponse;
import com.withbuddy.account.auth.dto.response.LoginUserResponse;
import com.withbuddy.global.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Auth", description = "인증 API — Redis 세션 기반 단일 기기 로그인/로그아웃 관리")
public interface AuthControllerDocs {

    @Operation(
        summary = "로그인",
        description = """
            [목적] 회사코드·사번·이름으로 사용자를 인증하고 httpOnly 세션 쿠키를 발급한다.
            [추가 보안] Turnstile 보호가 활성화된 환경에서는 turnstileToken도 함께 검증한다.
            [베네핏] 발급된 세션 토큰을 Redis에 저장해 단일 기기 세션을 강제한다. \
            다른 기기에서 재로그인 시 이전 세션이 자동 무효화되어 계정 보안을 강화한다. \
            기존 프론트 호환을 위해 accessToken도 응답 바디에 함께 유지한다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "로그인 성공 — 세션 쿠키 발급 및 accessToken/사용자 정보 반환",
                content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "보안 검증 실패 — Turnstile 토큰 누락 또는 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "로그인 시도 횟수 초과 — 일시 잠금",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "보안 검증 일시 장애 — Turnstile 검증 서비스 연결 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "로그인 실패 — 회사코드·사번·이름 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<LoginResponse> login(LoginRequest loginRequest, @Parameter(hidden = true) HttpServletRequest request);

    @Operation(
        summary = "현재 사용자 조회",
        description = """
            [목적] 현재 세션 쿠키를 기준으로 로그인된 사용자 정보를 조회한다.
            [베네핏] 프론트엔드가 새로고침 이후에도 서버 기준 세션 상태를 복원할 수 있다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "조회 성공 — 현재 로그인 사용자 정보 반환",
                content = @Content(schema = @Schema(implementation = LoginUserResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패 — 세션 쿠키 없음 또는 만료",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<LoginUserResponse> me(@Parameter(hidden = true) Authentication authentication);

    @Operation(
        summary = "로그아웃",
        description = """
            [목적] 현재 로그인 세션을 종료하고 Redis에서 토큰을 즉시 삭제한 뒤 인증 쿠키를 만료시킨다.
            [베네핏] 로그아웃 즉시 해당 토큰이 무효화되어 탈취된 토큰으로의 API 접근을 차단한다. \
            공용 PC나 모바일 분실 상황에서 원격으로 세션을 종료할 수 있다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "로그아웃 성공 — 세션 즉시 무효화"),
        @ApiResponse(responseCode = "401", description = "인증 실패 — 이미 만료된 토큰",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(hidden = true) HttpServletRequest request
    );
}
