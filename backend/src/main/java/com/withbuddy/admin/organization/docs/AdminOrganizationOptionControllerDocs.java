package com.withbuddy.admin.organization.docs;

import com.withbuddy.admin.organization.dto.response.OrganizationOptionsResponse;
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

@Tag(name = "Admin Organization Options", description = "고객사 관리자 계정 페이지용 회사별 부서/팀명 옵션 API")
public interface AdminOrganizationOptionControllerDocs {

    @Operation(
            summary = "회사별 부서/팀명 조회",
            description = """
                [목적] 현재 로그인한 관리자의 회사 범위에서 신입 계정 생성 및 조회 필터에 사용할 부서/팀명 옵션을 조회한다.
                [동작] JWT의 companyCode를 기준으로 company_organization_units.company_code를 제한하고,
                부서 단위로 팀명 목록을 묶어 반환한다."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회사별 부서/팀명 옵션 조회 성공",
                    content = @Content(schema = @Schema(implementation = OrganizationOptionsResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패, 세션 만료 또는 세션 무효화",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrganizationOptionsResponse> getOrganizationOptions(
            @Parameter(hidden = true) Authentication authentication
    );
}
