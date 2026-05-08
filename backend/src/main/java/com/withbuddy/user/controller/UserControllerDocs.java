package com.withbuddy.user.controller;

import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.user.dto.CreateUserRequest;
import com.withbuddy.user.dto.CreateUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Admin Users", description = "고객사 관리자 계정 페이지용 신입 계정 관리 API")
public interface UserControllerDocs {

    @Operation(
            summary = "신입 계정 생성",
            description = """
                [목적] 현재 로그인한 사용자의 회사 범위에서 신입 사원 계정을 생성한다.
                [동작] JWT의 companyCode를 기준으로 회사를 식별하고, users.company_code에 매핑해 저장한다.
                생성되는 계정의 role은 항상 USER이다.
                동일 회사 내 `employeeNumber`가 이미 존재하면 `409 DUPLICATE_EMPLOYEE_NUMBER`를 반환한다.
                생성된 계정은 회사코드, 사번, 이름으로 로그인할 수 있다."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "신입 계정 생성 성공",
                    content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패, 세션 만료 또는 세션 무효화",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "동일 회사 내 중복 사번",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreateUserResponse> createUser(
            @Parameter(hidden = true) Authentication authentication,
            CreateUserRequest request
    );
}
