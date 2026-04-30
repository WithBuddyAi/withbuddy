package com.withbuddy.chat.controller;

import com.withbuddy.activity.dto.LogResponse;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageListResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.dto.QuickQuestionClickRequest;
import com.withbuddy.chat.dto.QuickQuestionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Chat", description = "踰꾨뵒 梨꾪똿 API ???섏뒿?ъ썝??AI 踰꾨뵒? ??뷀븯怨??⑤낫???뺣낫瑜??삳뒗 ?듭떖 梨꾨꼸")
public interface ChatMessageControllerDocs {

    @Operation(
        summary = "硫붿떆吏 ?꾩넚 諛?AI ?듬? ?붿껌",
        description = """
            [紐⑹쟻] ?섏뒿?ъ썝??吏덈Ц????ν븯怨?AI ?쒕쾭(RAG)???듬????붿껌?쒕떎.
            [踰좊꽕?? ?щ궡 臾몄꽌 湲곕컲???뺥솗??AI ?듬???利됱떆 ?쒓났???섏뒿?ъ썝???ъ닔??HR ?놁씠??\
            ?⑤낫??愿???뺣낫瑜??먭린 二쇰룄?곸쑝濡??닿껐?????덈떎. \
            ?듬?怨??④퍡 愿??臾몄꽌 ID媛 諛섑솚?섏뼱 異쒖쿂瑜?異붿쟻?????덈떎."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "硫붿떆吏 ?꾩넚 諛?AI ?듬? ?앹꽦 ?깃났",
                content = @Content(schema = @Schema(implementation = ChatMessageCreateResponse.class))),
        @ApiResponse(responseCode = "401", description = "?몄쬆 ?ㅽ뙣 ???좏슚?섏? ?딆? ?좏겙",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "504", description = "AI ?쒕쾭 ?묐떟 ?쒓컙 珥덇낵",
                content = @Content(
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2026-03-25T10:30:00Z",
                                  "status": 504,
                                  "error": "Gateway Timeout",
                                  "code": "AI_TIMEOUT",
                                  "errors": [{"field": "ai", "message": "AI ?듬? ?앹꽦 ?쒓컙??珥덇낵?섏뿀?듬땲?? ?좎떆 ???ㅼ떆 ?쒕룄??二쇱꽭??"}],
                                  "path": "/api/v1/chat/messages"
                                }""")))
    })
    ChatMessageCreateResponse sendMessage(
            @Parameter(hidden = true) Authentication authentication,
            ChatMessageRequest request
    );

    @Operation(
        summary = "????대젰 議고쉶",
        description = """
            [紐⑹쟻] 濡쒓렇?명븳 ?ъ슜?먯쓽 ?꾩껜 ?먮뒗 ?뱀젙 ?좎쭨??????대젰??議고쉶?쒕떎.
            [踰좊꽕?? ?댁쟾 ????댁슜??蹂듭썝???곗냽?곸씤 ???寃쏀뿕???쒓났?쒕떎. \
            ?좎쭨 ?꾪꽣(date ?뚮씪誘명꽣)瑜??ъ슜?섎㈃ ?뱀젙 ?쇱옄????붾쭔 議고쉶?????덉뼱 \
            ?⑤낫??吏꾪뻾 ?먮쫫???쇱옄蹂꾨줈 ?뚯븙?????덈떎."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "????대젰 議고쉶 ?깃났",
                content = @Content(schema = @Schema(implementation = ChatMessageListResponse.class))),
        @ApiResponse(responseCode = "401", description = "?몄쬆 ?ㅽ뙣 ???좏슚?섏? ?딆? ?좏겙",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ChatMessageListResponse getMessages(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    @Operation(
        summary = "梨꾪똿 ?몄뀡 ?쒖옉 濡쒓퉭",
        description = """
            [紐⑹쟻] ?섏뒿?ъ썝??梨꾪똿???쒖옉???쒖젏??湲곕줉?쒕떎. ?섎（ 1?뚮쭔 湲곕줉?쒕떎.
            [踰좊꽕?? ?섏뒿?ъ썝??梨쀫큸 ?쒖슜 鍮덈룄? ?⑦꽩??遺꾩꽍???⑤낫??李몄뿬?꾨? 痢≪젙?????덈떎. \
            ???곗씠?곕? 湲곕컲?쇰줈 梨쀫큸 誘명솢?⑹옄瑜?議곌린???앸퀎?섍퀬 媛쒖엯?????덈떎."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "?몄뀡 ?쒖옉 理쒖큹 湲곕줉"),
        @ApiResponse(responseCode = "200", description = "?대? ?ㅻ뒛 湲곕줉??(以묐났 濡쒓퉭 諛⑹?)"),
        @ApiResponse(responseCode = "401", description = "?몄쬆 ?ㅽ뙣",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<LogResponse> saveSessionStart(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
        summary = "異붿쿇 吏덈Ц 紐⑸줉 議고쉶",
        description = """
            [紐⑹쟻] ?섏뒿?ъ썝?먭쾶 梨쀫큸?먯꽌 臾쇱뼱蹂????덈뒗 ???吏덈Ц 紐⑸줉???쒓났?쒕떎.
            [踰좊꽕?? ?대뼡 吏덈Ц???댁빞 ?좎? 紐⑤Ⅴ???섏뒿?ъ썝??吏꾩엯 ?λ꼍????텛怨?\
            梨쀫큸 泥??ъ슜???좊룄?쒕떎. 異붿쿇 吏덈Ц ?대┃留뚯쑝濡??좎쓽誘명븳 ?듬????살쓣 ???덉뼱 \
            珥덇린 ?⑤낫???꾨즺?⑥쓣 ?믪씠????湲곗뿬?쒕떎."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "異붿쿇 吏덈Ц 紐⑸줉 諛섑솚"),
        @ApiResponse(responseCode = "401", description = "?몄쬆 ?ㅽ뙣",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Map<String, List<QuickQuestionResponse>> getQuickQuestions(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
        summary = "異붿쿇 吏덈Ц ?대┃ 濡쒓퉭",
        description = """
            [紐⑹쟻] ?섏뒿?ъ썝??異붿쿇 吏덈Ц???대┃???됰룞??湲곕줉?쒕떎.
            [踰좊꽕?? ?대뼡 異붿쿇 吏덈Ц???먯＜ ?좏깮?섎뒗吏 ?곗씠?곕? ?섏쭛??\
            肄섑뀗痢????異붿쿇 吏덈Ц 紐⑸줉??吏?띿쟻?쇰줈 媛쒖꽑?????덈떎. \
            ?대┃ ?곗씠?곕뒗 ?⑤낫??肄섑뀗痢??④낵 痢≪젙??吏?쒕줈 ?쒖슜?쒕떎."""
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "?대┃ 濡쒓렇 湲곕줉 ?깃났"),
        @ApiResponse(responseCode = "401", description = "?몄쬆 ?ㅽ뙣",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    LogResponse saveQuickQuestionClick(
            @Parameter(hidden = true) Authentication authentication,
            QuickQuestionClickRequest request
    );
}
