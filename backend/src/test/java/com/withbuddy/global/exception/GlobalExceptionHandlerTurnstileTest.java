package com.withbuddy.global.exception;

import com.withbuddy.account.auth.exception.LoginRateLimitExceededException;
import com.withbuddy.account.auth.exception.TurnstileVerificationFailedException;
import com.withbuddy.account.auth.exception.TurnstileVerificationUnavailableException;
import com.withbuddy.global.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTurnstileTest {

    @Test
    void mapsTurnstileFailureToBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

        ResponseEntity<ErrorResponse> response = handler.handleTurnstileVerificationFailedException(
                new TurnstileVerificationFailedException("보안 확인에 실패했습니다. 다시 시도해 주세요."),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("CAPTCHA_VERIFICATION_FAILED");
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("captcha");
    }

    @Test
    void mapsTurnstileUnavailableToServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

        ResponseEntity<ErrorResponse> response = handler.handleTurnstileVerificationUnavailableException(
                new TurnstileVerificationUnavailableException("보안 확인 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", new RuntimeException("timeout")),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("CAPTCHA_UNAVAILABLE");
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("captcha");
    }

    @Test
    void mapsLoginRateLimitToTooManyRequests() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

        ResponseEntity<ErrorResponse> response = handler.handleLoginRateLimitExceededException(
                new LoginRateLimitExceededException("로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.", 900),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("900");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("LOGIN_RATE_LIMITED");
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("login");
    }
}
