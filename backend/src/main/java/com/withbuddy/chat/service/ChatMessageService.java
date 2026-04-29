package com.withbuddy.chat.service;

import com.withbuddy.chat.dto.QuickQuestionResponse;
import com.withbuddy.infrastructure.ai.client.AiClient;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.dto.ChatMessageResponse;
import com.withbuddy.infrastructure.ai.dto.ConversationTurn;
import com.withbuddy.chat.entity.ChatMessageDocument;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.entity.MessageType;
import com.withbuddy.chat.entity.SenderType;
import com.withbuddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.chat.repository.ChatMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.dto.AiUserContext;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final Duration CONVERSATION_LOCK_TTL = Duration.ofSeconds(5);
    private static final List<MessageType> HISTORY_TYPES = List.of(MessageType.user_question, MessageType.rag_answer);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDocumentRepository chatMessageDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final AiClient aiClient;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AsyncAiCallService asyncAiCallService;
    private final QuickQuestionCatalog quickQuestionCatalog;

    public ChatMessageCreateResponse saveUserMessage(JwtAuthenticationPrincipal principal, ChatMessageRequest request) {
        Long loginUserId = principal.userId();
        String loginUserName = principal.name();
        String companyCode = principal.companyCode();
        List<ConversationTurn> conversationHistory = sanitizeConversationHistoryForAi(
                resolveConversationHistory(loginUserId)
        );

        ChatMessage savedQuestionMessage = transactionTemplate.execute(status -> saveQuestionMessage(loginUserId, request.getContent()));
        if (savedQuestionMessage == null) {
            throw new IllegalStateException("질문 메시지 저장에 실패했습니다.");
        }
        redisCacheService.put(
                RedisCacheKeys.ragStatus(savedQuestionMessage.getId()),
                "PENDING",
                RedisCacheTtl.RAG_STATUS
        );
        saveConversationQuestion(loginUserId, savedQuestionMessage.getContent());

        AiUserContext userContext = new AiUserContext(
                loginUserId,
                loginUserName,
                companyCode
        );

        AiAnswerServerRequest aiRequest = new AiAnswerServerRequest(
                savedQuestionMessage.getId(),
                userContext,
                savedQuestionMessage.getContent(),
                conversationHistory
        );

        ChatMessageResponse questionResponse = toResponse(
                savedQuestionMessage,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );

        AiAnswerServerResponse aiResponse;
        try {
            aiResponse = aiClient.requestAnswer(aiRequest);
        } catch (AiTimeoutException ex) {
            try {
                asyncAiCallService.callAndSaveAnswer(savedQuestionMessage.getId(), loginUserId, aiRequest);
            } catch (TaskRejectedException schedulingEx) {
                redisCacheService.put(
                        RedisCacheKeys.ragStatus(savedQuestionMessage.getId()),
                        "TIMEOUT",
                        RedisCacheTtl.RAG_STATUS
                );
                return new ChatMessageCreateResponse(questionResponse, null, "TIMEOUT");
            }
            return new ChatMessageCreateResponse(questionResponse, null, "PENDING");
        } catch (RuntimeException ex) {
            redisCacheService.put(
                    RedisCacheKeys.ragStatus(savedQuestionMessage.getId()),
                    "TIMEOUT",
                    RedisCacheTtl.RAG_STATUS
            );
            throw ex;
        }

        if (!savedQuestionMessage.getId().equals(aiResponse.getQuestionId())) {
            throw new IllegalStateException("AI 응답의 questionId가 저장된 질문 ID와 일치하지 않습니다.");
        }
        redisCacheService.put(
                RedisCacheKeys.ragStatus(savedQuestionMessage.getId()),
                "COMPLETED",
                RedisCacheTtl.RAG_STATUS
        );

        MessageType answerMessageType = aiResponse.getMessageType();
        List<Long> answerDocumentIds = filterExistingDocumentIds(extractDocumentIds(aiResponse));
        ChatMessage savedAnswerMessage = transactionTemplate.execute(
                status -> saveAnswerMessage(loginUserId, answerMessageType, aiResponse.getContent(), answerDocumentIds)
        );
        if (savedAnswerMessage == null) {
            throw new IllegalStateException("답변 메시지 저장에 실패했습니다.");
        }

        Map<Long, Document> documentMap = resolveDocumentMap(answerDocumentIds);
        Map<Long, DocumentFile> documentFileMap = resolveDocumentFileMap(answerDocumentIds);
        saveConversationAnswer(loginUserId, savedAnswerMessage.getContent());

        return new ChatMessageCreateResponse(
                questionResponse,
                toResponse(
                        savedAnswerMessage,
                        answerDocumentIds,
                        documentMap,
                        documentFileMap
                ),
                "COMPLETED"
        );
    }

    private List<ConversationTurn> sanitizeConversationHistoryForAi(List<ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        return history.stream()
                .filter(Objects::nonNull)
                .filter(turn -> isValidRole(turn.role()) && hasText(turn.content()))
                .toList();
    }

    private ChatMessage saveQuestionMessage(Long userId, String content) {
        ChatMessage questionMessage = new ChatMessage(
                userId,
                null,
                SenderType.USER,
                MessageType.user_question,
                content
        );
        return chatMessageRepository.save(questionMessage);
    }

    private ChatMessage saveAnswerMessage(Long userId, MessageType answerMessageType, String answerContent, List<Long> answerDocumentIds) {
        ChatMessage answerMessage = new ChatMessage(
                userId,
                null,
                SenderType.BOT,
                answerMessageType,
                answerContent
        );
        ChatMessage savedAnswerMessage = chatMessageRepository.save(answerMessage);
        saveDocumentMappings(savedAnswerMessage.getId(), answerDocumentIds);
        return savedAnswerMessage;
    }

    private ChatMessageResponse toResponse(
            ChatMessage message,
            List<Long> documentIds,
            Map<Long, Document> documentMap,
            Map<Long, DocumentFile> documentFileMap
    ) {
        List<ChatMessageResponse.DocumentResponse> documents = documentIds.stream()
                .map(documentId -> toDocumentResponse(documentId, documentMap, documentFileMap))
                .filter(Objects::nonNull)
                .toList();

        List<ChatMessageResponse.RecommendedContactResponse> recommendedContacts =
                resolveRecommendedContacts(message);

        return new ChatMessageResponse(
                message.getId(),
                message.getSuggestionId(),
                documents,
                message.getSenderType().name(),
                message.getMessageType().getValue(),
                message.getContent(),
                recommendedContacts,
                message.getCreatedAt().toString()
        );
    }

    private ChatMessageResponse.DocumentResponse toDocumentResponse(
            Long documentId,
            Map<Long, Document> documentMap,
            Map<Long, DocumentFile> documentFileMap
    ) {
        Document document = documentMap.get(documentId);
        if (document == null) {
            return null;
        }

        ChatMessageResponse.FileResponse fileResponse = null;

        if ("TEMPLATE".equals(document.getDocumentType())) {
            DocumentFile documentFile = documentFileMap.get(documentId);
            fileResponse = toFileResponse(documentId, documentFile);
        }

        return new ChatMessageResponse.DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType(),
                fileResponse
        );
    }

    private ChatMessageResponse.FileResponse toFileResponse(Long documentId, DocumentFile documentFile) {
        if (documentFile == null) {
            return null;
        }

        return new ChatMessageResponse.FileResponse(
                documentFile.getOriginalFileName(),
                documentFile.getContentType(),
                "/api/v1/documents/" + documentId + "/download"
        );
    }

    private Map<Long, Document> resolveDocumentMap(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return documentRepository.findByIdInAndIsActiveTrue(documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
    }

    private Map<Long, DocumentFile> resolveDocumentFileMap(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return documentFileRepository.findByDocumentIdIn(documentIds).stream()
                .collect(Collectors.toMap(
                        DocumentFile::getDocumentId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
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

        return documentIds.stream()
                .filter(existingIds::contains)
                .toList();
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

    private List<ConversationTurn> resolveConversationHistory(Long userId) {
        List<ConversationTurn> redisHistory = loadConversationHistoryFromRedis(userId);
        if (!redisHistory.isEmpty()) {
            return redisHistory;
        }

        String lockKey = RedisCacheKeys.conversationLock(userId);
        String lockValue = UUID.randomUUID().toString();

        if (redisCacheService.putIfAbsent(lockKey, lockValue, CONVERSATION_LOCK_TTL)) {
            try {
                List<ConversationTurn> dbHistory = loadConversationHistoryFromDb(userId);
                if (!dbHistory.isEmpty()) {
                    saveConversationHistoryList(userId, dbHistory);
                }
                return dbHistory;
            } finally {
                redisCacheService.releaseLock(lockKey, lockValue);
            }
        }

        sleepBriefly();
        return loadConversationHistoryFromRedis(userId);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<ConversationTurn> loadConversationHistoryFromRedis(Long userId) {
        String key = RedisCacheKeys.conversation(String.valueOf(userId));
        List<String> serialized;
        try {
            serialized = redisCacheService.listRange(key, 0, -1);
        } catch (RuntimeException ex) {
            if (isRedisWrongType(ex)) {
                // 배포 전환 구간에서 legacy String key를 정리하고 DB fallback 경로를 사용한다.
                redisCacheService.delete(key);
                return List.of();
            }
            throw ex;
        }
        if (serialized.isEmpty()) {
            return List.of();
        }

        List<ConversationTurn> history = new ArrayList<>();
        for (String item : serialized) {
            try {
                ConversationTurn turn = objectMapper.readValue(item, ConversationTurn.class);
                if (isValidRole(turn.role()) && hasText(turn.content())) {
                    history.add(turn);
                }
            } catch (JsonProcessingException ignored) {
                // invalid history entry는 건너뛰고 가능한 항목만 사용
            }
        }
        return history;
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

    private List<ConversationTurn> loadConversationHistoryFromDb(Long userId) {
        List<ChatMessage> recent = chatMessageRepository.findTop10ByUserIdAndMessageTypeInOrderByCreatedAtDesc(userId, HISTORY_TYPES);
        if (recent.isEmpty()) {
            return List.of();
        }

        return recent.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(this::toConversationTurn)
                .toList();
    }

    private ConversationTurn toConversationTurn(ChatMessage message) {
        String role = message.getSenderType() == SenderType.USER ? "user" : "assistant";
        return new ConversationTurn(role, message.getContent());
    }

    private void saveConversationQuestion(Long userId, String userQuestion) {
        saveConversationTurn(userId, new ConversationTurn("user", userQuestion));
    }

    private void saveConversationAnswer(Long userId, String assistantAnswer) {
        saveConversationTurn(userId, new ConversationTurn("assistant", assistantAnswer));
    }

    private void saveConversationTurn(Long userId, ConversationTurn turn) {
        String key = RedisCacheKeys.conversation(String.valueOf(userId));
        writeConversationTurnsWithRecovery(key, List.of(turn));
    }

    private void saveConversationHistoryList(Long userId, List<ConversationTurn> history) {
        if (history.isEmpty()) {
            return;
        }
        String key = RedisCacheKeys.conversation(String.valueOf(userId));
        writeConversationTurnsWithRecovery(key, history);
    }

    private void writeConversationTurnsWithRecovery(String key, List<ConversationTurn> turns) {
        try {
            writeConversationTurns(key, turns);
        } catch (RuntimeException ex) {
            if (isRedisWrongType(ex)) {
                // 롤링 배포 구간에서 legacy String key가 재생성될 수 있어 쓰기 경로도 복구 처리한다.
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

    private boolean isValidRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Transactional
    public void saveSuggestionMessageIfNotExists(Long userId, Long suggestionId, String content) {
        boolean exists = chatMessageRepository.existsByUserIdAndSuggestionIdAndMessageType(
                userId,
                suggestionId,
                MessageType.suggestion
        );

        if (exists) {
            return;
        }

        ChatMessage message = ChatMessage.createSuggestionMessage(userId, suggestionId, content);
        chatMessageRepository.save(message);
    }

    private List<ChatMessageResponse.RecommendedContactResponse> resolveRecommendedContacts(ChatMessage message) {
        if (message.getSenderType() != SenderType.BOT || message.getMessageType() != MessageType.no_result) {
            return List.of();
        }

        return List.of(
                new ChatMessageResponse.RecommendedContactResponse(
                        "경영지원팀",
                        "김지수",
                        "매니저",
                        List.of(
                                new ChatMessageResponse.ContactMethodResponse(
                                        ChatMessageResponse.ContactMethodResponse.ContactType.EMAIL,
                                        "jisoo.kim@withbuddy.ai"
                                ),
                                new ChatMessageResponse.ContactMethodResponse(
                                        ChatMessageResponse.ContactMethodResponse.ContactType.SLACK,
                                        "@jisoo.kim"
                                )
                        )
                )
        );
    }

    public Map<String, List<QuickQuestionResponse>> getQuickQuestions(Long userId) {
        return Map.of(
                "quickQuestions",
                quickQuestionCatalog.getRandomQuickQuestions(5)
        );
    }
}
