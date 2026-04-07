package com.withbuddy.global.exception;

import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.global.dto.FieldValidationError;
import com.withbuddy.auth.exception.LoginFailedException;
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
}
