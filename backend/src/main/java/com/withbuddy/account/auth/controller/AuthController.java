package com.withbuddy.account.auth.controller;

import com.withbuddy.account.auth.cookie.AuthCookieService;
import com.withbuddy.account.auth.docs.AuthControllerDocs;
import com.withbuddy.account.auth.dto.request.LoginRequest;
import com.withbuddy.account.auth.dto.response.LoginResponse;
import com.withbuddy.account.auth.dto.response.LoginUserResponse;
import com.withbuddy.account.auth.service.AuthService;
import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @Override
    @PostMapping("/login")
<<<<<<< Updated upstream
=======
<<<<<<< Updated upstream
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        return authService.login(loginRequest, resolveClientIp(request));
=======
>>>>>>> Stashed changes
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        AuthService.AuthenticatedSession session = authService.login(loginRequest, resolveClientIp(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.createAccessTokenCookie(request, session.accessToken()).toString())
<<<<<<< Updated upstream
                .body(new LoginResponse(session.user()));
=======
                .body(new LoginResponse(session.accessToken(), session.user()));
>>>>>>> Stashed changes
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<LoginUserResponse> me(Authentication authentication) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(authService.getCurrentUser(principal.userId()));
<<<<<<< Updated upstream
=======
>>>>>>> Stashed changes
>>>>>>> Stashed changes
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        authService.logout(principal.userId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.expireAccessTokenCookie(request).toString())
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
