package com.withbuddy.ai.controller;

import com.withbuddy.ai.dto.ChatMessageRequest;
import com.withbuddy.ai.service.ChatMessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "Ai", description = "버디 API")
public class AiController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/messages")
    public ResponseEntity<Void> sendMessage(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        chatMessageService.saveUserMessage(authorizationHeader, request);
        return ResponseEntity.ok().build();
    }
}
