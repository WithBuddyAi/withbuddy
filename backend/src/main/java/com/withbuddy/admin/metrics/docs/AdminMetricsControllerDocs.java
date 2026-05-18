package com.withbuddy.admin.metrics.docs;

import com.withbuddy.admin.metrics.dto.response.FirstInteractionRateResponse;
import com.withbuddy.admin.metrics.dto.response.RagExperienceRateResponse;
import com.withbuddy.admin.metrics.dto.response.RevisitRateResponse;
import com.withbuddy.admin.metrics.dto.response.TtaResponse;
import com.withbuddy.admin.metrics.dto.response.UnansweredRateResponse;
import com.withbuddy.global.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Tag(name = "Admin Metrics", description = "서비스 관리자용 회사별 핵심 지표 조회 API")
public interface AdminMetricsControllerDocs {

    @Operation(summary = "D+6 RAG 답변 수신 경험률 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "지표 조회 성공",
                    content = @Content(schema = @Schema(implementation = RagExperienceRateResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RagExperienceRateResponse> getRagExperienceRate(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    );

    @Operation(summary = "D+0 첫 인터랙션 발생률 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "지표 조회 성공",
                    content = @Content(schema = @Schema(implementation = FirstInteractionRateResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<FirstInteractionRateResponse> getFirstInteractionRate(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    );

    @Operation(summary = "D+6 재방문률 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "지표 조회 성공",
                    content = @Content(schema = @Schema(implementation = RevisitRateResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RevisitRateResponse> getRevisitRate(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    );

    @Operation(summary = "미답변 비율 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "지표 조회 성공",
                    content = @Content(schema = @Schema(implementation = UnansweredRateResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UnansweredRateResponse> getUnansweredRate(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    );

    @Operation(summary = "평균 TTA 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "지표 조회 성공",
                    content = @Content(schema = @Schema(implementation = TtaResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<TtaResponse> getTta(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    );
}
