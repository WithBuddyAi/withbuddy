package com.withbuddy.chat.service;

import com.withbuddy.chat.dto.ChatMessageListResponse;
import com.withbuddy.chat.dto.ChatMessageResponse;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.repository.ChatMessageRepository;
import com.withbuddy.global.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageQueryService {

    private final ChatMessageRepository chatMessageRepository;
    private final JwtService jwtService;

    public ChatMessageListResponse getMessages(String bearerToken, LocalDate date) {
        String token = bearerToken.replace("Bearer ", "");
        Long userId = jwtService.getUserId(token);

        List<ChatMessageResponse> messages;

        if (date == null) {
            messages = chatMessageRepository.findByUserIdOrderByCreatedAtAsc(userId)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        } else {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            messages = chatMessageRepository
                    .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                            userId, start, end
                    )
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        return new ChatMessageListResponse(messages);
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSuggestionId(),
                message.getSenderType().name(),
                message.getMessageType().getValue(),
                message.getContent(),
                message.getCreatedAt().toString()
        );
    }
}
