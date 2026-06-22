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

        String sameSite = resolveSameSite(request);
        builder.httpOnly(authCookieProperties.isHttpOnly());
        builder.secure(resolveSecure(request, sameSite));
        builder.sameSite(sameSite);
        builder.path(authCookieProperties.getPath());

        if (StringUtils.hasText(authCookieProperties.getDomain())) {
            builder.domain(authCookieProperties.getDomain().trim());
        }

        return builder;
    }

    private String resolveSameSite(HttpServletRequest request) {
        if (StringUtils.hasText(authCookieProperties.getSameSite())) {
            return authCookieProperties.getSameSite().trim();
        }

        return isLocalRequest(request) ? "Lax" : "None";
    }

    private boolean resolveSecure(HttpServletRequest request, String sameSite) {
        if (authCookieProperties.getSecure() != null) {
            return authCookieProperties.getSecure();
        }

        if ("None".equalsIgnoreCase(sameSite) && !isLocalRequest(request)) {
            return true;
        }

        if (request.isSecure()) {
            return true;
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        if (isLoopbackHost(request.getServerName())) {
            return true;
        }

        String hostHeader = request.getHeader("Host");
        return StringUtils.hasText(hostHeader) && isLoopbackHost(stripPort(hostHeader));
    }

    private boolean isLoopbackHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }

        String normalized = stripPort(host).trim().toLowerCase();
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private String stripPort(String host) {
        if (!StringUtils.hasText(host)) {
            return host;
        }
        if (host.startsWith("[") && host.contains("]")) {
            return host.substring(0, host.indexOf(']') + 1);
        }
        int colonIndex = host.indexOf(':');
        return colonIndex >= 0 ? host.substring(0, colonIndex) : host;
    }
}
