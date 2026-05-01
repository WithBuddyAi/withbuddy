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

@Component
public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_PREFIX = "/internal/v1/";
    private static final String INTERNAL_ROLE = "ROLE_INTERNAL_API";

    private final InternalApiSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public InternalApiAuthenticationFilter(
            InternalApiSecurityProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerName = StringUtils.hasText(properties.getHeaderName())
                ? properties.getHeaderName()
                : "X-Internal-Token";
        String expectedToken = properties.getToken();
        String actualToken = request.getHeader(headerName);

        if (!StringUtils.hasText(expectedToken)) {
            writeUnauthorized(response, request.getRequestURI(), headerName, "내부 API 토큰이 설정되지 않았습니다.");
            return;
        }
        if (!StringUtils.hasText(actualToken) || !expectedToken.equals(actualToken)) {
            writeUnauthorized(response, request.getRequestURI(), headerName, "유효하지 않은 내부 API 토큰입니다.");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "internal-api",
                null,
                List.of(new SimpleGrantedAuthority(INTERNAL_ROLE))
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
