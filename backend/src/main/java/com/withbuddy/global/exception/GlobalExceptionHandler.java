package com.withbuddy.global.exception;

import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.global.dto.FieldValidationError;
import com.withbuddy.auth.exception.LoginFailedException;
import com.withbuddy.global.jwt.SessionNotActiveException;
import com.withbuddy.global.jwt.TokenMissingException;
import com.withbuddy.user.exception.DuplicateEmployeeNumberException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.jsonwebtoken.JwtException;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import com.withbuddy.storage.exception.StorageException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ErrorResponse> handleLoginFailedException(
            LoginFailedException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("login", e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "UNAUTHORIZED",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("auth", e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "INVALID_TOKEN",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwtException(
            ExpiredJwtException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("token", "액세스 토큰이 만료되었습니다.")
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "TOKEN_EXPIRED",
                errors,
                request.getRequestURI()
        );

        log.warn("토큰 만료: path={}, message={}", request.getRequestURI(), e.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(SessionNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotActiveException(
            SessionNotActiveException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("auth", e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "INVALID_TOKEN",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(
            JwtException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("token", "유효하지 않은 토큰입니다. 다시 로그인해 주세요.")
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "INVALID_TOKEN",
                errors,
                request.getRequestURI()
        );

        log.warn("유효하지 않은 토큰: path={}, message={}", request.getRequestURI(), e.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldValidationError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "BAD_REQUEST",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("request", e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "BAD_REQUEST",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("request", "요청 본문 형식이 올바르지 않습니다.")
        );

        Throwable cause = e.getCause();
        if (cause instanceof InvalidFormatException invalidFormatException
                && invalidFormatException.getTargetType() != null
                && LocalDate.class.isAssignableFrom(invalidFormatException.getTargetType())
                && !invalidFormatException.getPath().isEmpty()) {
            String fieldName = invalidFormatException.getPath().get(0).getFieldName();
            errors = List.of(new FieldValidationError(fieldName, "입사일은 yyyy-MM-dd 형식이어야 합니다."));
        }

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "BAD_REQUEST",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
            MissingRequestHeaderException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("auth", "인증 토큰이 누락되었습니다.")
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "TOKEN_MISSING",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageException(
            StorageException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError(e.getField(), e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                e.getStatus().value(),
                e.getStatus().getReasonPhrase(),
                e.getCode(),
                errors,
                request.getRequestURI()
        );

        log.warn("스토리지 예외: path={}, field={}, message={}", request.getRequestURI(), e.getField(), e.getMessage());

        return ResponseEntity.status(e.getStatus()).body(response);
    }

    @ExceptionHandler(AiTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAiTimeout(
            AiTimeoutException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("ai", "AI 답변 생성 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.")
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.GATEWAY_TIMEOUT.value(),
                HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase(),
                "AI_TIMEOUT",
                errors,
                request.getRequestURI()
        );

        log.warn("AI timeout: path={}, message={}", request.getRequestURI(), e.getMessage());

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    @ExceptionHandler({
            RedisConnectionFailureException.class,
            QueryTimeoutException.class
    })
    public ResponseEntity<ErrorResponse> handleRedisException(
            Exception e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("server", "세션 저장소 연결에 실패했습니다. 잠시 후 다시 시도해주세요.")
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "SESSION_STORE_UNAVAILABLE",
                errors,
                request.getRequestURI()
        );

        log.error("[REDIS_ERROR] path={}, message={}", request.getRequestURI(), e.getMessage(), e);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("server", "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.")
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "INTERNAL_SERVER_ERROR",
                errors,
                request.getRequestURI()
        );

        log.error("서버 오류: path={}", request.getRequestURI(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(TokenMissingException.class)
    public ResponseEntity<ErrorResponse> handleTokenMissingException(
            TokenMissingException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("auth", e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "TOKEN_MISSING",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(DuplicateEmployeeNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmployeeNumberException(
            DuplicateEmployeeNumberException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("employeeNumber", e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "DUPLICATE_EMPLOYEE_NUMBER",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(
            ForbiddenException e,
            HttpServletRequest request
    ) {
        List<FieldValidationError> errors = List.of(
                new FieldValidationError("auth", e.getMessage())
        );

        ErrorResponse response = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "FORBIDDEN",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
}
