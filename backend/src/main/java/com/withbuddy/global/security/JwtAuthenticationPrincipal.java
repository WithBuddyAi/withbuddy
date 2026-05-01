package com.withbuddy.global.security;

public record JwtAuthenticationPrincipal(
        Long userId,
        String employeeNumber,
        String name,
        String companyCode,
        String companyName
) {
}
