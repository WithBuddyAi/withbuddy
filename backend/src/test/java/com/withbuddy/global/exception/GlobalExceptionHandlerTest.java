package com.withbuddy.global.exception;

import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.storage.exception.StorageException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @Test
    void omitsCodeForRouteMissing404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/not/exist");

        ResponseEntity<ErrorResponse> response = handler.handleNotFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/not/exist"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isNull();
        assertThat(response.getBody().getPath()).isEqualTo("/not/exist");
        assertThat(response.getBody().getErrors()).isNotEmpty();
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("path");
    }

    @Test
    void keepsNotFoundCodeForResourceMissing404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents/100");

        ResponseEntity<ErrorResponse> response = handler.handleStorageException(
                new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/documents/100");
    }
}
