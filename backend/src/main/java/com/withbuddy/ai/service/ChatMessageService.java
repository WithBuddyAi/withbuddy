package com.withbuddy.ai.service;

import com.withbuddy.ai.client.AiClient;
import com.withbuddy.ai.dto.*;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.entity.MessageType;
import com.withbuddy.chat.entity.SenderType;
import com.withbuddy.ai.repository.ChatMessageRepository;
import com.withbuddy.global.jwt.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final JwtService jwtService;
    private final AiClient aiClient;

    @Transactional
    public ChatMessageCreateResponse saveUserMessage(String bearerToken, ChatMessageRequest request) {
        String token = bearerToken.replace("Bearer ", "");
        Long loginUserId = jwtService.getUserId(token);
        String companyCode = jwtService.getCompanyCode(token);

        ChatMessage questionMessage = new ChatMessage(
                loginUserId,
                null,
                SenderType.USER,
                MessageType.USER_QUESTION,
                request.getContent()
        );

        ChatMessage savedQuestionMessage = chatMessageRepository.save(questionMessage);

        AiServerRequest aiRequest = new AiServerRequest(
                savedQuestionMessage.getId(),
                companyCode,
                savedQuestionMessage.getContent()
        );

        AiServerResponse aiResponse = aiClient.requestAnswer(aiRequest);

        MessageType answerMessageType = convertMessageType(aiResponse.getMessageType());

        ChatMessage answerMessage = new ChatMessage(
                loginUserId,
                savedQuestionMessage.getId(),
                SenderType.BOT,
                answerMessageType,
                aiResponse.getContent()
        );

        ChatMessage savedAnswerMessage = chatMessageRepository.save(answerMessage);

        ChatMessageResponse questionResponse = new ChatMessageResponse(
                savedQuestionMessage.getId(),
                savedQuestionMessage.getSenderType().name(),
                savedQuestionMessage.getMessageType().getValue(),
                savedQuestionMessage.getContent(),
                null,
                savedQuestionMessage.getCreatedAt().toString()
        );

        ChatMessageResponse answerResponse = new ChatMessageResponse(
                savedAnswerMessage.getId(),
                savedAnswerMessage.getSenderType().name(),
                savedAnswerMessage.getMessageType().getValue(),
                savedAnswerMessage.getContent(),
                null,
                savedAnswerMessage.getCreatedAt().toString()
        );

        return new ChatMessageCreateResponse(questionResponse, answerResponse);
    }

    private MessageType convertMessageType(String aiMessageType) {
        return switch (aiMessageType) {
            case "rag_answer" -> MessageType.RAG_ANSWER;
            case "no_result" -> MessageType.NO_RESULT;
            case "out_of_scope" -> MessageType.OUT_OF_SCOPE;
            default -> throw new IllegalArgumentException("지원하지 않는 AI 응답 타입입니다: " + aiMessageType);
        };
    }
}