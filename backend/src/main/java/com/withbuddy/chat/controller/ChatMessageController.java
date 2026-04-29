package com.withbuddy.chat.controller;

import com.withbuddy.activity.dto.LogResponse;
import com.withbuddy.activity.entity.EventTarget;
import com.withbuddy.activity.entity.EventType;
import com.withbuddy.activity.log.RedisActivityLogService;
import com.withbuddy.activity.log.RmqActivityLogService;
import com.withbuddy.activity.service.UserActivityLogService;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageListResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.dto.ChatMessageStatusResponse;
import com.withbuddy.chat.service.ChatMessageQueryService;
import com.withbuddy.chat.service.ChatMessageService;
import com.withbuddy.global.jwt.JwtService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final JwtService jwtService;
    private final RedisActivityLogService redisActivityLogService;
    private final RmqActivityLogService rmqActivityLogService;

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageCreateResponse sendMessage(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return chatMessageService.saveUserMessage(bearerToken, request);
    }

    @GetMapping("/messages")
    @ResponseStatus(HttpStatus.OK)
    public ChatMessageListResponse getMessages(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return chatMessageQueryService.getMessages(bearerToken, date);
    }

    @PostMapping("/session-start")
    public ResponseEntity<LogResponse> saveSessionStart(
            @RequestHeader(value = "Authorization", required = false) String bearerToken
    ) {
        LogResponse response = userActivityLogService.saveChatSessionStart(bearerToken);
        if (response.isLogged()) {
            Long userId = extractUserId(bearerToken);
            redisActivityLogService.append(userId, EventType.SESSION_START, EventTarget.CHAT);
            rmqActivityLogService.publish(userId, EventType.SESSION_START, EventTarget.CHAT);
        }

        if (response.isLogged()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/quick-questions")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, List<Map<String, String>>> getQuickQuestions(
            @RequestHeader(value = "Authorization", required = false) String bearerToken
    ) {
        return chatMessageService.getQuickQuestions(bearerToken);
    }

    @PostMapping("/quick-questions/click")
    @ResponseStatus(HttpStatus.CREATED)
    public LogResponse saveQuickQuestionClick(
            @RequestHeader(value = "Authorization", required = false) String bearerToken
    ) {
        LogResponse response = userActivityLogService.saveQuickQuestionClick(bearerToken);
        Long userId = extractUserId(bearerToken);
        redisActivityLogService.append(userId, EventType.BUTTON_CLICK, EventTarget.QUICK_TAP);
        rmqActivityLogService.publish(userId, EventType.BUTTON_CLICK, EventTarget.QUICK_TAP);
        return response;
    }

    @GetMapping("/messages/{questionId}/status")
    @ResponseStatus(HttpStatus.OK)
    public ChatMessageStatusResponse getMessageStatus(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @PathVariable Long questionId
    ) {
        return chatMessageQueryService.getMessageStatus(bearerToken, questionId);
    }

    private Long extractUserId(String bearerToken) {
        String token = jwtService.extractBearerToken(bearerToken);
        return jwtService.getUserId(token);
    }
}
