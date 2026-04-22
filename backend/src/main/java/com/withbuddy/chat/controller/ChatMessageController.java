package com.withbuddy.chat.controller;

import com.withbuddy.activity.dto.SessionStartLogResponse;
import com.withbuddy.activity.service.UserActivityLogService;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageListResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.service.ChatMessageQueryService;
import com.withbuddy.chat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;


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
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return chatMessageService.saveUserMessage(bearerToken, request);
    }

    @GetMapping("/messages")
    @ResponseStatus(HttpStatus.OK)
    public ChatMessageListResponse getMessages(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return chatMessageQueryService.getMessages(bearerToken, date);
    }

    @PostMapping("/session-start")
    public ResponseEntity<SessionStartLogResponse> saveSessionStart(
            @RequestHeader("Authorization") String bearerToken
    ) {
        SessionStartLogResponse response = userActivityLogService.saveChatSessionStart(bearerToken);

        if (response.isLogged()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/quick-questions")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, List<Map<String, String>>> getQuickQuestions() {
        return Map.of("quickQuestions", List.of());
    }
}
