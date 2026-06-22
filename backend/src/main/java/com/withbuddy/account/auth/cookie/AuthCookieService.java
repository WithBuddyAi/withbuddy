package com.withbuddy.account.auth.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
public class AuthCookieService {

    private final AuthCookieProperties authCookieProperties;
    private final long accessExpirationMillis;

    public AuthCookieService(
            AuthCookieProperties authCookieProperties,
            @Value("${jwt.access-expiration}") long accessExpirationMillis
    ) {
        this.authCookieProperties = authCookieProperties;
        this.accessExpirationMillis = accessExpirationMillis;
    }

    public ResponseCookie createAccessTokenCookie(HttpServletRequest request, String token) {
        return baseCookieBuilder(request)
                .value(token)
                .maxAge(Duration.ofMillis(accessExpirationMillis))
                .build();
    }

    public ResponseCookie expireAccessTokenCookie(HttpServletRequest request) {
        return baseCookieBuilder(request)
                .value("")
                .maxAge(Duration.ZERO)
                .build();
    }

    public Optional<String> resolveAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> AuthCookieNames.ACCESS_TOKEN.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookieBuilder(HttpServletRequest request) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(
                AuthCookieNames.ACCESS_TOKEN,
                ""
        );

        builder.httpOnly(authCookieProperties.isHttpOnly());
        builder.secure(resolveSecure(request));
        builder.sameSite(authCookieProperties.getSameSite());
        builder.path(authCookieProperties.getPath());

        if (StringUtils.hasText(authCookieProperties.getDomain())) {
            builder.domain(authCookieProperties.getDomain().trim());
        }

        return builder;
    }

    private boolean resolveSecure(HttpServletRequest request) {
        if (authCookieProperties.getSecure() != null) {
            return authCookieProperties.getSecure();
        }

        if (request.isSecure()) {
            return true;
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }
}
