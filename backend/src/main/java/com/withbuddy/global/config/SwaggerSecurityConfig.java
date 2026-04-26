package com.withbuddy.global.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 후 발급된 accessToken을 입력하세요. 'Bearer ' 접두사 없이 토큰만 입력하면 됩니다."
)
public class SwaggerSecurityConfig {

    @Bean
    public OpenApiCustomizer globalSecurityCustomizer() {
        return openApi -> openApi.addSecurityItem(
                new SecurityRequirement().addList("bearerAuth")
        );
    }
}
