package com.withbuddy.admin.user.docs;

import com.withbuddy.admin.user.dto.request.CreateUserRequest;
import com.withbuddy.admin.user.dto.response.CreateUserResponse;
import com.withbuddy.admin.user.dto.response.UserListResponse;
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

@Tag(name = "Admin Users", description = "고객사 관리자 계정 페이지용 신입 계정 관리 API")
public interface AdminUserControllerDocs {

    @Operation(
            summary = "신입 계정 생성",
            description = """
                [목적] 현재 로그인한 사용자의 회사 범위에서 신입 사원 계정을 생성한다.
                [동작] JWT의 companyCode를 기준으로 회사를 식별하고, users.company_code에 매핑해 저장한다.
                생성되는 계정의 role은 항상 USER이고 accountStatus는 ACTIVE이다.
                입사일은 계정 생성일 기준 -6개월~+6개월(양 끝 포함) 범위만 허용한다.
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

    @Operation(
            summary = "신입 계정 조회",
            description = """
                [목적] 현재 로그인한 관리자의 회사 범위에서 신입 사원 계정 목록을 조회한다.
                [동작] JWT의 companyCode를 기준으로 users.company_code를 제한하고, role이 USER이고 accountStatus가 ACTIVE, READ_ONLY, INACTIVE인 계정만 반환한다.
                department와 teamName이 전달되면 각각 부서와 팀명에 대해 부분 검색하며, 둘 다 전달되면 두 조건을 모두 만족하는 계정만 반환한다.
                기본 정렬은 입사일 오름차순이며, 응답에는 질문 누적 횟수와 활성 여부를 포함한다."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "신입 계정 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = UserListResponse.class))),
            @ApiResponse(responseCode = "400", description = "쿼리 파라미터 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패, 세션 만료 또는 세션 무효화",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserListResponse> getUsers(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "조회 페이지. 기본값 0", example = "0") int page,
            @Parameter(description = "페이지 크기. 기본값 10", example = "10") int size,
            @Parameter(description = "부서 검색어", example = "개발팀") String department,
            @Parameter(description = "팀명 검색어", example = "백엔드팀") String teamName,
            @Parameter(description = "정렬 기준. name, employeeNumber, hireDate 중 하나", example = "name") String sortBy,
            @Parameter(description = "정렬 방향. asc 또는 desc", example = "asc") String sortDirection
    );
}
