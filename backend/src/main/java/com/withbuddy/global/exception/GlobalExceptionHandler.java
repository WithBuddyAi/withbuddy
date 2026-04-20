package com.withbuddy.global.exception;

import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.global.dto.FieldValidationError;
import com.withbuddy.auth.exception.LoginFailedException;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import com.withbuddy.storage.exception.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
                504,
                "Gateway Timeout",
                "AI_TIMEOUT",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.status(504).body(response);
    }
}
