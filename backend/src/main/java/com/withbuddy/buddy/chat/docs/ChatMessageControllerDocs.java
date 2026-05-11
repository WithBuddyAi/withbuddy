package com.withbuddy.buddy.chat.docs;

import com.withbuddy.buddy.activity.dto.response.LogResponse;
import com.withbuddy.buddy.chat.dto.request.ChatMessageRequest;
import com.withbuddy.buddy.chat.dto.request.QuickQuestionClickRequest;
import com.withbuddy.buddy.chat.dto.response.ChatMessageListResponse;
import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.storage.dto.response.DocumentDownloadResponse;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Chat", description = "With Buddy chat APIs")
public interface ChatMessageControllerDocs {

    @Operation(
            summary = "질문 전송 및 AI 답변 SSE 스트리밍",
            description = """
                    [목적] 사용자의 질문 메시지를 저장한 뒤 AI 답변을 SSE 스트림으로 전달한다.
                    [동작] 공개 API는 `question_saved`, `answer_delta`, `answer_completed`, `error` 이벤트를 사용한다.
                    프론트엔드는 `answer_delta.content`를 이어 붙여 임시 답변을 표시하고,
                    `answer_completed.answer`를 기준으로 최종 BOT 메시지를 확정한다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SSE 스트림 시작 성공",
                    content = @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = {
                                    @ExampleObject(
                                            name = "question_saved",
                                            summary = "질문 저장 완료 이벤트",
                                            value = """
                                                    event: question_saved
                                                    data: {"question":{"id":201,"suggestionId":null,"documents":[],"senderType":"USER","messageType":"user_question","content":"복지카드는 어떻게 요청하나요?","quickTaps":[],"recommendedContacts":[],"createdAt":"2026-05-04T09:30:00"}}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "answer_delta",
                                            summary = "답변 조각 이벤트",
                                            value = """
                                                    event: answer_delta
                                                    data: {"questionId":201,"content":"복지카드는"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "answer_completed",
                                            summary = "최종 답변 완료 이벤트",
                                            value = """
                                                    event: answer_completed
                                                    data: {"questionId":201,"answer":{"id":202,"suggestionId":null,"documents":[],"senderType":"BOT","messageType":"rag_answer","content":"복지카드는 안내 문서를 기준으로 요청하실 수 있습니다.","quickTaps":[],"recommendedContacts":[],"createdAt":"2026-05-04T09:30:03"}}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "error",
                                            summary = "스트림 오류 이벤트",
                                            value = """
                                                    event: error
                                                    data: {"code":"AI_STREAM_FAILED","message":"AI 답변 생성 중 오류가 발생했습니다.","questionId":201}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 유효하지 않은 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    SseEmitter streamMessage(
            @Parameter(hidden = true) Authentication authentication,
            ChatMessageRequest request
    );

    @Operation(
            summary = "대화 이력 조회",
            description = "로그인 사용자의 채팅 메시지 이력을 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "대화 이력 조회 성공",
                    content = @Content(schema = @Schema(implementation = ChatMessageListResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ChatMessageListResponse getMessages(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    @Operation(
            summary = "채팅 세션 시작 로그",
            description = "사용자의 채팅 세션 시작을 기록한다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "세션 시작 최초 기록"),
            @ApiResponse(responseCode = "200", description = "이미 기록된 세션"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<LogResponse> saveSessionStart(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
            summary = "추천 질문 목록 조회",
            description = "채팅 진입 시 노출할 추천 질문 목록을 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "추천 질문 목록 반환"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Map<String, List<QuickQuestionResponse>> getQuickQuestions(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
            summary = "추천 질문 클릭 로그",
            description = "추천 질문 클릭 이벤트를 기록한다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "클릭 로그 기록 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    LogResponse saveQuickQuestionClick(
            @Parameter(hidden = true) Authentication authentication,
            QuickQuestionClickRequest request
    );

    @Operation(
            summary = "채팅 문서 다운로드 URL 조회",
            description = "채팅 답변에 연결된 문서의 다운로드 URL을 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "다운로드 URL 조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentDownloadResponse.class))),
            @ApiResponse(responseCode = "403", description = "접근 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "문서 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    DocumentDownloadResponse getDocumentDownloadUrl(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable Long documentId
    );
}
