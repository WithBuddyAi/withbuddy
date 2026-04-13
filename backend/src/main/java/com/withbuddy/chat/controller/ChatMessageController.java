package com.withbuddy.chat.controller;

import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.service.ChatMessageService;
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
