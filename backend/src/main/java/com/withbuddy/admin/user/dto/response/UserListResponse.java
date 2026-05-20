package com.withbuddy.admin.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "신입 계정 목록 조회 응답")
public record UserListResponse(

        @Schema(description = "신입 계정 목록")
        List<UserListItemResponse> content,

        @Schema(description = "현재 페이지", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "10")
        int size,

        @Schema(description = "전체 데이터 수", example = "1")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "1")
        int totalPages,

        @Schema(description = "첫 페이지 여부", example = "true")
        boolean first,

        @Schema(description = "마지막 페이지 여부", example = "true")
        boolean last
) {
}
