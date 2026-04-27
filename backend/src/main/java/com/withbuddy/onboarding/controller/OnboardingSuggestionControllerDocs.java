package com.withbuddy.onboarding.controller;

import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.onboarding.dto.OnboardingSuggestionListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Onboarding", description = "온보딩 제안 API — 수습사원 개인화 온보딩 체크리스트 제공")
public interface OnboardingSuggestionControllerDocs {

    @Operation(
        summary = "내 온보딩 제안 목록 조회",
        description = """
            [목적] 로그인한 수습사원의 개인화된 온보딩 제안 항목을 조회한다.
            [베네핏] 수습사원이 지금 어떤 온보딩 단계를 수행해야 하는지 한눈에 파악할 수 있다. \
            Redis 캐싱을 통해 반복 조회 시 DB 부하 없이 빠르게 응답하며, \
            온보딩 완료율 추적의 기반 데이터로 활용된다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "온보딩 제안 목록 반환",
                content = @Content(schema = @Schema(implementation = OnboardingSuggestionListResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패 — 유효하지 않은 토큰",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OnboardingSuggestionListResponse> getMyOnboardingSuggestions(
            @Parameter(hidden = true) Authentication authentication
    );
}
