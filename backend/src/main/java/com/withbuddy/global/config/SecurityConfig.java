package com.withbuddy.global.config;

import com.withbuddy.global.security.StorageApiKeyAuthenticationFilter;
import com.withbuddy.global.security.StorageApiKeyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final StorageApiKeyAuthenticationFilter storageApiKeyAuthenticationFilter;
    private final StorageApiKeyProperties storageApiKeyProperties;
    private final CorsProperties corsProperties;

    public SecurityConfig(
            StorageApiKeyAuthenticationFilter storageApiKeyAuthenticationFilter,
            StorageApiKeyProperties storageApiKeyProperties,
            CorsProperties corsProperties
    ) {
        this.storageApiKeyAuthenticationFilter = storageApiKeyAuthenticationFilter;
        this.storageApiKeyProperties = storageApiKeyProperties;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/chat/**",
                            "/error",
                            "/actuator/health",
                            "/actuator/health/**",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/v3/api-docs/**"
                    ).permitAll();

                    auth.requestMatchers(request -> request.getRequestURI().startsWith("/api/v1/documents")).permitAll();

                    auth.anyRequest().authenticated();
                });

        if (storageApiKeyProperties.isEnabled()) {
            http.addFilterBefore(storageApiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(filterBlank(corsProperties.getAllowedOriginPatterns()));
        configuration.setAllowedMethods(filterBlank(corsProperties.getAllowedMethods()));
        configuration.setAllowedHeaders(filterBlank(corsProperties.getAllowedHeaders()));
        List<String> exposedHeaders = filterBlank(corsProperties.getExposedHeaders());
        if (!exposedHeaders.isEmpty()) {
            configuration.setExposedHeaders(exposedHeaders);
        }
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> filterBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }
}
