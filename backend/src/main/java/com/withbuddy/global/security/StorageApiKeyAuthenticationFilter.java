package com.withbuddy.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.global.dto.FieldValidationError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
public class StorageApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String STORAGE_API_PREFIX = "/api/v1/documents";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String STORAGE_ADMIN_ROLE = "ROLE_STORAGE_ADMIN";

    private final StorageApiKeyProperties storageApiKeyProperties;
    private final ObjectMapper objectMapper;

    public StorageApiKeyAuthenticationFilter(
            StorageApiKeyProperties storageApiKeyProperties,
            ObjectMapper objectMapper
    ) {
        this.storageApiKeyProperties = storageApiKeyProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(STORAGE_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!storageApiKeyProperties.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKeyValue = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apiKeyValue)) {
            writeUnauthorized(response, request.getRequestURI(), API_KEY_HEADER, "X-API-Key 헤더가 필요합니다.");
            return;
        }

        Optional<StorageApiKeyProperties.ApiKey> matchedKey = storageApiKeyProperties.findActiveKey(apiKeyValue);
        if (matchedKey.isEmpty()) {
            writeUnauthorized(response, request.getRequestURI(), API_KEY_HEADER, "유효하지 않은 API Key 입니다.");
            return;
        }

        StorageApiKeyProperties.ApiKey apiKey = matchedKey.get();
        StorageApiKeyPrincipal principal = new StorageApiKeyPrincipal(apiKey.getId(), apiKey.isGlobalAccess());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(STORAGE_ADMIN_ROLE))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletResponse response,
            String path,
            String field,
            String message
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "UNAUTHORIZED",
                List.of(new FieldValidationError(field, message)),
                path
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
