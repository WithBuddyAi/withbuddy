package com.withbuddy.account.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.admin.activity.service.UserActivityLogService;
import com.withbuddy.admin.activity.log.RedisActivityLogService;
import com.withbuddy.admin.activity.log.RmqActivityLogService;
import com.withbuddy.admin.activity.entity.EventTarget;
import com.withbuddy.admin.activity.entity.EventType;
import com.withbuddy.account.auth.dto.request.LoginRequest;
import com.withbuddy.account.auth.dto.response.LoginUserResponse;
import com.withbuddy.account.auth.exception.LoginFailedException;
import com.withbuddy.account.auth.ratelimit.LoginAttemptRateLimitService;
import com.withbuddy.account.auth.turnstile.TurnstileVerificationService;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.user.service.UserLifecycleStatusResolver;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserActivityLogService userActivityLogService;
    private final RedisActivityLogService redisActivityLogService;
    private final RmqActivityLogService rmqActivityLogService;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;
    private final TurnstileVerificationService turnstileVerificationService;
    private final LoginAttemptRateLimitService loginAttemptRateLimitService;

    @Transactional
    public AuthenticatedSession login(LoginRequest request, String clientIp) {
        String normalizedCompanyCode = normalizeCompanyCode(request.getCompanyCode());
        String normalizedEmployeeNumber = normalizeValue(request.getEmployeeNumber());
        String normalizedName = normalizeValue(request.getName());
        loginAttemptRateLimitService.checkAllowed(normalizedCompanyCode, normalizedEmployeeNumber, clientIp);
        turnstileVerificationService.verifyLoginToken(request.getTurnstileToken(), clientIp);

        User user;
        try {
            user = userRepository.findByCompany_CompanyCodeAndNameAndEmployeeNumber(
                    normalizedCompanyCode,
                    normalizedName,
                    normalizedEmployeeNumber
            ).orElseThrow(() -> new LoginFailedException("입력하신 정보를 다시 확인해 주세요."));
        } catch (LoginFailedException e) {
            loginAttemptRateLimitService.recordCredentialFailure(normalizedCompanyCode, normalizedEmployeeNumber, clientIp);
            throw e;
        }

        UserAccountStatus currentAccountStatus = UserLifecycleStatusResolver.resolve(user, KST_CLOCK);
        if (user.getRole() == UserRole.USER && user.getAccountStatus() != currentAccountStatus) {
            user.updateAccountStatus(currentAccountStatus);
        }

        loginAttemptRateLimitService.clearOnSuccess(normalizedCompanyCode, normalizedEmployeeNumber, clientIp);

        redisCacheService.get(RedisCacheKeys.userSession(user.getId()))
                .ifPresent(oldToken -> redisCacheService.delete(RedisCacheKeys.sessionToken(oldToken)));

        String accessToken = jwtService.generateAccessToken(user);
        redisCacheService.put(
                RedisCacheKeys.sessionToken(accessToken),
                String.valueOf(user.getId()),
                RedisCacheTtl.SESSION_TOKEN
        );
        redisCacheService.put(
                RedisCacheKeys.userSession(user.getId()),
                accessToken,
                RedisCacheTtl.SESSION_TOKEN
        );

        if (user.getRole() != UserRole.USER || currentAccountStatus != UserAccountStatus.INACTIVE) {
            userActivityLogService.saveLoginSessionStart(user.getId());
            redisActivityLogService.append(user.getId(), EventType.SESSION_START, EventTarget.LOGIN);
            rmqActivityLogService.publish(user.getId(), EventType.SESSION_START, EventTarget.LOGIN);
        }

        LoginUserResponse userResponse = buildUserResponse(user, currentAccountStatus);
        cacheUserProfile(user.getId(), userResponse);

        return new AuthenticatedSession(accessToken, userResponse);
    }

    @Transactional
    public LoginUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자 정보를 찾을 수 없습니다."));

        UserAccountStatus currentAccountStatus = UserLifecycleStatusResolver.resolve(user, KST_CLOCK);
        if (user.getRole() == UserRole.USER && user.getAccountStatus() != currentAccountStatus) {
            user.updateAccountStatus(currentAccountStatus);
        }

        LoginUserResponse userResponse = buildUserResponse(user, currentAccountStatus);
        cacheUserProfile(userId, userResponse);
        return userResponse;
    }

    private UserAccountStatus resolveResponseAccountStatus(User user, UserAccountStatus currentAccountStatus) {
        if (user.getRole() == UserRole.USER) {
            return currentAccountStatus;
        }
        if (user.getRole() == UserRole.ADMIN) {
            return user.getAccountStatus();
        }
        return null;
    }

    public void logout(Long userId) {
        String token = redisCacheService.get(RedisCacheKeys.userSession(userId)).orElse(null);
        redisCacheService.delete(RedisCacheKeys.userSession(userId));
        redisCacheService.delete(RedisCacheKeys.userProfile(userId));
        if (token != null) {
            redisCacheService.delete(RedisCacheKeys.sessionToken(token));
        }
    }

    private void cacheUserProfile(Long userId, LoginUserResponse userResponse) {
        try {
            redisCacheService.put(
                    RedisCacheKeys.userProfile(userId),
                    objectMapper.writeValueAsString(userResponse),
                    RedisCacheTtl.USER_PROFILE
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("사용자 프로필 캐시 직렬화에 실패했습니다.", e);
        }
    }

    private LoginUserResponse buildUserResponse(User user, UserAccountStatus currentAccountStatus) {
        return new LoginUserResponse(
                user.getId(),
                user.getCompany().getCompanyCode(),
                user.getCompany().getName(),
                user.getEmployeeNumber(),
                user.getName(),
                user.getDepartment(),
                user.getTeamName(),
                user.getRole(),
                resolveResponseAccountStatus(user, currentAccountStatus),
                user.getHireDate()
        );
    }

    private String normalizeCompanyCode(String value) {
        return normalizeValue(value).toUpperCase(Locale.ROOT);
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    public record AuthenticatedSession(String accessToken, LoginUserResponse user) {
    }
}
