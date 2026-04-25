package com.withbuddy.chat.controller;

import com.withbuddy.activity.dto.LogResponse;
import com.withbuddy.activity.service.UserActivityLogService;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageListResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.service.ChatMessageQueryService;
import com.withbuddy.chat.service.ChatMessageService;
import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Ai", description = "버디 채팅 API")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatMessageQueryService chatMessageQueryService;
    private final UserActivityLogService userActivityLogService;

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageCreateResponse sendMessage(
            Authentication authentication,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return chatMessageService.saveUserMessage(principal, request);
    }

    @GetMapping("/messages")
    @ResponseStatus(HttpStatus.OK)
    public ChatMessageListResponse getMessages(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return chatMessageQueryService.getMessages(principal.userId(), date);
    }

    @PostMapping("/session-start")
    public ResponseEntity<LogResponse> saveSessionStart(Authentication authentication) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        LogResponse response = userActivityLogService.saveChatSessionStart(principal.userId());

        if (response.isLogged()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/quick-questions")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, List<Map<String, String>>> getQuickQuestions(Authentication authentication) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return chatMessageService.getQuickQuestions(principal.userId());
    }

    @PostMapping("/quick-questions/click")
    @ResponseStatus(HttpStatus.CREATED)
    public LogResponse saveQuickQuestionClick(Authentication authentication) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return userActivityLogService.saveQuickQuestionClick(principal.userId());
    }
}
