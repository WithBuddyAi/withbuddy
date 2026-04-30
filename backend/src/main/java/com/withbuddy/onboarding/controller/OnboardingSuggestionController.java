package com.withbuddy.onboarding.controller;

import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.onboarding.dto.OnboardingSuggestionExposureResponse;
import com.withbuddy.onboarding.service.OnboardingSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/onboarding-suggestions")
public class OnboardingSuggestionController implements OnboardingSuggestionControllerDocs {

    private final OnboardingSuggestionService onboardingSuggestionService;

    @PostMapping("/me/exposure")
    public ResponseEntity<OnboardingSuggestionExposureResponse> exposeMyOnboardingSuggestion(
            Authentication authentication
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);

        OnboardingSuggestionExposureResponse response =
                onboardingSuggestionService.exposeMyOnboardingSuggestion(principal.userId());

        return ResponseEntity.ok(response);
    }
}
