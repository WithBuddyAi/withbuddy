package com.withbuddy.onboarding.service;

import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.buddy.chat.entity.ChatMessage;
import com.withbuddy.buddy.chat.service.ChatMessageService;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.buddy.onboarding.dto.response.OnboardingSuggestionExposureResponse;
import com.withbuddy.buddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.buddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.buddy.onboarding.service.OnboardingSuggestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingSuggestionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OnboardingSuggestionRepository onboardingSuggestionRepository;

    @Mock
    private ChatMessageService chatMessageService;

    @Test
    void createsSuggestionExposureWhenTargetExists() {
        OnboardingSuggestionService service = new OnboardingSuggestionService(
                userRepository,
                onboardingSuggestionRepository,
                chatMessageService
        );

        User user = mock(User.class);
        Company company = mock(Company.class);
        OnboardingSuggestion suggestion = mock(OnboardingSuggestion.class);
        ChatMessage savedMessage = mock(ChatMessage.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getHireDate()).thenReturn(LocalDate.now().minusDays(1));
        when(user.getName()).thenReturn("Tester");
        when(user.getCompany()).thenReturn(company);
        when(company.getName()).thenReturn("WithBuddy");
        when(onboardingSuggestionRepository.findTopByDayOffset(1)).thenReturn(Optional.of(suggestion));
        when(suggestion.getId()).thenReturn(4L);
        when(suggestion.getContent()).thenReturn("{N}day onboarding guide");
        when(chatMessageService.findSuggestionMessage(1L, 4L)).thenReturn(null);
        when(chatMessageService.saveSuggestionMessageIfNotExists(1L, 4L, "2day onboarding guide")).thenReturn(savedMessage);
        when(savedMessage.getId()).thenReturn(301L);

        OnboardingSuggestionExposureResponse response = service.exposeMyOnboardingSuggestion(1L);

        assertThat(response.isCreated()).isTrue();
        assertThat(response.getMessageId()).isEqualTo(301L);
        assertThat(response.getSuggestionId()).isEqualTo(4L);
    }

    @Test
    void returnsExistingMessageWhenSuggestionAlreadyExists() {
        OnboardingSuggestionService service = new OnboardingSuggestionService(
                userRepository,
                onboardingSuggestionRepository,
                chatMessageService
        );

        User user = mock(User.class);
        OnboardingSuggestion suggestion = mock(OnboardingSuggestion.class);
        ChatMessage existing = mock(ChatMessage.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getHireDate()).thenReturn(LocalDate.now().minusDays(1));
        when(onboardingSuggestionRepository.findTopByDayOffset(1)).thenReturn(Optional.of(suggestion));
        when(suggestion.getId()).thenReturn(4L);
        when(chatMessageService.findSuggestionMessage(1L, 4L)).thenReturn(existing);
        when(existing.getId()).thenReturn(301L);

        OnboardingSuggestionExposureResponse response = service.exposeMyOnboardingSuggestion(1L);

        assertThat(response.isCreated()).isFalse();
        assertThat(response.getMessageId()).isEqualTo(301L);
        verify(chatMessageService, never()).saveSuggestionMessageIfNotExists(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsNoExposureWhenSuggestionDoesNotExist() {
        OnboardingSuggestionService service = new OnboardingSuggestionService(
                userRepository,
                onboardingSuggestionRepository,
                chatMessageService
        );

        User user = mock(User.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getHireDate()).thenReturn(LocalDate.now().minusDays(100));
        when(onboardingSuggestionRepository.findTopByDayOffset(100)).thenReturn(Optional.empty());

        OnboardingSuggestionExposureResponse response = service.exposeMyOnboardingSuggestion(1L);

        assertThat(response.isCreated()).isFalse();
        assertThat(response.getMessageId()).isNull();
        assertThat(response.getSuggestionId()).isNull();
    }
}
