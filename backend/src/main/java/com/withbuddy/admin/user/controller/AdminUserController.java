package com.withbuddy.admin.user.controller;

import com.withbuddy.admin.user.docs.AdminUserControllerDocs;
import com.withbuddy.admin.user.dto.request.CreateUserRequest;
import com.withbuddy.admin.user.dto.response.CreateUserResponse;
import com.withbuddy.admin.user.dto.response.UserListResponse;
import com.withbuddy.admin.user.service.AdminUserService;
import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController implements AdminUserControllerDocs {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping
    @Override
    public ResponseEntity<CreateUserResponse> createUser(
            Authentication authentication,
            @Valid @RequestBody CreateUserRequest request
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.status(201).body(adminUserService.createUser(principal, request));
    }

    @GetMapping
    @Override
    public ResponseEntity<UserListResponse> getUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String teamName,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(adminUserService.getUsers(
                principal,
                page,
                size,
                department,
                teamName,
                sortBy,
                sortDirection
        ));
    }
}
