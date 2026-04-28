package com.withbuddy.user.controller;

import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.user.dto.CreateUserRequest;
import com.withbuddy.user.dto.CreateUserResponse;
import com.withbuddy.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserControllerDocs {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Override
    public ResponseEntity<CreateUserResponse> createUser(
            Authentication authentication,
            @Valid @RequestBody CreateUserRequest request
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.status(201).body(userService.createUser(principal, request));
    }
}
