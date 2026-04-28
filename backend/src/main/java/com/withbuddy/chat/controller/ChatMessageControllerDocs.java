package com.withbuddy.chat.controller;

import com.withbuddy.activity.dto.LogResponse;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageListResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.dto.ChatMessageStatusResponse;
import com.withbuddy.global.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Chat", description = "버디 채팅 API — 수습사원이 AI 버디와 대화하고 온보딩 정보를 얻는 핵심 채널")
public interface ChatMessageControllerDocs {

    @Operation(
        summary = "메시지 전송 및 AI 답변 요청",
        description = """
            [목적] 수습사원의 질문을 저장하고 AI 서버(RAG)에 답변을 요청한다.
            [베네핏] 사내 문서 기반의 정확한 AI 답변을 즉시 제공해 수습사원이 사수나 HR 없이도 \
            온보딩 관련 정보를 자기 주도적으로 해결할 수 있다. \
            답변과 함께 관련 문서 ID가 반환되어 출처를 추적할 수 있다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "메시지 전송 및 AI 답변 생성 성공",
                content = @Content(schema = @Schema(implementation = ChatMessageCreateResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패 — 유효하지 않은 토큰",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "504", description = "AI 서버 응답 시간 초과",
                content = @Content(
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2026-03-25T10:30:00Z",
                                  "status": 504,
                                  "error": "Gateway Timeout",
                                  "code": "AI_TIMEOUT",
                                  "errors": [{"field": "ai", "message": "AI 답변 생성 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."}],
                                  "path": "/api/v1/chat/messages"
                                }""")))
    })
    ChatMessageCreateResponse sendMessage(
            @Parameter(hidden = true) Authentication authentication,
            ChatMessageRequest request
    );

    @Operation(
        summary = "대화 이력 조회",
        description = """
            [목적] 로그인한 사용자의 전체 또는 특정 날짜의 대화 이력을 조회한다.
            [베네핏] 이전 대화 내용을 복원해 연속적인 대화 경험을 제공한다. \
            날짜 필터(date 파라미터)를 사용하면 특정 일자의 대화만 조회할 수 있어 \
            온보딩 진행 흐름을 일자별로 파악할 수 있다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "대화 이력 조회 성공",
                content = @Content(schema = @Schema(implementation = ChatMessageListResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패 — 유효하지 않은 토큰",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ChatMessageListResponse getMessages(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    @Operation(
        summary = "채팅 세션 시작 로깅",
        description = """
            [목적] 수습사원이 채팅을 시작한 시점을 기록한다. 하루 1회만 기록된다.
            [베네핏] 수습사원의 챗봇 활용 빈도와 패턴을 분석해 온보딩 참여도를 측정할 수 있다. \
            이 데이터를 기반으로 챗봇 미활용자를 조기에 식별하고 개입할 수 있다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "세션 시작 최초 기록"),
        @ApiResponse(responseCode = "200", description = "이미 오늘 기록됨 (중복 로깅 방지)"),
        @ApiResponse(responseCode = "401", description = "인증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<LogResponse> saveSessionStart(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
        summary = "추천 질문 목록 조회",
        description = """
            [목적] 수습사원에게 챗봇에서 물어볼 수 있는 대표 질문 목록을 제공한다.
            [베네핏] 어떤 질문을 해야 할지 모르는 수습사원의 진입 장벽을 낮추고 \
            챗봇 첫 사용을 유도한다. 추천 질문 클릭만으로 유의미한 답변을 얻을 수 있어 \
            초기 온보딩 완료율을 높이는 데 기여한다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "추천 질문 목록 반환"),
        @ApiResponse(responseCode = "401", description = "인증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Map<String, List<Map<String, String>>> getQuickQuestions(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
        summary = "추천 질문 클릭 로깅",
        description = """
            [목적] 수습사원이 추천 질문을 클릭한 행동을 기록한다.
            [베네핏] 어떤 추천 질문이 자주 선택되는지 데이터를 수집해 \
            콘텐츠 팀이 추천 질문 목록을 지속적으로 개선할 수 있다. \
            클릭 데이터는 온보딩 콘텐츠 효과 측정의 지표로 활용된다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "클릭 로그 기록 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    LogResponse saveQuickQuestionClick(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
        summary = "AI 답변 처리 상태 조회",
        description = """
            [목적] 10초 초과로 비동기 처리 중인 질문의 상태와 완료된 답변을 조회한다.
            [베네핏] 클라이언트가 PENDING 응답 이후 폴링으로 완료 여부를 확인해 \
            긴 AI 답변 생성 중에도 화면 흐름을 유지할 수 있다."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "상태 조회 성공 (PENDING / COMPLETED / TIMEOUT)",
                content = @Content(schema = @Schema(implementation = ChatMessageStatusResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패 또는 접근 권한 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ChatMessageStatusResponse getMessageStatus(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable Long questionId
    );
}
