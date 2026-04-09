package com.withbuddy.ai.controller;

import com.withbuddy.ai.dto.ChatMessageCreateResponse;
import com.withbuddy.ai.dto.ChatMessageRequest;
import com.withbuddy.ai.service.ChatMessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Ai", description = "버디 채팅 API")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageCreateResponse sendMessage(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return chatMessageService.saveUserMessage(bearerToken, request);
    }
}
