package com.withbuddy.global.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

public final class RedisFailureLogSupport {

    private static final AtomicLong REDIS_FAILURE_COUNT = new AtomicLong(0);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private RedisFailureLogSupport() {
    }

    public static void logRedisFailure(Logger log, HttpServletRequest request, Throwable throwable) {
        long occurrence = REDIS_FAILURE_COUNT.incrementAndGet();
        String failedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
        UserIdentityHint userIdentityHint = resolveUserIdentityHint(request);

        log.error(
                "[REDIS_ERROR] failedAt={}, occurrence={}, method={}, path={}, userIdentifiable={}, userIdHint={}, clientIp={}, message={}",
                failedAt,
                occurrence,
                request.getMethod(),
                request.getRequestURI(),
                userIdentityHint.userIdentifiable(),
                userIdentityHint.userIdHint(),
                resolveClientIp(request),
                throwable.getMessage(),
                throwable
        );
    }

    private static UserIdentityHint resolveUserIdentityHint(HttpServletRequest request) {
        String principalUserId = extractUserIdFromSecurityContext();
        if (StringUtils.hasText(principalUserId)) {
            return new UserIdentityHint(true, principalUserId);
        }

        String tokenUserId = extractUserIdFromAuthorizationHeader(request.getHeader(AUTHORIZATION_HEADER));
        if (StringUtils.hasText(tokenUserId)) {
            return new UserIdentityHint(true, tokenUserId);
        }

        return new UserIdentityHint(false, "-");
    }

    private static String extractUserIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtAuthenticationPrincipal jwtPrincipal) {
            return String.valueOf(jwtPrincipal.userId());
        }

        return null;
    }

    private static String extractUserIdFromAuthorizationHeader(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }

        String trimmedHeader = authorizationHeader.trim();
        if (!trimmedHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }

        String token = trimmedHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return null;
        }

        String[] tokenParts = token.split("\\.");
        if (tokenParts.length < 2) {
            return null;
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokenParts[1]);
            JsonNode payload = OBJECT_MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode subject = payload.get("sub");
            if (subject == null || subject.isNull()) {
                return null;
            }

            String userIdHint = subject.asText();
            return StringUtils.hasText(userIdHint) ? userIdHint : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            String[] forwardedValues = forwardedFor.split(",");
            String firstIp = forwardedValues[0].trim();
            if (StringUtils.hasText(firstIp)) {
                return firstIp;
            }
        }

        return request.getRemoteAddr();
    }

    private record UserIdentityHint(boolean userIdentifiable, String userIdHint) {
    }
}
