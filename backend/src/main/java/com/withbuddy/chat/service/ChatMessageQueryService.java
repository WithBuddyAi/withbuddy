package com.withbuddy.chat.service;

import com.withbuddy.chat.dto.ChatMessageListResponse;
import com.withbuddy.chat.dto.ChatMessageResponse;
import com.withbuddy.chat.dto.ChatMessageStatusResponse;
import com.withbuddy.chat.entity.ChatMessage;
import com.withbuddy.chat.entity.ChatMessageDocument;
import com.withbuddy.chat.entity.MessageType;
import com.withbuddy.chat.entity.SenderType;
import com.withbuddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.chat.repository.ChatMessageRepository;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageQueryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageDocumentRepository chatMessageDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final RedisCacheService redisCacheService;

    public ChatMessageListResponse getMessages(Long userId, LocalDate date) {
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
                        documentFileMap
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

        return chatMessageRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        userId, start, end
                );
    }

    private Map<Long, List<Long>> resolveDocumentIdsByMessageId(List<ChatMessage> chatMessages) {
        if (chatMessages.isEmpty()) {
            return Map.of();
        }

        List<Long> messageIds = chatMessages.stream()
                .map(ChatMessage::getId)
                .toList();

        List<ChatMessageDocument> mappings =
                chatMessageDocumentRepository.findByChatMessageIdIn(messageIds);

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

    private List<ChatMessageResponse.RecommendedContactResponse> resolveRecommendedContacts(ChatMessage message) {
        if (message.getSenderType() != SenderType.BOT || message.getMessageType() != MessageType.no_result) {
            return Collections.emptyList();
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

    public ChatMessageStatusResponse getMessageStatus(Long userId, Long questionId) {
        chatMessageRepository.findById(questionId)
                .filter(msg -> userId.equals(msg.getUserId()))
                .orElseThrow(() -> new UnauthorizedException("해당 메시지에 접근 권한이 없습니다."));

        String status = redisCacheService.get(RedisCacheKeys.ragStatus(questionId))
                .orElse("TIMEOUT");

        if ("COMPLETED".equals(status)) {
            Optional<Long> answerIdOpt = redisCacheService.get(ragAnswerIdKey(questionId))
                    .map(Long::parseLong);
            if (answerIdOpt.isPresent()) {
                Long answerId = answerIdOpt.get();
                Optional<ChatMessage> answerOpt = chatMessageRepository.findById(answerId);
                if (answerOpt.isPresent()) {
                    List<Long> documentIds = chatMessageDocumentRepository
                            .findByChatMessageIdIn(List.of(answerId))
                            .stream()
                            .map(ChatMessageDocument::getDocumentId)
                            .toList();
                    Map<Long, Document> documentMap = resolveDocumentMap(documentIds);
                    Map<Long, DocumentFile> documentFileMap = resolveDocumentFileMap(documentIds);
                    ChatMessageResponse answerResponse = toResponse(answerOpt.get(), documentIds, documentMap, documentFileMap);
                    return new ChatMessageStatusResponse("COMPLETED", answerResponse);
                }
            }
            return new ChatMessageStatusResponse("COMPLETED", null);
        }

        return new ChatMessageStatusResponse(status, null);
    }

    private static String ragAnswerIdKey(Long questionId) {
        return "rag:answer:" + questionId;
    }
}
