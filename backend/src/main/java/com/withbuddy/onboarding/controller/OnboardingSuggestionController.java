package com.withbuddy.onboarding.controller;

import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.onboarding.dto.OnboardingSuggestionListResponse;
import com.withbuddy.onboarding.service.OnboardingSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/onboarding-suggestions")
public class OnboardingSuggestionController {

    private final OnboardingSuggestionService onboardingSuggestionService;
    private final JwtService jwtService;

    @GetMapping("/me")
    public ResponseEntity<OnboardingSuggestionListResponse> getMyOnboardingSuggestions(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        String token = extractToken(authorizationHeader);
        Long userId = jwtService.getUserId(token);

        OnboardingSuggestionListResponse response =
                onboardingSuggestionService.getMyOnboardingSuggestions(userId);

        return ResponseEntity.ok(response);
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authorization 헤더 형식이 올바르지 않습니다.");
        }
        return authorizationHeader.substring(7);
    }
}