package com.withbuddy.global.security;

import com.withbuddy.global.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;

public final class AuthenticationPrincipalResolver {

    private AuthenticationPrincipalResolver() {
    }

    public static JwtAuthenticationPrincipal requireJwtPrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("인증 토큰이 없습니다.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtAuthenticationPrincipal jwtPrincipal)) {
            throw new UnauthorizedException("인증 토큰이 올바르지 않습니다.");
        }
        return jwtPrincipal;
    }
}
