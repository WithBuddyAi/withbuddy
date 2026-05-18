package com.withbuddy.account.user.controller;

import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.account.user.docs.UserControllerDocs;
import com.withbuddy.account.user.dto.request.CreateUserRequest;
import com.withbuddy.account.user.dto.response.CreateUserResponse;
import com.withbuddy.account.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
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
