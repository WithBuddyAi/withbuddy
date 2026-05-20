package com.withbuddy.admin.organization.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "회사별 부서/팀명 옵션의 부서 항목")
public record OrganizationOptionDepartmentResponse(

        @Schema(description = "부서명", example = "개발팀")
        String department,

        @Schema(description = "해당 부서에 속한 팀명 목록", example = "[\"백엔드팀\", \"프론트엔드팀\"]")
        List<String> teamNames
) {
}
