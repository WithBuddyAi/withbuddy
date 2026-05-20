package com.withbuddy.admin.organization.controller;

import com.withbuddy.admin.organization.docs.AdminOrganizationOptionControllerDocs;
import com.withbuddy.admin.organization.dto.response.OrganizationOptionsResponse;
import com.withbuddy.admin.organization.service.AdminOrganizationOptionService;
import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/organization-options")
public class AdminOrganizationOptionController implements AdminOrganizationOptionControllerDocs {

    private final AdminOrganizationOptionService organizationOptionService;

    public AdminOrganizationOptionController(AdminOrganizationOptionService organizationOptionService) {
        this.organizationOptionService = organizationOptionService;
    }

    @GetMapping
    @Override
    public ResponseEntity<OrganizationOptionsResponse> getOrganizationOptions(Authentication authentication) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(organizationOptionService.getOrganizationOptions(principal));
    }
}
