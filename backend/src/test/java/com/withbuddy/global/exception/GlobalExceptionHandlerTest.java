package com.withbuddy.global.exception;

import com.withbuddy.global.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void mapsRedisConnectionFailureToSessionStoreUnavailable503() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/logout");

        ResponseEntity<ErrorResponse> response = handler.handleRedisConnectionFailureException(
                new RedisConnectionFailureException("redis down"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getCode()).isEqualTo("SESSION_STORE_UNAVAILABLE");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/auth/logout");
        assertThat(response.getBody().getErrors()).isNotEmpty();
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("server");
    }
}
