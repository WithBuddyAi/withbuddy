package com.withbuddy.admin.metrics.service;

import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.admin.metrics.dto.response.AdminDashboardResponse;
import com.withbuddy.admin.metrics.dto.response.InternalAdminDashboardResponse;
import com.withbuddy.admin.metrics.dto.response.UnansweredQuestionPatternsResponse;
import com.withbuddy.admin.metrics.repository.AdminMetricsRepository;
import com.withbuddy.global.exception.ForbiddenException;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.infrastructure.ai.client.AiNoResultSummaryClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    @Mock
    private AdminMetricsRepository adminMetricsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiNoResultSummaryClient aiNoResultSummaryClient;

    @InjectMocks
    private AdminMetricsService adminMetricsService;

    @Test
    void returnsTopPatternsWithDefaultLimitAndSevenDayWindow() {
        JwtAuthenticationPrincipal principal = principal(1L, "WB0001");
        User serviceAdmin = serviceAdmin();
        when(userRepository.findById(1L)).thenReturn(Optional.of(serviceAdmin));
        when(adminMetricsRepository.findUnansweredQuestionPatterns(
                org.mockito.ArgumentMatchers.eq("WB0001"),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 5, 26)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 1)),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(pattern(
                "WB0001",
                "복지카드는 어떻게 신청하나요?",
                4L,
                3L,
                4L,
                0L,
                LocalDateTime.of(2026, 6, 1, 13, 0)
        ))));
        when(aiNoResultSummaryClient.summarize(
                "WB0001",
                List.of("복지카드는 어떻게 신청하나요?")
        )).thenReturn(new AiNoResultSummaryClient.NoResultSummaryResponse(
                "WB0001",
                1,
                "복지카드 신청 관련 문서가 부족합니다.",
                List.of("복지카드 신청 절차 문서를 보강하세요."),
                "A"
        ));

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
        assertThat(response.aiSummary()).isNotNull();
        assertThat(response.aiSummary().status()).isEqualTo("READY");
        assertThat(response.aiSummary().summary()).isEqualTo("복지카드 신청 관련 문서가 부족합니다.");
        assertThat(response.aiSummary().actions()).containsExactly("복지카드 신청 절차 문서를 보강하세요.");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMetricsRepository).findUnansweredQuestionPatterns(
                org.mockito.ArgumentMatchers.eq("WB0001"),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 5, 26)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 1)),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void adminIsForcedToOwnCompanyScope() {
        JwtAuthenticationPrincipal principal = principal(2L, "WB0001");
        User admin = activeAdmin("WB0001");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(adminMetricsRepository.findDocumentGapRateMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of());

        adminMetricsService.getDocumentGapRate(
                principal,
                null,
                LocalDate.of(2026, 6, 1)
        );

        verify(adminMetricsRepository).findDocumentGapRateMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1));
    }

    @Test
    void adminCannotRequestOtherCompanyMetrics() {
        JwtAuthenticationPrincipal principal = principal(2L, "WB0001");
        User admin = activeAdmin("WB0001");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminMetricsService.getDocumentGapRate(
                principal,
                "WB9999",
                LocalDate.of(2026, 6, 1)
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("다른 회사 지표는 조회할 수 없습니다.");
    }

    @Test
    void dashboardUsesP0Metrics() {
        JwtAuthenticationPrincipal principal = principal(2L, "WB0001");
        User admin = activeAdmin("WB0001");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(adminMetricsRepository.findRagExperienceRateMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of());
        when(adminMetricsRepository.findDocumentGapRateMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(documentGapMetric("WB0001", "WithBuddy", 10L, 2L)));
        when(adminMetricsRepository.findUnstartedUsersMetrics("WB0001", LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(unstartedUsersMetric("WB0001", "WithBuddy", 8L, 3L)));
        when(adminMetricsRepository.findUnansweredQuestionPatterns(
                org.mockito.ArgumentMatchers.eq("WB0001"),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 5, 26)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 1)),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        AdminDashboardResponse response = adminMetricsService.getDashboard(
                principal,
                null,
                LocalDate.of(2026, 6, 1),
                null
        );

        assertThat(response.metric()).isEqualTo("admin_dashboard");
        assertThat(response.documentGapRate().companies()).hasSize(1);
        assertThat(response.unstartedUsers().companies()).hasSize(1);
        assertThat(response.unansweredQuestionPatterns().limit()).isEqualTo(5);
        assertThat(response.unansweredQuestionPatterns().aiSummary().status()).isEqualTo("SKIPPED");
        verifyNoInteractions(aiNoResultSummaryClient);
    }

    @Test
    void internalDashboardReturnsServiceAdminOnlyMetrics() {
        JwtAuthenticationPrincipal principal = principal(1L, "WB0001");
        User serviceAdmin = serviceAdmin();
        when(userRepository.findById(1L)).thenReturn(Optional.of(serviceAdmin));
        when(adminMetricsRepository.findFirstInteractionRateMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(firstInteractionMetric("WB0001", "WithBuddy", 20L, 15L)));
        when(adminMetricsRepository.findRevisitRateMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(revisitMetric("WB0001", "WithBuddy", 12L, 7L)));
        when(adminMetricsRepository.findTtaMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(ttaMetric("WB0001", "WithBuddy", 11L, 8L, 13.5)));
        when(adminMetricsRepository.findTtaUnreachedMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(ttaUnreachedMetric("WB0001", "WithBuddy", 11L, 3L)));
        when(adminMetricsRepository.findUnansweredRateMetrics("WB0001", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(unansweredMetric("WB0001", "WithBuddy", 10L, 2L, 1L, 1L)));

        InternalAdminDashboardResponse response = adminMetricsService.getInternalDashboard(
                principal,
                "WB0001",
                LocalDate.of(2026, 6, 1)
        );

        assertThat(response.metric()).isEqualTo("internal_admin_dashboard");
        assertThat(response.nonRagRate().companies()).hasSize(1);
        assertThat(response.nonRagRate().companies().getFirst().nonRagAnswers()).isEqualTo(4L);
        assertThat(response.ttaUnreached().companies().getFirst().unreachedUsers()).isEqualTo(3L);
        assertThat(response.firstInteractionRate().companies().getFirst().firstInteractionUsers()).isEqualTo(15L);
    }

    @Test
    void internalDashboardRejectsCompanyAdmin() {
        JwtAuthenticationPrincipal principal = principal(2L, "WB0001");
        User admin = org.mockito.Mockito.mock(User.class);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminMetricsService.getInternalDashboard(
                principal,
                "WB0001",
                LocalDate.of(2026, 6, 1)
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("서비스 관리자 권한이 필요한 API입니다.");
    }

    private JwtAuthenticationPrincipal principal(Long userId, String companyCode) {
        return new JwtAuthenticationPrincipal(
                userId,
                "A001",
                "관리자",
                companyCode,
                "WithBuddy",
                "2026-06-01"
        );
    }

    private User serviceAdmin() {
        User user = org.mockito.Mockito.mock(User.class);
        org.mockito.Mockito.doReturn(UserRole.SERVICE_ADMIN).when(user).getRole();
        return user;
    }

    private User activeAdmin(String companyCode) {
        User user = org.mockito.Mockito.mock(User.class);
        Company company = org.mockito.Mockito.mock(Company.class);
        org.mockito.Mockito.doReturn(UserRole.ADMIN).when(user).getRole();
        org.mockito.Mockito.doReturn(true).when(user).isActiveAdmin();
        org.mockito.Mockito.doReturn(company).when(user).getCompany();
        org.mockito.Mockito.doReturn(companyCode).when(company).getCompanyCode();
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

    private AdminMetricsRepository.DocumentGapMetricProjection documentGapMetric(
            String companyCode,
            String companyName,
            Long answerableBotAnswers,
            Long noResultAnswers
    ) {
        return new AdminMetricsRepository.DocumentGapMetricProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public Long getAnswerableBotAnswers() {
                return answerableBotAnswers;
            }

            @Override
            public Long getNoResultAnswers() {
                return noResultAnswers;
            }
        };
    }

    private AdminMetricsRepository.UnstartedUsersMetricProjection unstartedUsersMetric(
            String companyCode,
            String companyName,
            Long activeNewUsers,
            Long unstartedUsers
    ) {
        return new AdminMetricsRepository.UnstartedUsersMetricProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public Long getActiveNewUsers() {
                return activeNewUsers;
            }

            @Override
            public Long getUnstartedUsers() {
                return unstartedUsers;
            }
        };
    }

    private AdminMetricsRepository.FirstInteractionMetricProjection firstInteractionMetric(
            String companyCode,
            String companyName,
            Long targetUsers,
            Long firstInteractionUsers
    ) {
        return new AdminMetricsRepository.FirstInteractionMetricProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public Long getTargetUsers() {
                return targetUsers;
            }

            @Override
            public Long getFirstInteractionUsers() {
                return firstInteractionUsers;
            }
        };
    }

    private AdminMetricsRepository.RevisitMetricProjection revisitMetric(
            String companyCode,
            String companyName,
            Long d0Users,
            Long revisitUsers
    ) {
        return new AdminMetricsRepository.RevisitMetricProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public Long getD0Users() {
                return d0Users;
            }

            @Override
            public Long getRevisitUsers() {
                return revisitUsers;
            }
        };
    }

    private AdminMetricsRepository.UnansweredMetricProjection unansweredMetric(
            String companyCode,
            String companyName,
            Long totalAiAnswers,
            Long noResultAnswers,
            Long outOfScopeAnswers,
            Long sensitiveAnswers
    ) {
        return new AdminMetricsRepository.UnansweredMetricProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public Long getTotalAiAnswers() {
                return totalAiAnswers;
            }

            @Override
            public Long getNoResultAnswers() {
                return noResultAnswers;
            }

            @Override
            public Long getOutOfScopeAnswers() {
                return outOfScopeAnswers;
            }

            @Override
            public Long getSensitiveAnswers() {
                return sensitiveAnswers;
            }
        };
    }

    private AdminMetricsRepository.TtaMetricProjection ttaMetric(
            String companyCode,
            String companyName,
            Long loggedInUsers,
            Long measuredUsers,
            Double averageTtaMinutes
    ) {
        return new AdminMetricsRepository.TtaMetricProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public Long getLoggedInUsers() {
                return loggedInUsers;
            }

            @Override
            public Long getMeasuredUsers() {
                return measuredUsers;
            }

            @Override
            public Double getAverageTtaMinutes() {
                return averageTtaMinutes;
            }
        };
    }

    private AdminMetricsRepository.TtaUnreachedMetricProjection ttaUnreachedMetric(
            String companyCode,
            String companyName,
            Long loggedInUsers,
            Long unreachedUsers
    ) {
        return new AdminMetricsRepository.TtaUnreachedMetricProjection() {
            @Override
            public String getCompanyCode() {
                return companyCode;
            }

            @Override
            public String getCompanyName() {
                return companyName;
            }

            @Override
            public Long getLoggedInUsers() {
                return loggedInUsers;
            }

            @Override
            public Long getUnreachedUsers() {
                return unreachedUsers;
            }
        };
    }
}
