package com.withbuddy.account.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.account.auth.dto.request.LoginRequest;
import com.withbuddy.account.auth.exception.LoginFailedException;
import com.withbuddy.account.auth.exception.LoginUserNotFoundException;
import com.withbuddy.account.auth.ratelimit.LoginAttemptRateLimitService;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.auth.turnstile.TurnstileVerificationService;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.admin.activity.log.RedisActivityLogService;
import com.withbuddy.admin.activity.log.RmqActivityLogService;
import com.withbuddy.admin.activity.service.UserActivityLogService;
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 7, 10).atStartOfDay(KST).toInstant(),
            KST
    );

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserActivityLogService userActivityLogService;
    @Mock
    private RedisActivityLogService redisActivityLogService;
    @Mock
    private RmqActivityLogService rmqActivityLogService;
    @Mock
    private RedisCacheService redisCacheService;
    @Mock
    private TurnstileVerificationService turnstileVerificationService;
    @Mock
    private LoginAttemptRateLimitService loginAttemptRateLimitService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                jwtService,
                userActivityLogService,
                redisActivityLogService,
                rmqActivityLogService,
                redisCacheService,
                new ObjectMapper().findAndRegisterModules(),
                turnstileVerificationService,
                loginAttemptRateLimitService,
                FIXED_CLOCK
        );
    }

    @Test
    void loginThrowsDedicatedExceptionWhenAccountDoesNotExist() {
        LoginRequest request = request("WB0003", "20261005", "김사부");

        when(userRepository.findByCompany_CompanyCodeAndNameAndEmployeeNumber("WB0003", "김사부", "20261005"))
                .thenReturn(Optional.empty());
        when(userRepository.findByCompany_CompanyCodeAndEmployeeNumber("WB0003", "20261005"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(LoginUserNotFoundException.class)
                .hasMessage("존재하지 않는 계정입니다.");

        verify(loginAttemptRateLimitService).recordCredentialFailure("WB0003", "20261005", "127.0.0.1");
        verify(loginAttemptRateLimitService, never()).clearOnSuccess(anyString(), anyString(), anyString());
    }

    @Test
    void loginReturnsPreForUserWithinPreOnboardingWindow() {
        LoginRequest request = request("WB0003", "20261005", "김사부");
        User user = user("WB0003", "디지털헬스", "김사부", "20261005", LocalDate.of(2026, 7, 14));

        when(userRepository.findByCompany_CompanyCodeAndNameAndEmployeeNumber("WB0003", "김사부", "20261005"))
                .thenReturn(Optional.of(user));
        when(redisCacheService.get(anyString())).thenReturn(Optional.empty());
        when(jwtService.generateAccessToken(user)).thenReturn("token");

        AuthService.AuthenticatedSession session = authService.login(request, "127.0.0.1");

        assertThat(session.user().getAccountStatus()).isEqualTo(UserAccountStatus.PRE);
        verify(loginAttemptRateLimitService).clearOnSuccess("WB0003", "20261005", "127.0.0.1");
    }

    private LoginRequest request(String companyCode, String employeeNumber, String name) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "companyCode", companyCode);
        ReflectionTestUtils.setField(request, "employeeNumber", employeeNumber);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "turnstileToken", "token");
        return request;
    }

    private User user(String companyCode, String companyName, String name, String employeeNumber, LocalDate hireDate) {
        Company company = mock(Company.class);
        lenient().when(company.getCompanyCode()).thenReturn(companyCode);
        lenient().when(company.getName()).thenReturn(companyName);
        lenient().when(company.getProbationPeriod()).thenReturn(90);
        User user = User.builder()
                .company(company)
                .name(name)
                .department("디지털헬스사업팀")
                .teamName("디지털헬스사업팀")
                .employeeNumber(employeeNumber)
                .hireDate(hireDate)
                .role(UserRole.USER)
                .accountStatus(UserAccountStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", 103L);
        return user;
    }
}
