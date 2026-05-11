package com.withbuddy.account.auth.controller;

import com.withbuddy.account.auth.docs.AuthControllerDocs;
import com.withbuddy.account.auth.dto.request.LoginRequest;
import com.withbuddy.account.auth.dto.response.LoginResponse;
import com.withbuddy.account.auth.service.AuthService;
import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        authService.logout(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
