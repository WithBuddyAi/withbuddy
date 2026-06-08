package com.withbuddy.admin.metrics.service;

import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.admin.metrics.dto.response.UnansweredQuestionPatternsResponse;
import com.withbuddy.admin.metrics.repository.AdminMetricsRepository;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    @Mock
    private AdminMetricsRepository adminMetricsRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminMetricsService adminMetricsService;

    @Test
    void returnsTopPatternsWithDefaultLimit() {
        JwtAuthenticationPrincipal principal = new JwtAuthenticationPrincipal(
                1L,
                "A001",
                "관리자",
                "WB0001",
                "WithBuddy",
                "2026-06-01"
        );
        User serviceAdmin = serviceAdmin();
        when(userRepository.findById(1L)).thenReturn(Optional.of(serviceAdmin));
        when(adminMetricsRepository.findUnansweredQuestionPatterns(
                org.mockito.ArgumentMatchers.eq("WB0001"),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 2)),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(pattern(
                "WB0001",
                "복지카드는 어떻게 신청하나요?",
                4L,
                3L,
                3L,
                1L,
                LocalDateTime.of(2026, 6, 1, 13, 0)
        ))));

        UnansweredQuestionPatternsResponse response = adminMetricsService.getUnansweredQuestionPatterns(
                principal,
                "WB0001",
                LocalDate.of(2026, 6, 1),
                null
        );

        assertThat(response.metric()).isEqualTo("unanswered_question_patterns");
        assertThat(response.limit()).isEqualTo(5);
        assertThat(response.patterns()).hasSize(1);
        assertThat(response.patterns().getFirst().questionContent()).isEqualTo("복지카드는 어떻게 신청하나요?");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMetricsRepository).findUnansweredQuestionPatterns(
                org.mockito.ArgumentMatchers.eq("WB0001"),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 2)),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void capsPatternLimitAtTwenty() {
        JwtAuthenticationPrincipal principal = new JwtAuthenticationPrincipal(
                1L,
                "A001",
                "관리자",
                "WB0001",
                "WithBuddy",
                "2026-06-01"
        );
        User serviceAdmin = serviceAdmin();
        when(userRepository.findById(1L)).thenReturn(Optional.of(serviceAdmin));
        when(adminMetricsRepository.findUnansweredQuestionPatterns(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 2)),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        UnansweredQuestionPatternsResponse response = adminMetricsService.getUnansweredQuestionPatterns(
                principal,
                null,
                LocalDate.of(2026, 6, 1),
                100
        );

        assertThat(response.limit()).isEqualTo(20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMetricsRepository).findUnansweredQuestionPatterns(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 2)),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    private User serviceAdmin() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getRole()).thenReturn(com.withbuddy.account.user.entity.UserRole.SERVICE_ADMIN);
        return user;
    }

    private AdminMetricsRepository.UnansweredQuestionPatternProjection pattern(
            String companyCode,
            String questionContent,
            Long totalCount,
            Long uniqueUsers,
            Long noResultCount,
            Long outOfScopeCount,
            LocalDateTime latestOccurredAt
    ) {
        return new AdminMetricsRepository.UnansweredQuestionPatternProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getQuestionContent() {
                return questionContent;
            }

            @Override
            public Long getTotalCount() {
                return totalCount;
            }

            @Override
            public Long getUniqueUsers() {
                return uniqueUsers;
            }

            @Override
            public Long getNoResultCount() {
                return noResultCount;
            }

            @Override
            public Long getOutOfScopeCount() {
                return outOfScopeCount;
            }

            @Override
            public LocalDateTime getLatestOccurredAt() {
                return latestOccurredAt;
            }
        };
    }
}
