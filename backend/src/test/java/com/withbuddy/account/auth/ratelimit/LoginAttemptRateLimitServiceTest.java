package com.withbuddy.account.auth.ratelimit;

import com.withbuddy.account.auth.exception.LoginRateLimitExceededException;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptRateLimitServiceTest {

    @Mock
    private RedisCacheService redisCacheService;

    private LoginRateLimitProperties properties;
    private LoginAttemptRateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new LoginRateLimitProperties();
        service = new LoginAttemptRateLimitService(properties, redisCacheService);
    }

    @Test
    void allowsLoginWhenNoActiveLockExists() {
        when(redisCacheService.getExpireSeconds(RedisCacheKeys.loginLockAccount("WB0001", "20260001")))
                .thenReturn(Optional.empty());
        when(redisCacheService.getExpireSeconds(RedisCacheKeys.loginLockIp("127.0.0.1")))
                .thenReturn(Optional.empty());

        assertThatCode(() -> service.checkAllowed("WB0001", "20260001", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksWhenAccountLockExists() {
        when(redisCacheService.getExpireSeconds(RedisCacheKeys.loginLockAccount("WB0001", "20260001")))
                .thenReturn(Optional.of(120L));

        assertThatThrownBy(() -> service.checkAllowed("WB0001", "20260001", "127.0.0.1"))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .hasMessage("로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void createsLockWhenAccountFailuresExceedThreshold() {
        when(redisCacheService.increment(RedisCacheKeys.loginFailureAccount("WB0001", "20260001")))
                .thenReturn(6L);

        assertThatThrownBy(() -> service.recordCredentialFailure("WB0001", "20260001", "127.0.0.1"))
                .isInstanceOf(LoginRateLimitExceededException.class);

        verify(redisCacheService).put(
                RedisCacheKeys.loginLockAccount("WB0001", "20260001"),
                "locked",
                Duration.ofSeconds(properties.getLockSeconds())
        );
    }

    @Test
    void createsIpLockWhenIpFailuresExceedThreshold() {
        when(redisCacheService.increment(RedisCacheKeys.loginFailureAccount("WB0001", "20260001")))
                .thenReturn(1L);
        when(redisCacheService.increment(RedisCacheKeys.loginFailureIp("127.0.0.1")))
                .thenReturn(21L);

        assertThatThrownBy(() -> service.recordCredentialFailure("WB0001", "20260001", "127.0.0.1"))
                .isInstanceOf(LoginRateLimitExceededException.class);

        verify(redisCacheService).put(
                RedisCacheKeys.loginLockIp("127.0.0.1"),
                "locked",
                Duration.ofSeconds(properties.getLockSeconds())
        );
    }

    @Test
    void clearsAccountAndIpStateOnSuccess() {
        service.clearOnSuccess("WB0001", "20260001", "127.0.0.1");

        verify(redisCacheService).delete(RedisCacheKeys.loginFailureAccount("WB0001", "20260001"));
        verify(redisCacheService).delete(RedisCacheKeys.loginLockAccount("WB0001", "20260001"));
        verify(redisCacheService, never()).delete(RedisCacheKeys.loginFailureIp("127.0.0.1"));
        verify(redisCacheService, never()).delete(RedisCacheKeys.loginLockIp("127.0.0.1"));
    }

    @Test
    void skipsRateLimitWhenDisabled() {
        properties.setEnabled(false);

        assertThatCode(() -> service.recordCredentialFailure("WB0001", "20260001", "127.0.0.1"))
                .doesNotThrowAnyException();

        verify(redisCacheService, never()).increment(RedisCacheKeys.loginFailureAccount("WB0001", "20260001"));
    }
}
