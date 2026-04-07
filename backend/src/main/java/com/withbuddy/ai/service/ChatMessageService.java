package com.withbuddy.ai.service;

import com.withbuddy.ai.dto.ChatMessageRequest;
import com.withbuddy.ai.entity.ChatMessage;
import com.withbuddy.ai.entity.MessageType;
import com.withbuddy.ai.entity.SenderType;
import com.withbuddy.ai.repository.ChatMessageRepository;
import com.withbuddy.global.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final JwtService jwtService;

    public void saveUserMessage(String bearerToken, ChatMessageRequest request) {
        String token = bearerToken.replace("Bearer ", "");
        Long loginUserId = jwtService.getUserId(token);

        ChatMessage chatMessage = new ChatMessage(
                loginUserId,
                null,
                SenderType.USER,
                MessageType.USER_QUESTION,
                request.getContent()
        );

        chatMessageRepository.save(chatMessage);
    }
}