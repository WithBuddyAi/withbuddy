package com.withbuddy.auth.service;

import com.withbuddy.activity.service.UserActivityLogService;
import com.withbuddy.auth.dto.request.LoginRequest;
import com.withbuddy.auth.dto.response.LoginResponse;
import com.withbuddy.auth.dto.response.LoginUserResponse;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.user.entity.User;
import com.withbuddy.auth.exception.LoginFailedException;
import com.withbuddy.auth.repository.UserRepository;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.jwt.JwtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserActivityLogService userActivityLogService;
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
        ).orElseThrow(() -> new LoginFailedException("입력하신 정보를 다시 확인해 주세요"));

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

        LoginUserResponse userResponse = new LoginUserResponse(
                user.getId(),
                user.getCompany().getCompanyCode(),
                user.getCompany().getName(),
                user.getEmployeeNumber(),
                user.getName(),
                user.getHireDate()
        );
        cacheUserProfile(user.getId(), userResponse);

        return new LoginResponse(accessToken, userResponse);
    }

    public void logout(String bearerToken) {
        String token = extractToken(bearerToken);
        Long userId = jwtService.getUserId(token);
        redisCacheService.delete(RedisCacheKeys.userSession(userId));
        redisCacheService.delete(RedisCacheKeys.sessionToken(token));
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

    private String extractToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authorization 헤더 형식이 올바르지 않습니다.");
        }
        return bearerToken.substring(7);
    }
}
