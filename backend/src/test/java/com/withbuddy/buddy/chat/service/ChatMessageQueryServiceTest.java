package com.withbuddy.buddy.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.buddy.chat.dto.response.ChatMessageListResponse;
import com.withbuddy.buddy.chat.entity.ChatMessage;
import com.withbuddy.buddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.buddy.chat.repository.ChatMessageRepository;
import com.withbuddy.buddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import com.withbuddy.storage.service.DocumentDownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMessageDocumentRepository chatMessageDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentFileRepository documentFileRepository;
    @Mock
    private DocumentDownloadService documentDownloadService;
    @Mock
    private QuickQuestionCatalog quickQuestionCatalog;
    @Mock
    private OnboardingSuggestionRepository onboardingSuggestionRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void hidesSuggestionQuickTapsForReadOnlyUser() {
        ChatMessageQueryService service = new ChatMessageQueryService(
                chatMessageRepository,
                chatMessageDocumentRepository,
                documentRepository,
                documentFileRepository,
                documentDownloadService,
                new ObjectMapper(),
                quickQuestionCatalog,
                onboardingSuggestionRepository,
                userRepository
        );
        Long userId = 1L;
        ChatMessage suggestionMessage = ChatMessage.createSuggestionMessage(userId, 10L, "suggestion");
        ReflectionTestUtils.setField(suggestionMessage, "createdAt", LocalDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user(UserRole.READ_ONLY)));
        when(chatMessageRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(suggestionMessage));
        when(chatMessageDocumentRepository.findByChatMessageIdIn(anyList())).thenReturn(List.of());

        ChatMessageListResponse response = service.getMessages(userId, null);

        assertThat(response.getMessages()).hasSize(1);
        assertThat(response.getMessages().getFirst().getQuickTaps()).isEmpty();
        verify(onboardingSuggestionRepository, never()).findById(10L);
    }

    private User user(UserRole role) {
        return User.builder()
                .name("tester")
                .department("-")
                .teamName("-")
                .employeeNumber("E001")
                .hireDate(LocalDate.now())
                .role(role)
                .build();
    }
}
