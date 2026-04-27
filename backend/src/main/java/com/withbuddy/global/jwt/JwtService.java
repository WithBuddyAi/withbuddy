package com.withbuddy.global.jwt;

import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import com.withbuddy.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private Long accessExpiration;

    private final RedisCacheService redisCacheService;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = resolveKeyBytes(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveKeyBytes(String secretValue) {
        try {
            return Decoders.BASE64.decode(secretValue);
        } catch (Exception ignored) {
            byte[] raw = secretValue.getBytes(StandardCharsets.UTF_8);
            if (raw.length >= 32) {
                return raw;
            }

            try {
                return MessageDigest.getInstance("SHA-256").digest(raw);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm is not available", e);
            }
        }
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("companyCode", user.getCompany().getCompanyCode())
                .claim("employeeNumber", user.getEmployeeNumber())
                .claim("name", user.getName())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            validateAndTouchSession(token);
            return true;
        } catch (JwtException | SessionNotActiveException | UnauthorizedException | TokenMissingException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return validateAndTouchSession(token);
    }

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public String getCompanyCode(String token) {
        return getClaims(token).get("companyCode", String.class);
    }

    public String getName(String token) {
        return getClaims(token).get("name", String.class);
    }

    public String extractBearerToken(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            throw new TokenMissingException("인증 토큰이 누락되었습니다.");
        }

        String trimmedBearerToken = bearerToken.trim();

        if (!trimmedBearerToken.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedException("Authorization 헤더 형식이 올바르지 않습니다.");
        }

        String token = trimmedBearerToken.substring(7).trim();

        if (!StringUtils.hasText(token)) {
            throw new TokenMissingException("인증 토큰이 누락되었습니다.");
        }

        return token;
    }

    private Claims validateAndTouchSession(String token) {
        Claims claims = parseValidClaims(token);
        Long userId = parseUserId(claims);

        String activeToken = redisCacheService.get(RedisCacheKeys.userSession(userId))
                .orElseThrow(() -> new SessionNotActiveException("활성 세션이 만료되었거나 존재하지 않습니다."));

        if (!Objects.equals(activeToken, token)) {
            throw new SessionNotActiveException("다른 기기에서 다시 로그인되어 현재 세션이 종료되었습니다.");
        }

        redisCacheService.put(
                RedisCacheKeys.userSession(userId),
                token,
                RedisCacheTtl.SESSION_TOKEN
        );

        return claims;
    }

    private Claims parseValidClaims(String token) {
        if (!StringUtils.hasText(token)) {
            throw new TokenMissingException("인증 토큰이 누락되었습니다.");
        }

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Long parseUserId(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            throw new UnauthorizedException("토큰 사용자 정보를 해석할 수 없습니다.");
        }
    }
}