package com.withbuddy.global.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.global.jwt.JwtService;
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
}
