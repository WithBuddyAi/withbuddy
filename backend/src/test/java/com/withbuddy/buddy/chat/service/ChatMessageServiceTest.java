package com.withbuddy.buddy.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.buddy.chat.entity.ChatMessage;
import com.withbuddy.buddy.chat.entity.MessageType;
import com.withbuddy.buddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.buddy.chat.repository.ChatMessageRepository;
import com.withbuddy.buddy.chat.repository.UnansweredQuestionLogRepository;
import com.withbuddy.buddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.infrastructure.ai.client.AiQuestionEmbeddingClient;
import com.withbuddy.infrastructure.ai.client.AiStreamClient;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMessageDocumentRepository chatMessageDocumentRepository;
    @Mock
    private UnansweredQuestionLogRepository unansweredQuestionLogRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentFileRepository documentFileRepository;
    @Mock
    private AiStreamClient aiStreamClient;
    @Mock
    private AiQuestionEmbeddingClient aiQuestionEmbeddingClient;
    @Mock
    private RedisCacheService redisCacheService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Executor aiCallExecutor;
    @Mock
    private QuickQuestionCatalog quickQuestionCatalog;
    @Mock
    private OnboardingSuggestionRepository onboardingSuggestionRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    void savesSuggestionMessageWhenNoExistingRow() {
        Long userId = 1L;
        Long suggestionId = 7L;
        ChatMessage saved = ChatMessage.createSuggestionMessage(userId, suggestionId, "hello");

        when(chatMessageRepository.findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                userId, suggestionId, MessageType.suggestion
        )).thenReturn(Optional.empty());
        when(chatMessageRepository.save(org.mockito.ArgumentMatchers.any(ChatMessage.class))).thenReturn(saved);

        ChatMessage result = chatMessageService.saveSuggestionMessageIfNotExists(userId, suggestionId, "hello");

        assertThat(result).isSameAs(saved);
        verify(chatMessageRepository, times(1)).save(org.mockito.ArgumentMatchers.any(ChatMessage.class));
    }

    @Test
    void returnsExistingRowWhenUniqueConstraintRaceOccurs() {
        Long userId = 1L;
        Long suggestionId = 7L;
        ChatMessage existing = ChatMessage.createSuggestionMessage(userId, suggestionId, "hello");

        when(chatMessageRepository.findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                userId, suggestionId, MessageType.suggestion
        )).thenReturn(Optional.empty(), Optional.of(existing));
        when(chatMessageRepository.save(org.mockito.ArgumentMatchers.any(ChatMessage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        ChatMessage result = chatMessageService.saveSuggestionMessageIfNotExists(userId, suggestionId, "hello");

        assertThat(result).isSameAs(existing);
        verify(chatMessageRepository, times(2))
                .findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                        userId, suggestionId, MessageType.suggestion
                );
    }

    @Test
    void resolvesAnswerToMessageIdOnlyForNoResult() {
        Long result = chatMessageService.resolveAnswerToMessageId(MessageType.no_result, 201L);

        assertThat(result).isEqualTo(201L);
    }

    @Test
    void doesNotResolveAnswerToMessageIdForOtherMessageTypes() {
        Long result = chatMessageService.resolveAnswerToMessageId(MessageType.rag_answer, 201L);

        assertThat(result).isNull();
    }

    @Test
    void createsUnansweredQuestionLogForNoResult() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.no_result);

        assertThat(result).isTrue();
    }

    @Test
    void createsUnansweredQuestionLogForOutOfScope() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.out_of_scope);

        assertThat(result).isTrue();
    }

    @Test
    void createsUnansweredQuestionLogForSensitive() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.sensitive);

        assertThat(result).isTrue();
    }

    @Test
    void doesNotCreateUnansweredQuestionLogForRagAnswer() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.rag_answer);

        assertThat(result).isFalse();
    }
}
