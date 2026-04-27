package com.withbuddy.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.entity.ChatMessageDocument;
import com.withbuddy.chat.entity.MessageType;
import com.withbuddy.chat.entity.SenderType;
import com.withbuddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.chat.repository.ChatMessageRepository;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.infrastructure.ai.dto.ConversationTurn;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 비동기 AI 답변 저장 서비스.
 * ChatMessageService ↔ AsyncAiCallService 순환 의존을 방지하기 위해
 * 저장 책임을 별도 클래스로 분리했습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAnswerSaveService {

    private static final int MAX_HISTORY_MESSAGES = 10;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDocumentRepository chatMessageDocumentRepository;
    private final DocumentRepository documentRepository;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveAsyncAnswer(Long questionId, Long userId, AiAnswerServerResponse aiResponse) {
        if (!questionId.equals(aiResponse.getQuestionId())) {
            log.warn("비동기 답변 저장 취소: questionId 불일치. expected={}, actual={}", questionId, aiResponse.getQuestionId());
            redisCacheService.put(ragStatusKey(questionId), "TIMEOUT", RedisCacheTtl.RAG_STATUS);
            return;
        }

        MessageType answerMessageType = aiResponse.getMessageType();
        List<Long> answerDocumentIds = filterExistingDocumentIds(extractDocumentIds(aiResponse));
        ChatMessage savedAnswerMessage = saveAnswerMessage(userId, answerMessageType, aiResponse.getContent(), answerDocumentIds);

        ChatMessage questionMessage = chatMessageRepository.findById(questionId)
                .orElseThrow(() -> new IllegalStateException("질문 메시지를 찾을 수 없습니다: " + questionId));
        saveConversationPair(userId, questionMessage.getContent(), savedAnswerMessage.getContent());

        redisCacheService.put(ragAnswerIdKey(questionId), String.valueOf(savedAnswerMessage.getId()), RedisCacheTtl.RAG_STATUS);
        redisCacheService.put(ragStatusKey(questionId), "COMPLETED", RedisCacheTtl.RAG_STATUS);
    }

    static String ragStatusKey(Long questionId) {
        return "rag:status:" + questionId;
    }

    static String ragAnswerIdKey(Long questionId) {
        return "rag:answer:" + questionId;
    }

    private ChatMessage saveAnswerMessage(Long userId, MessageType type, String content, List<Long> documentIds) {
        ChatMessage answerMessage = new ChatMessage(userId, null, SenderType.BOT, type, content);
        ChatMessage saved = chatMessageRepository.save(answerMessage);
        saveDocumentMappings(saved.getId(), documentIds);
        return saved;
    }

    private void saveDocumentMappings(Long chatMessageId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        List<ChatMessageDocument> mappings = documentIds.stream()
                .map(documentId -> ChatMessageDocument.builder()
                        .chatMessageId(chatMessageId)
                        .documentId(documentId)
                        .build())
                .toList();
        chatMessageDocumentRepository.saveAll(mappings);
    }

    private List<Long> extractDocumentIds(AiAnswerServerResponse aiResponse) {
        if (aiResponse.getDocuments() == null || aiResponse.getDocuments().isEmpty()) {
            return List.of();
        }
        return aiResponse.getDocuments().stream()
                .map(AiAnswerServerResponse.DocumentRef::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> filterExistingDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        Set<Long> existingIds = documentRepository.findByIdInAndIsActiveTrue(documentIds).stream()
                .map(Document::getId)
                .collect(Collectors.toSet());
        return documentIds.stream().filter(existingIds::contains).toList();
    }

    private void saveConversationPair(Long userId, String userQuestion, String assistantAnswer) {
        String key = RedisCacheKeys.conversation(String.valueOf(userId));
        List<ConversationTurn> turns = List.of(
                new ConversationTurn("user", userQuestion),
                new ConversationTurn("assistant", assistantAnswer)
        );
        writeConversationTurnsWithRecovery(key, turns);
    }

    private void writeConversationTurnsWithRecovery(String key, List<ConversationTurn> turns) {
        try {
            writeConversationTurns(key, turns);
        } catch (RuntimeException ex) {
            if (isRedisWrongType(ex)) {
                redisCacheService.delete(key);
                writeConversationTurns(key, turns);
                return;
            }
            throw ex;
        }
    }

    private void writeConversationTurns(String key, List<ConversationTurn> turns) {
        for (ConversationTurn turn : turns) {
            writeConversationTurn(key, turn);
        }
        redisCacheService.listTrim(key, -MAX_HISTORY_MESSAGES, -1);
        redisCacheService.expire(key, RedisCacheTtl.CONVERSATION);
    }

    private void writeConversationTurn(String key, ConversationTurn turn) {
        try {
            redisCacheService.listRightPush(key, objectMapper.writeValueAsString(turn));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대화 이력 직렬화에 실패했습니다.", e);
        }
    }

    private boolean isRedisWrongType(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("WRONGTYPE")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
