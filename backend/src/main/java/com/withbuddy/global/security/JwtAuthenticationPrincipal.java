package com.withbuddy.global.security;

public record JwtAuthenticationPrincipal(
        Long userId,
        String companyCode,
        String employeeNumber,
        String name
) {
}
