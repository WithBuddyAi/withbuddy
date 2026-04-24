package com.withbuddy.chat.service;

import com.withbuddy.infrastructure.ai.client.AiClient;
import com.withbuddy.chat.dto.ChatMessageCreateResponse;
import com.withbuddy.chat.dto.ChatMessageRequest;
import com.withbuddy.chat.dto.ChatMessageResponse;
import com.withbuddy.chat.entity.ChatMessageDocument;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.entity.MessageType;
import com.withbuddy.chat.entity.SenderType;
import com.withbuddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.chat.repository.ChatMessageRepository;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.dto.AiUserContext;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDocumentRepository chatMessageDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final JwtService jwtService;
    private final AiClient aiClient;
    private final RedisCacheService redisCacheService;

    @Transactional
    public ChatMessageCreateResponse saveUserMessage(String bearerToken, ChatMessageRequest request) {
        String token = extractToken(bearerToken);
        Long loginUserId = jwtService.getUserId(token);
        String loginUserName = jwtService.getName(token);
        String companyCode = jwtService.getCompanyCode(token);

        ChatMessage questionMessage = new ChatMessage(
                loginUserId,
                null,
                SenderType.USER,
                MessageType.user_question,
                request.getContent()
        );

        ChatMessage savedQuestionMessage = chatMessageRepository.save(questionMessage);
        redisCacheService.put(
                RedisCacheKeys.ragStatus(savedQuestionMessage.getId()),
                "PENDING",
                RedisCacheTtl.RAG_STATUS
        );
        redisCacheService.put(
                RedisCacheKeys.conversation(String.valueOf(loginUserId)),
                savedQuestionMessage.getContent(),
                RedisCacheTtl.CONVERSATION
        );

        AiUserContext userContext = new AiUserContext(
                loginUserId,
                loginUserName,
                companyCode
        );

        AiAnswerServerRequest aiRequest = new AiAnswerServerRequest(
                savedQuestionMessage.getId(),
                userContext,
                savedQuestionMessage.getContent()
        );

        AiAnswerServerResponse aiResponse;
        try {
            aiResponse = aiClient.requestAnswer(aiRequest);
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

        ChatMessage answerMessage = new ChatMessage(
                loginUserId,
                null,
                SenderType.BOT,
                answerMessageType,
                aiResponse.getContent()
        );

        ChatMessage savedAnswerMessage = chatMessageRepository.save(answerMessage);
        saveDocumentMappings(savedAnswerMessage.getId(), answerDocumentIds);

        Map<Long, Document> documentMap = resolveDocumentMap(answerDocumentIds);
        Map<Long, DocumentFile> documentFileMap = resolveDocumentFileMap(answerDocumentIds);

        return new ChatMessageCreateResponse(
                toResponse(
                        savedQuestionMessage,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        Collections.emptyMap()
                ),
                toResponse(
                        savedAnswerMessage,
                        answerDocumentIds,
                        documentMap,
                        documentFileMap
                )
        );
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

    private String extractToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authorization 헤더 형식이 올바르지 않습니다.");
        }
        return bearerToken.substring(7);
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

    public Map<String, List<Map<String, String>>> getQuickQuestions(String bearerToken) {
        String token = extractToken(bearerToken);
        jwtService.getUserId(token);

        return Map.of(
                "quickQuestions",
                List.of(
                        Map.of("content", "연차는 어떻게 신청하나요?"),
                        Map.of("content", "급여일이 언제인가요?"),
                        Map.of("content", "건강검진은 어떻게 받나요?")
                )
        );
    }
}
