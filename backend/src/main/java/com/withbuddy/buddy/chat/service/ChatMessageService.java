package com.withbuddy.buddy.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.buddy.chat.dto.request.ChatMessageRequest;
import com.withbuddy.buddy.chat.dto.response.ChatMessageResponse;
import com.withbuddy.buddy.chat.dto.response.ChatStreamAnswerCompletedResponse;
import com.withbuddy.buddy.chat.dto.response.ChatStreamAnswerDeltaResponse;
import com.withbuddy.buddy.chat.dto.response.ChatStreamErrorResponse;
import com.withbuddy.buddy.chat.dto.response.ChatStreamQuestionSavedResponse;
import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import com.withbuddy.buddy.chat.entity.ChatMessage;
import com.withbuddy.buddy.chat.entity.ChatMessageDocument;
import com.withbuddy.buddy.chat.entity.MessageType;
import com.withbuddy.buddy.chat.entity.SenderType;
import com.withbuddy.buddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.buddy.chat.repository.ChatMessageRepository;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.infrastructure.ai.client.AiStreamClient;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.dto.ConversationTurn;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.buddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.buddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int RETRY_REUSE_WINDOW_MINUTES = 10;
    private static final AtomicLong AI_TIMEOUT_ERROR_COUNT = new AtomicLong(0);
    private static final AtomicLong AI_HTTP_500_ERROR_COUNT = new AtomicLong(0);
    private static final AtomicLong AI_NETWORK_ERROR_COUNT = new AtomicLong(0);
    private static final AtomicLong AI_OTHER_ERROR_COUNT = new AtomicLong(0);
    private static final TypeReference<List<ChatMessageResponse.RecommendedContactResponse>> RECOMMENDED_CONTACTS_TYPE =
            new TypeReference<>() {};

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDocumentRepository chatMessageDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final AiStreamClient aiStreamClient;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    @Qualifier("aiCallExecutor")
    private final Executor aiCallExecutor;
    private final QuickQuestionCatalog quickQuestionCatalog;
    private final OnboardingSuggestionRepository onboardingSuggestionRepository;

    public SseEmitter streamUserMessage(JwtAuthenticationPrincipal principal, ChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        aiCallExecutor.execute(() -> {
            Long questionId = null;
            try {
                Long loginUserId = principal.userId();
                String loginUserName = principal.name();
                String companyCode = principal.companyCode();
                String hireDate = principal.hireDate();

                SavedQuestionContext questionContext = transactionTemplate.execute(
                        status -> resolveQuestionMessage(loginUserId, request.getContent())
                );
                if (questionContext == null || questionContext.message() == null) {
                    throw new IllegalStateException("질문 메시지 저장에 실패했습니다.");
                }

                ChatMessage savedQuestionMessage = questionContext.message();
                questionId = savedQuestionMessage.getId();
                if (questionContext.newlyCreated()) {
                    saveConversationQuestion(loginUserId, savedQuestionMessage.getContent());
                } else {
                    ensureConversationQuestionOnRetry(loginUserId, savedQuestionMessage.getContent());
                }

                ChatMessageResponse questionResponse = toResponse(
                        savedQuestionMessage,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        Collections.emptyMap()
                );
                sendStreamEvent(emitter, "question_saved", new ChatStreamQuestionSavedResponse(questionResponse));

                AiAnswerServerResponse aiResponse = aiStreamClient.streamAnswer(
                        questionId,
                        loginUserId,
                        loginUserName,
                        companyCode,
                        hireDate,
                        savedQuestionMessage.getContent(),
                        delta -> forwardDelta(emitter, delta)
                );

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

                ChatMessageResponse answerResponse = toResponse(
                        savedAnswerMessage,
                        answerDocumentIds,
                        documentMap,
                        documentFileMap
                );
                sendStreamEvent(
                        emitter,
                        "answer_completed",
                        new ChatStreamAnswerCompletedResponse(questionId, answerResponse)
                );
                emitter.complete();
            } catch (AiTimeoutException exception) {
                logAiErrorMetric("TIMEOUT", questionId, exception);
                sendStreamError(emitter, "AI_TIMEOUT", "AI 답변 생성 시간이 초과되었습니다.", questionId);
            } catch (Exception exception) {
                logAiErrorMetric(classifyAiErrorType(exception), questionId, exception);
                log.error("SSE chat stream failed. questionId={}", questionId, exception);
                sendStreamError(emitter, "AI_STREAM_FAILED", "AI 답변 생성 중 오류가 발생했습니다.", questionId);
            }
        });

        return emitter;
    }

    private void forwardDelta(SseEmitter emitter, ChatStreamAnswerDeltaResponse delta) {
        try {
            sendStreamEvent(emitter, "answer_delta", delta);
        } catch (IOException e) {
            throw new IllegalStateException("answer_delta 전송에 실패했습니다.", e);
        }
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

    private SavedQuestionContext resolveQuestionMessage(Long userId, String content) {
        String normalizedContent = content == null ? "" : content.trim();

        ChatMessage latestQuestion = chatMessageRepository
                .findTopByUserIdAndSenderTypeAndMessageTypeOrderByCreatedAtDesc(
                        userId,
                        SenderType.USER,
                        MessageType.user_question
                )
                .orElse(null);

        if (isReusableRetriedQuestion(userId, normalizedContent, latestQuestion)) {
            log.info("타임아웃 재시도 질문 재사용: userId={}, questionId={}", userId, latestQuestion.getId());
            return new SavedQuestionContext(latestQuestion, false);
        }

        ChatMessage saved = saveQuestionMessage(userId, normalizedContent);
        return new SavedQuestionContext(saved, true);
    }

    private boolean isReusableRetriedQuestion(Long userId, String normalizedContent, ChatMessage latestQuestion) {
        if (latestQuestion == null) {
            return false;
        }
        if (!Objects.equals(normalizedContent, latestQuestion.getContent())) {
            return false;
        }

        LocalDateTime reusableThreshold = LocalDateTime.now().minusMinutes(RETRY_REUSE_WINDOW_MINUTES);
        if (latestQuestion.getCreatedAt().isBefore(reusableThreshold)) {
            return false;
        }

        return !chatMessageRepository.existsByUserIdAndSenderTypeAndCreatedAtGreaterThanEqual(
                userId,
                SenderType.BOT,
                latestQuestion.getCreatedAt()
        );
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
                "/api/v1/chat/documents/" + document.getId() + "/download"
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

    private void saveConversationQuestion(Long userId, String userQuestion) {
        saveConversationTurn(userId, new ConversationTurn("user", userQuestion));
    }

    private void ensureConversationQuestionOnRetry(Long userId, String userQuestion) {
        String key = RedisCacheKeys.conversation(String.valueOf(userId));
        List<String> cachedTurns = readConversationTurnsWithRecovery(key);

        boolean hasSameQuestionTurn = cachedTurns.stream()
                .map(this::deserializeConversationTurnQuietly)
                .filter(Objects::nonNull)
                .anyMatch(turn ->
                        "user".equals(turn.role()) && Objects.equals(userQuestion, turn.content())
                );

        if (hasSameQuestionTurn) {
            return;
        }

        // Edge-case recovery: DB에는 질문이 있으나 Redis 이력이 누락된 경우 보강한다.
        writeConversationTurnsWithRecovery(key, List.of(new ConversationTurn("user", userQuestion)));
        log.info("재시도 경로 Redis 질문 이력 보강: userId={}", userId);
    }

    private List<String> readConversationTurnsWithRecovery(String key) {
        try {
            return redisCacheService.listRange(key, -MAX_HISTORY_MESSAGES, -1);
        } catch (RuntimeException ex) {
            if (isRedisWrongType(ex)) {
                redisCacheService.delete(key);
                log.warn("Redis conversation key WRONGTYPE 복구: key={}", key);
                return List.of();
            }
            throw ex;
        }
    }

    private void saveConversationAnswer(Long userId, String assistantAnswer) {
        saveConversationTurn(userId, new ConversationTurn("assistant", assistantAnswer));
    }

    private void saveConversationTurn(Long userId, ConversationTurn turn) {
        String key = RedisCacheKeys.conversation(String.valueOf(userId));
        writeConversationTurnsWithRecovery(key, List.of(turn));
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

    private ConversationTurn deserializeConversationTurnQuietly(String rawTurn) {
        if (rawTurn == null || rawTurn.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawTurn, ConversationTurn.class);
        } catch (JsonProcessingException e) {
            return null;
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

    private String classifyAiErrorType(Throwable throwable) {
        if (containsStatusCode(throwable, 500)) {
            return "HTTP_500";
        }
        if (hasCause(throwable, ConnectException.class) || hasCause(throwable, UnknownHostException.class)) {
            return "NETWORK";
        }
        return "OTHER";
    }

    private boolean containsStatusCode(Throwable throwable, int statusCode) {
        Throwable current = throwable;
        String token = "status=" + statusCode;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(token)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> targetType) {
        Throwable current = throwable;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logAiErrorMetric(String errorType, Long questionId, Throwable throwable) {
        long count = switch (errorType) {
            case "TIMEOUT" -> AI_TIMEOUT_ERROR_COUNT.incrementAndGet();
            case "HTTP_500" -> AI_HTTP_500_ERROR_COUNT.incrementAndGet();
            case "NETWORK" -> AI_NETWORK_ERROR_COUNT.incrementAndGet();
            default -> AI_OTHER_ERROR_COUNT.incrementAndGet();
        };

        log.warn(
                "[AI_ERROR_METRIC] type={}, count={}, questionId={}, errorClass={}, message={}",
                errorType,
                count,
                questionId,
                throwable.getClass().getSimpleName(),
                throwable.getMessage()
        );
    }

    private void sendStreamEvent(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(
                SseEmitter.event()
                        .name(eventName)
                        .data(payload)
        );
    }

    private void sendStreamError(SseEmitter emitter, String code, String message, Long questionId) {
        try {
            sendStreamEvent(emitter, "error", new ChatStreamErrorResponse(code, message, questionId));
            emitter.complete();
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
        }
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

    private record SavedQuestionContext(ChatMessage message, boolean newlyCreated) {
    }
}
