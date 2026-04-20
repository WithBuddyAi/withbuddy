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
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.dto.AiUserContext;
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

        AiAnswerServerResponse aiResponse = aiClient.requestAnswer(aiRequest);

        if (!savedQuestionMessage.getId().equals(aiResponse.getQuestionId())) {
            throw new IllegalStateException("AI 응답의 questionId가 저장된 질문 ID와 일치하지 않습니다.");
        }

        MessageType answerMessageType = aiResponse.getMessageType();
        List<Long> answerDocumentIds = extractDocumentIds(aiResponse);

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

        return new ChatMessageResponse(
                message.getId(),
                message.getSuggestionId(),
                documents,
                message.getSenderType().name(),
                message.getMessageType().getValue(),
                message.getContent(),
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
            throw new IllegalArgumentException("Authorization 헤더 형식이 올바르지 않습니다.");
        }
        return bearerToken.substring(7);
    }
}