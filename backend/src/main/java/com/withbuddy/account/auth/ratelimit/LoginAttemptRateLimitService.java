package com.withbuddy.account.auth.ratelimit;

import com.withbuddy.account.auth.exception.LoginRateLimitExceededException;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptRateLimitService {

    private final LoginRateLimitProperties properties;
    private final RedisCacheService redisCacheService;

    public void checkAllowed(String companyCode, String employeeNumber, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }

        enforceLock(RedisCacheKeys.loginLockAccount(companyCode, employeeNumber));
        if (clientIp != null && !clientIp.isBlank()) {
            enforceLock(RedisCacheKeys.loginLockIp(clientIp));
        }
    }

    public void recordCredentialFailure(String companyCode, String employeeNumber, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }

        long accountFailures = incrementWithWindow(RedisCacheKeys.loginFailureAccount(companyCode, employeeNumber));
        if (accountFailures > properties.getAccountMaxFailures()) {
            lockAndThrow(RedisCacheKeys.loginLockAccount(companyCode, employeeNumber));
        }

        if (clientIp != null && !clientIp.isBlank()) {
            long ipFailures = incrementWithWindow(RedisCacheKeys.loginFailureIp(clientIp));
            if (ipFailures > properties.getIpMaxFailures()) {
                lockAndThrow(RedisCacheKeys.loginLockIp(clientIp));
            }
        }
    }

    public void clearOnSuccess(String companyCode, String employeeNumber, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }

        redisCacheService.delete(RedisCacheKeys.loginFailureAccount(companyCode, employeeNumber));
        redisCacheService.delete(RedisCacheKeys.loginLockAccount(companyCode, employeeNumber));
    }

    private long incrementWithWindow(String key) {
        long failures = redisCacheService.increment(key);
        if (failures <= 1) {
            redisCacheService.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
        }
        return failures;
    }

    private void enforceLock(String lockKey) {
        long retryAfterSeconds = redisCacheService.getExpireSeconds(lockKey).orElse(0L);
        if (retryAfterSeconds > 0) {
            throw new LoginRateLimitExceededException(
                    "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.",
                    retryAfterSeconds
            );
        }
    }

    private void lockAndThrow(String lockKey) {
        redisCacheService.put(lockKey, "locked", Duration.ofSeconds(properties.getLockSeconds()));
        throw new LoginRateLimitExceededException(
                "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.",
                properties.getLockSeconds()
        );
    }
}
