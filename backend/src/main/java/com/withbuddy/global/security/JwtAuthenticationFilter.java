package com.withbuddy.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.global.dto.FieldValidationError;
import com.withbuddy.global.exception.UnauthorizedException;
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.global.jwt.SessionExpiredException;
import com.withbuddy.global.jwt.SessionRevokedException;
import com.withbuddy.global.jwt.TokenMissingException;
import com.withbuddy.global.logging.RedisFailureLogSupport;
import com.withbuddy.global.logging.RequestUrlMaskingSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
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
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorizationHeader)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = jwtService.extractBearerToken(authorizationHeader);
            Claims claims = jwtService.getClaims(token);

            Long userId = Long.parseLong(claims.getSubject());
            JwtAuthenticationPrincipal principal = new JwtAuthenticationPrincipal(
                    userId,
                    claims.get("employeeNumber", String.class),
                    claims.get("name", String.class),
                    claims.get("companyCode", String.class),
                    claims.get("companyName", String.class),
                    claims.get("hireDate", String.class)
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            String maskedPath = RequestUrlMaskingSupport.resolveMaskedPath(request);
            RedisFailureLogSupport.logRedisFailure(log, request, e);
            writeServiceUnavailable(response, maskedPath);
            return;
        } catch (TokenMissingException e) {
            String maskedPath = RequestUrlMaskingSupport.resolveMaskedPath(request);
            writeUnauthorized(response, maskedPath, "TOKEN_MISSING", "auth", e.getMessage());
            return;
        } catch (SessionExpiredException | ExpiredJwtException e) {
            String maskedPath = RequestUrlMaskingSupport.resolveMaskedPath(request);
            writeUnauthorized(response, maskedPath, "SESSION_EXPIRED", "session", e.getMessage());
            return;
        } catch (SessionRevokedException e) {
            String maskedPath = RequestUrlMaskingSupport.resolveMaskedPath(request);
            writeUnauthorized(response, maskedPath, "SESSION_REVOKED", "session", e.getMessage());
            return;
        } catch (UnauthorizedException | JwtException | IllegalArgumentException e) {
            String maskedPath = RequestUrlMaskingSupport.resolveMaskedPath(request);
            writeUnauthorized(response, maskedPath, "INVALID_TOKEN", "token", e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletResponse response,
            String path,
            String code,
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
                code,
                List.of(new FieldValidationError(field, message)),
                path
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private void writeServiceUnavailable(
            HttpServletResponse response,
            String path
    ) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "SESSION_STORE_UNAVAILABLE",
                List.of(new FieldValidationError("server", "세션 저장소 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.")),
                path
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
