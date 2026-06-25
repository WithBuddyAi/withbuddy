package com.withbuddy.admin.user.service;

import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.company.repository.CompanyRepository;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.admin.activity.repository.UserActivityLogRepository;
import com.withbuddy.admin.user.dto.request.CreateUserRequest;
import com.withbuddy.admin.user.exception.InvalidHireDateRangeException;
import com.withbuddy.admin.user.repository.AdminUserRepository;
import com.withbuddy.buddy.chat.repository.ChatMessageRepository;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), KST);

    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserActivityLogRepository userActivityLogRepository;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                adminUserRepository,
                companyRepository,
                chatMessageRepository,
                userActivityLogRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void rejectsHireDateBeforeSixMonthWindow() {
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(activeAdmin()));

        assertThatThrownBy(() -> adminUserService.createUser(
                principal(),
                request(LocalDate.of(2025, 12, 16))
        ))
                .isInstanceOf(InvalidHireDateRangeException.class)
                .hasMessage("입사일은 오늘 기준 ±6개월 이내로 입력해 주세요.");

        verify(companyRepository, never()).findByCompanyCode(any());
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void rejectsHireDateAfterSixMonthWindow() {
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(activeAdmin()));

        assertThatThrownBy(() -> adminUserService.createUser(
                principal(),
                request(LocalDate.of(2026, 12, 18))
        ))
                .isInstanceOf(InvalidHireDateRangeException.class)
                .hasMessage("입사일은 오늘 기준 ±6개월 이내로 입력해 주세요.");

        verify(companyRepository, never()).findByCompanyCode(any());
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void allowsHireDateOnSixMonthWindowBoundaries() {
        Company company = company();
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(activeAdmin()));
        when(companyRepository.findByCompanyCode("WB0001")).thenReturn(Optional.of(company));
        when(adminUserRepository.existsByCompany_CompanyCodeAndEmployeeNumber("WB0001", "20260001"))
                .thenReturn(false);
        when(adminUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminUserService.createUser(principal(), request(LocalDate.of(2025, 12, 17)));
        adminUserService.createUser(principal(), request(LocalDate.of(2026, 12, 17)));

        verify(adminUserRepository, times(2)).save(any(User.class));
    }

    private JwtAuthenticationPrincipal principal() {
        return new JwtAuthenticationPrincipal(
                1L,
                "ADMIN001",
                "관리자",
                "WB0001",
                "테크 주식회사",
                "2026-01-01"
        );
    }

    private CreateUserRequest request(LocalDate hireDate) {
        CreateUserRequest request = new CreateUserRequest();
        ReflectionTestUtils.setField(request, "name", "김지원");
        ReflectionTestUtils.setField(request, "employeeNumber", "20260001");
        ReflectionTestUtils.setField(request, "department", "개발팀");
        ReflectionTestUtils.setField(request, "teamName", "백엔드팀");
        ReflectionTestUtils.setField(request, "hireDate", hireDate);
        return request;
    }

    private User activeAdmin() {
        return User.builder()
                .company(company())
                .name("관리자")
                .department("개발팀")
                .teamName("백엔드팀")
                .employeeNumber("ADMIN001")
                .hireDate(LocalDate.of(2026, 1, 1))
                .role(UserRole.ADMIN)
                .accountStatus(UserAccountStatus.ACTIVE)
                .build();
    }

    private Company company() {
        Company company = mock(Company.class);
        when(company.getCompanyCode()).thenReturn("WB0001");
        lenient().when(company.getName()).thenReturn("테크 주식회사");
        return company;
    }
}
