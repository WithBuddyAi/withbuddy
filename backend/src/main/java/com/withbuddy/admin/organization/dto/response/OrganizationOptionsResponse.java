package com.withbuddy.admin.organization.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "회사별 부서/팀명 옵션 조회 응답")
public record OrganizationOptionsResponse(

        @Schema(description = "부서별 팀명 옵션 목록")
        List<OrganizationOptionDepartmentResponse> departments
) {
}
