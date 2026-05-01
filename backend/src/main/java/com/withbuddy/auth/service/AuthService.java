package com.withbuddy.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.activity.service.UserActivityLogService;
import com.withbuddy.activity.log.RedisActivityLogService;
import com.withbuddy.activity.log.RmqActivityLogService;
import com.withbuddy.activity.entity.EventTarget;
import com.withbuddy.activity.entity.EventType;
import com.withbuddy.auth.dto.request.LoginRequest;
import com.withbuddy.auth.dto.response.LoginResponse;
import com.withbuddy.auth.dto.response.LoginUserResponse;
import com.withbuddy.auth.exception.LoginFailedException;
import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserActivityLogService userActivityLogService;
    private final RedisActivityLogService redisActivityLogService;
    private final RmqActivityLogService rmqActivityLogService;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    public LoginResponse login(LoginRequest request) {
        String normalizedCompanyCode = normalizeCompanyCode(request.getCompanyCode());
        String normalizedEmployeeNumber = normalizeValue(request.getEmployeeNumber());
        String normalizedName = normalizeValue(request.getName());

        User user = userRepository.findByCompany_CompanyCodeAndNameAndEmployeeNumber(
                normalizedCompanyCode,
                normalizedName,
                normalizedEmployeeNumber
        ).orElseThrow(() -> new LoginFailedException("입력하신 정보를 다시 확인해 주세요."));

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

        userActivityLogService.saveLoginSessionStart(user.getId());
        redisActivityLogService.append(user.getId(), EventType.SESSION_START, EventTarget.LOGIN);
        rmqActivityLogService.publish(user.getId(), EventType.SESSION_START, EventTarget.LOGIN);

        LoginUserResponse userResponse = new LoginUserResponse(
                user.getId(),
                user.getCompany().getCompanyCode(),
                user.getRole(),
                user.getCompany().getName(),
                user.getEmployeeNumber(),
                user.getName(),
                user.getHireDate()
        );
        cacheUserProfile(user.getId(), userResponse);

        return new LoginResponse(accessToken, userResponse);
    }

    public void logout(Long userId) {
        String token = redisCacheService.get(RedisCacheKeys.userSession(userId)).orElse(null);
        redisCacheService.delete(RedisCacheKeys.userSession(userId));
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

    private String normalizeCompanyCode(String value) {
        return normalizeValue(value).toUpperCase(Locale.ROOT);
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
