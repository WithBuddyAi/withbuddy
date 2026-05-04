package com.withbuddy.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.dto.ChatMessageResponse;
import com.withbuddy.chat.dto.QuickQuestionResponse;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.entity.ChatMessageDocument;
import com.withbuddy.chat.entity.MessageType;
import com.withbuddy.chat.entity.SenderType;
import com.withbuddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.chat.repository.ChatMessageRepository;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.infrastructure.ai.client.AiClient;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.dto.AiUserContext;
import com.withbuddy.infrastructure.ai.dto.ConversationTurn;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import com.withbuddy.storage.service.DocumentDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final Duration CONVERSATION_LOCK_TTL = Duration.ofSeconds(5);
    private static final List<MessageType> HISTORY_TYPES = List.of(MessageType.user_question, MessageType.rag_answer);
    private static final TypeReference<List<ChatMessageResponse.RecommendedContactResponse>> RECOMMENDED_CONTACTS_TYPE =
            new TypeReference<>() {};

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDocumentRepository chatMessageDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentDownloadService documentDownloadService;
    private final AiClient aiClient;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final QuickQuestionCatalog quickQuestionCatalog;
    private final OnboardingSuggestionRepository onboardingSuggestionRepository;

    public ChatMessageCreateResponse saveUserMessage(JwtAuthenticationPrincipal principal, ChatMessageRequest request) {
        Long loginUserId = principal.userId();
        String loginUserName = principal.name();
        String companyCode = principal.companyCode();
        String companyName = principal.companyName();
        List<ConversationTurn> conversationHistory = sanitizeConversationHistoryForAi(
                resolveConversationHistory(loginUserId)
        );

        ChatMessage savedQuestionMessage = transactionTemplate.execute(status -> saveQuestionMessage(loginUserId, request.getContent()));
        if (savedQuestionMessage == null) {
            throw new IllegalStateException("질문 메시지 저장에 실패했습니다.");
        }
        saveConversationQuestion(loginUserId, savedQuestionMessage.getContent());

        AiUserContext userContext = new AiUserContext(
                loginUserId,
                loginUserName,
                companyCode,
                companyName
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

        AiAnswerServerResponse aiResponse = aiClient.requestAnswer(aiRequest);

        if (!savedQuestionMessage.getId().equals(aiResponse.getQuestionId())) {
            throw new IllegalStateException("AI 응답의 questionId가 저장된 질문 ID와 일치하지 않습니다.");
        }
        MessageType answerMessageType = aiResponse.getMessageType();
        List<Long> answerDocumentIds = filterExistingDocumentIds(extractDocumentIds(aiResponse));
        List<ChatMessageResponse.RecommendedContactResponse> recommendedContacts =
                toRecommendedContactResponses(aiResponse.getRecommendedContacts());
        String recommendedContactsJson = serializeRecommendedContacts(recommendedContacts);
        ChatMessage savedAnswerMessage = transactionTemplate.execute(
                status -> saveAnswerMessage(
                        loginUserId,
                        answerMessageType,
                        aiResponse.getContent(),
                        answerDocumentIds,
                        recommendedContactsJson
                )
        );
        if (savedAnswerMessage == null) {
            throw new IllegalStateException("답변 메시지 저장에 실패했습니다.");
        }

        Map<Long, Document> documentMap = resolveDocumentMap(answerDocumentIds);
        Map<Long, DocumentFile> documentFileMap = resolveDocumentFileMap(answerDocumentIds);
        saveConversationAnswer(loginUserId, savedAnswerMessage.getContent());

        return new ChatMessageCreateResponse(
                questionResponse,
                toResponse(savedAnswerMessage, answerDocumentIds, documentMap, documentFileMap)
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
                content,
                null
        );
        return chatMessageRepository.save(questionMessage);
    }

    private ChatMessage saveAnswerMessage(
            Long userId,
            MessageType answerMessageType,
            String answerContent,
            List<Long> answerDocumentIds,
            String recommendedContactsJson
    ) {
        ChatMessage answerMessage = new ChatMessage(
                userId,
                null,
                SenderType.BOT,
                answerMessageType,
                answerContent,
                recommendedContactsJson
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

        return new ChatMessageResponse(
                message.getId(),
                message.getSuggestionId(),
                documents,
                message.getSenderType().name(),
                message.getMessageType().getValue(),
                message.getContent(),
                resolveQuickTaps(message),
                resolveRecommendedContacts(message),
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
            fileResponse = toFileResponse(document, documentFile);
        }

        return new ChatMessageResponse.DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType(),
                fileResponse
        );
    }

    private ChatMessageResponse.FileResponse toFileResponse(Document document, DocumentFile documentFile) {
        if (documentFile == null) {
            return null;
        }

        return new ChatMessageResponse.FileResponse(
                documentFile.getOriginalFileName(),
                documentFile.getContentType(),
                resolveDownloadUrl(document, documentFile)
        );
    }

    private String resolveDownloadUrl(Document document, DocumentFile documentFile) {
        try {
            return documentDownloadService.getDownloadUrl(document, documentFile).getDownloadUrl();
        } catch (RuntimeException ex) {
            log.warn("문서 presigned URL 조회 실패. documentId={}, reason={}", document.getId(), ex.getMessage());
            return "/api/v1/documents/" + document.getId() + "/download";
        }
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
    public ChatMessage saveSuggestionMessageIfNotExists(Long userId, Long suggestionId, String content) {
        return chatMessageRepository.findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                        userId,
                        suggestionId,
                        MessageType.suggestion
                )
                .orElseGet(() -> chatMessageRepository.save(
                        ChatMessage.createSuggestionMessage(userId, suggestionId, content)
                ));
    }

    @Transactional(readOnly = true)
    public ChatMessage findSuggestionMessage(Long userId, Long suggestionId) {
        return chatMessageRepository.findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                userId,
                suggestionId,
                MessageType.suggestion
        ).orElse(null);
    }

    @Transactional
    public ChatMessage saveNudgeMessage(Long userId, Long suggestionId, String content) {
        if (suggestionId != null) {
            return saveSuggestionMessageIfNotExists(userId, suggestionId, content);
        }
        return chatMessageRepository.save(ChatMessage.createSuggestionMessage(userId, null, content));
    }

    @Transactional(readOnly = true)
    public boolean hasSuggestionMessageToday(Long userId, LocalDate date, ZoneId zoneId) {
        LocalDateTime start = date.atStartOfDay(zoneId).toLocalDateTime();
        LocalDateTime end = date.plusDays(1).atStartOfDay(zoneId).toLocalDateTime();

        return chatMessageRepository.existsByUserIdAndMessageTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                MessageType.suggestion,
                start,
                end
        );
    }

    private List<QuickQuestionResponse> resolveQuickTaps(ChatMessage message) {
        if (message.getMessageType() != MessageType.suggestion || message.getSuggestionId() == null) {
            return List.of();
        }

        OnboardingSuggestion suggestion = onboardingSuggestionRepository.findById(message.getSuggestionId())
                .orElse(null);
        if (suggestion == null) {
            return List.of();
        }

        return quickQuestionCatalog.getOnboardingQuickTaps(suggestion.getDayOffset());
    }

    private List<ChatMessageResponse.RecommendedContactResponse> resolveRecommendedContacts(ChatMessage message) {
        if (message.getSenderType() != SenderType.BOT) {
            return List.of();
        }
        return deserializeRecommendedContacts(message.getRecommendedContactsJson());
    }

    public Map<String, List<QuickQuestionResponse>> getQuickQuestions(Long userId) {
        return Map.of("quickQuestions", quickQuestionCatalog.getRandomQuickQuestions(5));
    }

    private List<ChatMessageResponse.RecommendedContactResponse> toRecommendedContactResponses(
            List<AiAnswerServerResponse.RecommendedContactRef> recommendedContacts
    ) {
        if (recommendedContacts == null || recommendedContacts.isEmpty()) {
            return List.of();
        }

        return recommendedContacts.stream()
                .filter(Objects::nonNull)
                .map(contact -> new ChatMessageResponse.RecommendedContactResponse(
                        contact.getDepartment(),
                        contact.getName(),
                        contact.getPosition(),
                        toContactMethodResponses(contact.getConnects())
                ))
                .toList();
    }

    private List<ChatMessageResponse.ContactMethodResponse> toContactMethodResponses(
            List<AiAnswerServerResponse.ContactMethodRef> connects
    ) {
        if (connects == null || connects.isEmpty()) {
            return List.of();
        }

        return connects.stream()
                .filter(Objects::nonNull)
                .map(connect -> new ChatMessageResponse.ContactMethodResponse(
                        connect.getType() == null ? null : connect.getType().name().toLowerCase(Locale.ROOT),
                        connect.getValue()
                ))
                .toList();
    }

    private String serializeRecommendedContacts(List<ChatMessageResponse.RecommendedContactResponse> recommendedContacts) {
        if (recommendedContacts == null || recommendedContacts.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(recommendedContacts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize recommended contacts.", e);
        }
    }

    private List<ChatMessageResponse.RecommendedContactResponse> deserializeRecommendedContacts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, RECOMMENDED_CONTACTS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize recommended contacts.", e);
        }
    }
}
