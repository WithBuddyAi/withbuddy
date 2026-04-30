package com.withbuddy.onboarding.service;

import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.chat.service.ChatMessageService;
import com.withbuddy.chat.service.QuickQuestionCatalog;
import com.withbuddy.company.entity.Company;
import com.withbuddy.infrastructure.mq.NudgeEventPublisher;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.onboarding.dto.OnboardingSuggestionItemResponse;
import com.withbuddy.onboarding.dto.OnboardingSuggestionListResponse;
import com.withbuddy.onboarding.entity.OnboardingSuggestion;
import com.withbuddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingSuggestionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private UserRepository userRepository;

    @Mock
    private OnboardingSuggestionRepository onboardingSuggestionRepository;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private NudgeEventPublisher nudgeEventPublisher;

    @Test
    void returnsSuggestionWithQuickTapsForExactDayOffset() {
        QuickQuestionCatalog quickQuestionCatalog = new QuickQuestionCatalog();
        OnboardingSuggestionService onboardingSuggestionService = new OnboardingSuggestionService(
                userRepository,
                onboardingSuggestionRepository,
                chatMessageService,
                quickQuestionCatalog,
                redisCacheService,
                nudgeEventPublisher
        );

        User user = org.mockito.Mockito.mock(User.class);
        Company company = org.mockito.Mockito.mock(Company.class);
        OnboardingSuggestion suggestion = org.mockito.Mockito.mock(OnboardingSuggestion.class);
        LocalDate hireDate = LocalDate.now(KST);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getHireDate()).thenReturn(hireDate);
        when(user.getName()).thenReturn("김지원");
        when(user.getCompany()).thenReturn(company);
        when(company.getName()).thenReturn("위드버디");
        when(onboardingSuggestionRepository.findTopByDayOffset(0)).thenReturn(Optional.of(suggestion));
        when(suggestion.getId()).thenReturn(99L);
        when(suggestion.getTitle()).thenReturn("입사 당일");
        when(suggestion.getContent()).thenReturn("{N}일차 온보딩 안내");
        when(suggestion.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 3, 20, 9, 0));
        when(redisCacheService.putIfAbsent(any(), eq("1"), any())).thenReturn(true);

        OnboardingSuggestionListResponse response = onboardingSuggestionService.getMyOnboardingSuggestions(1L);

        assertThat(response.getSuggestions()).hasSize(1);

        OnboardingSuggestionItemResponse item = response.getSuggestions().getFirst();
        assertThat(item.getDayOffset()).isEqualTo(0);
        assertThat(item.getContent()).isEqualTo("1일차 온보딩 안내");
        assertThat(item.getQuickTaps()).hasSize(3);
        assertThat(item.getQuickTaps())
                .extracting("eventTarget")
                .containsExactly("QUICK_TAP_IT_SETUP", "QUICK_TAP_EQUIPMENT", "QUICK_TAP_LEAVE_START");

        verify(nudgeEventPublisher).publish(any());
        verify(chatMessageService, never()).saveSuggestionMessageIfNotExists(any(), any(), any());
    }

    @Test
    void returnsEmptyWhenNoSuggestionExistsForDayOffset() {
        QuickQuestionCatalog quickQuestionCatalog = new QuickQuestionCatalog();
        OnboardingSuggestionService onboardingSuggestionService = new OnboardingSuggestionService(
                userRepository,
                onboardingSuggestionRepository,
                chatMessageService,
                quickQuestionCatalog,
                redisCacheService,
                nudgeEventPublisher
        );

        User user = org.mockito.Mockito.mock(User.class);
        LocalDate hireDate = LocalDate.now(KST).minusDays(1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getHireDate()).thenReturn(hireDate);
        when(onboardingSuggestionRepository.findTopByDayOffset(1)).thenReturn(Optional.empty());

        OnboardingSuggestionListResponse response = onboardingSuggestionService.getMyOnboardingSuggestions(1L);

        assertThat(response.getSuggestions()).isEmpty();
        verify(chatMessageService, never()).saveSuggestionMessageIfNotExists(any(), any(), any());
        verify(nudgeEventPublisher, never()).publish(any());
    }

    @Test
    void returnsEmptyWhenSuggestionAlreadyShownToday() {
        QuickQuestionCatalog quickQuestionCatalog = new QuickQuestionCatalog();
        OnboardingSuggestionService onboardingSuggestionService = new OnboardingSuggestionService(
                userRepository,
                onboardingSuggestionRepository,
                chatMessageService,
                quickQuestionCatalog,
                redisCacheService,
                nudgeEventPublisher
        );

        User user = org.mockito.Mockito.mock(User.class);
        Company company = org.mockito.Mockito.mock(Company.class);
        OnboardingSuggestion suggestion = org.mockito.Mockito.mock(OnboardingSuggestion.class);
        LocalDate hireDate = LocalDate.now(KST);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getHireDate()).thenReturn(hireDate);
        when(user.getName()).thenReturn("김지원");
        when(user.getCompany()).thenReturn(company);
        when(company.getName()).thenReturn("위드버디");
        when(onboardingSuggestionRepository.findTopByDayOffset(0)).thenReturn(Optional.of(suggestion));
        when(redisCacheService.putIfAbsent(any(), eq("1"), any())).thenReturn(false);

        OnboardingSuggestionListResponse response = onboardingSuggestionService.getMyOnboardingSuggestions(1L);

        assertThat(response.getSuggestions()).isEmpty();
        verify(nudgeEventPublisher, never()).publish(any());
        verify(chatMessageService, never()).saveSuggestionMessageIfNotExists(any(), any(), any());
    }
}
