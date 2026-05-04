package com.withbuddy.infrastructure.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "사용자 정보")
public class AiUserContext {

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "사용자 이름", example = "김지원")
    private String name;

    @Schema(description = "회사 코드", example = "WB0001")
    private String companyCode;

    @Schema(description = "회사명", example = "테크 주식회사")
    private String companyName;

    @Schema(description = "입사일", example = "2026-04-01")
    private String hireDate;

    @Schema(description = "오늘 날짜 (서버 기준)", example = "2026-05-05")
    private String today;
}
