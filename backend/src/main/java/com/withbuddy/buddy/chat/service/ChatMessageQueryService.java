package com.withbuddy.buddy.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.buddy.chat.dto.response.ChatMessageListResponse;
import com.withbuddy.buddy.chat.dto.response.ChatMessageResponse;
import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import com.withbuddy.buddy.chat.entity.ChatMessage;
import com.withbuddy.buddy.chat.entity.ChatMessageDocument;
import com.withbuddy.buddy.chat.entity.MessageType;
import com.withbuddy.buddy.chat.entity.SenderType;
import com.withbuddy.buddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.buddy.chat.repository.ChatMessageRepository;
import com.withbuddy.buddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.buddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.storage.dto.response.DocumentDownloadResponse;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.exception.StorageException;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import com.withbuddy.storage.service.DocumentDownloadService;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageQueryService {

    private static final TypeReference<List<ChatMessageResponse.RecommendedContactResponse>> RECOMMENDED_CONTACTS_TYPE =
            new TypeReference<>() {};

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDocumentRepository chatMessageDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentDownloadService documentDownloadService;
    private final ObjectMapper objectMapper;
    private final QuickQuestionCatalog quickQuestionCatalog;
    private final OnboardingSuggestionRepository onboardingSuggestionRepository;
    private final UserRepository userRepository;

    public ChatMessageListResponse getMessages(Long userId, LocalDate date) {
        User user = requireMessageQueryAllowed(userId);
        List<ChatMessage> chatMessages = findChatMessages(userId, date);
        if (chatMessages.isEmpty()) {
            return new ChatMessageListResponse(Collections.emptyList());
        }

        Map<Long, List<Long>> documentIdsByMessageId = resolveDocumentIdsByMessageId(chatMessages);
        List<Long> allDocumentIds = documentIdsByMessageId.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

        Map<Long, Document> documentMap = resolveDocumentMap(allDocumentIds);
        Map<Long, DocumentFile> documentFileMap = resolveDocumentFileMap(allDocumentIds);

        List<ChatMessageResponse> messages = chatMessages.stream()
                .map(message -> toResponse(
                        message,
                        documentIdsByMessageId.getOrDefault(message.getId(), Collections.emptyList()),
                        documentMap,
                        documentFileMap,
                        canExposeQuickTaps(user)
                ))
                .toList();

        return new ChatMessageListResponse(messages);
    }

    private List<ChatMessage> findChatMessages(Long userId, LocalDate date) {
        if (date == null) {
            return chatMessageRepository.findByUserIdOrderByCreatedAtAsc(userId);
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return chatMessageRepository.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                userId,
                start,
                end
        );
    }

    private Map<Long, List<Long>> resolveDocumentIdsByMessageId(List<ChatMessage> chatMessages) {
        if (chatMessages.isEmpty()) {
            return Map.of();
        }

        List<Long> messageIds = chatMessages.stream()
                .map(ChatMessage::getId)
                .toList();

        List<ChatMessageDocument> mappings = chatMessageDocumentRepository.findByChatMessageIdIn(messageIds);
        return mappings.stream()
                .sorted(Comparator.comparing(ChatMessageDocument::getId))
                .collect(Collectors.groupingBy(
                        ChatMessageDocument::getChatMessageId,
                        Collectors.mapping(ChatMessageDocument::getDocumentId, Collectors.toList())
                ));
    }

    private Map<Long, Document> resolveDocumentMap(List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }

        return documentRepository.findByIdInAndIsActiveTrue(documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
    }

    private Map<Long, DocumentFile> resolveDocumentFileMap(List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }

        return documentFileRepository.findByDocumentIdIn(documentIds).stream()
                .collect(Collectors.toMap(
                        DocumentFile::getDocumentId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private ChatMessageResponse toResponse(
            ChatMessage message,
            List<Long> documentIds,
            Map<Long, Document> documentMap,
            Map<Long, DocumentFile> documentFileMap,
            boolean exposeQuickTaps
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
                resolveQuickTaps(message, exposeQuickTaps),
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

    public DocumentDownloadResponse getDocumentDownloadUrl(Long userId, Long documentId) {
        requireChatDocumentDownloadAllowed(userId);
        if (!chatMessageDocumentRepository.existsByUserIdAndDocumentId(userId, documentId)) {
            throw new StorageException(HttpStatus.FORBIDDEN, "FORBIDDEN", "documentId", "채팅에서 수신하지 않은 문서입니다.");
        }

        return documentDownloadService.getDownloadUrl(documentId);
    }

    private User requireMessageQueryAllowed(Long userId) {
        User user = findUser(userId);
        boolean readableUser = user.getRole() == UserRole.USER
                && (user.getAccountStatus() == UserAccountStatus.ACTIVE
                || user.getAccountStatus() == UserAccountStatus.READ_ONLY);
        if (!readableUser && user.getRole() != UserRole.SERVICE_ADMIN) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "접근 권한이 없습니다.");
        }
        return user;
    }

    private boolean canExposeQuickTaps(User user) {
        return (user.getRole() == UserRole.USER && user.getAccountStatus() == UserAccountStatus.ACTIVE) || user.getRole() == UserRole.SERVICE_ADMIN;
    }

    private void requireChatDocumentDownloadAllowed(Long userId) {
        User user = findUser(userId);
        if (user.getRole() == UserRole.USER && user.getAccountStatus() == UserAccountStatus.INACTIVE) {
            throw new ForbiddenException("ACCESS_DENIED", "role", "현재 역할에서는 문서를 다운로드할 수 없습니다.");
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));
    }

    private List<QuickQuestionResponse> resolveQuickTaps(ChatMessage message, boolean exposeQuickTaps) {
        if (!exposeQuickTaps) {
            return List.of();
        }
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
