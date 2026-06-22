package com.withbuddy.global.config;

import com.withbuddy.account.auth.cookie.AuthCookieNames;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Set;

@Configuration
@SecurityScheme(
        name = "sessionCookieAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = AuthCookieNames.ACCESS_TOKEN,
        description = "로그인 후 브라우저에 저장되는 httpOnly 세션 쿠키를 사용합니다."
)
public class SwaggerSecurityConfig {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login"
    );

    @Bean
    public OpenApiCustomizer globalSecurityCustomizer() {
        return openApi -> {
            openApi.addSecurityItem(new SecurityRequirement().addList("sessionCookieAuth"));

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                if (PUBLIC_PATHS.contains(path)) {
                    clearSecurity(pathItem);
                }
            });
        };
    }

    private void clearSecurity(PathItem pathItem) {
        pathItem.readOperations().forEach(operation -> operation.setSecurity(Collections.emptyList()));
    }
}
