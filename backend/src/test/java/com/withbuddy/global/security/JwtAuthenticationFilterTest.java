package com.withbuddy.global.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.global.jwt.SessionExpiredException;
import com.withbuddy.global.jwt.SessionRevokedException;
import com.withbuddy.global.jwt.TokenMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returns503WhenRedisConnectionFailsDuringAuthentication() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/messages");
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractBearerToken("Bearer test-token")).thenReturn("test-token");
        when(jwtService.getClaims("test-token"))
                .thenThrow(new RedisConnectionFailureException("redis connection failed"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("SESSION_STORE_UNAVAILABLE");
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/chat/messages");
        assertThat(body.get("errors").get(0).get("field").asText()).isEqualTo("server");
    }

    @Test
    void returns503WhenRedisQueryTimeoutOccursDuringAuthentication() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/messages");
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractBearerToken("Bearer test-token")).thenReturn("test-token");
        when(jwtService.getClaims("test-token"))
                .thenThrow(new QueryTimeoutException("redis query timeout"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("SESSION_STORE_UNAVAILABLE");
        assertThat(body.get("status").asInt()).isEqualTo(503);
    }

    @Test
    void returns401SessionExpiredWhenRedisSessionIsMissingEvenIfJwtIsValid() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/messages");
        request.addHeader("Authorization", "Bearer still-valid-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractBearerToken("Bearer still-valid-jwt")).thenReturn("still-valid-jwt");
        when(jwtService.getClaims("still-valid-jwt"))
                .thenThrow(new SessionExpiredException("redis session expired before jwt exp"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("SESSION_EXPIRED");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("errors").get(0).get("field").asText()).isEqualTo("session");
    }

    @Test
    void returns401SessionRevokedOnDuplicateLogin() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/messages");
        request.addHeader("Authorization", "Bearer previous-device-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractBearerToken("Bearer previous-device-token")).thenReturn("previous-device-token");
        when(jwtService.getClaims("previous-device-token"))
                .thenThrow(new SessionRevokedException("token was revoked by another login"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("SESSION_REVOKED");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("errors").get(0).get("field").asText()).isEqualTo("session");
    }

    @Test
    void returns401TokenMissingWhenAuthorizationHeaderHasNoToken() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/messages");
        request.addHeader("Authorization", "Bearer   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractBearerToken("Bearer   "))
                .thenThrow(new TokenMissingException("인증 토큰이 없습니다."));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("TOKEN_MISSING");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("errors").get(0).get("field").asText()).isEqualTo("auth");
    }
}
