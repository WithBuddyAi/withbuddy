package com.withbuddy.global.exception;

import com.withbuddy.global.dto.ErrorResponse;
import com.withbuddy.storage.exception.StorageException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

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
    void keepsNotFoundCodeForDomainResourceMissing() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/documents/999");

        ResponseEntity<ErrorResponse> response = handler.handleStorageException(
                new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/admin/documents/999");
    }

    @Test
    void includesStorageExceptionDetailsInFieldError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/documents/upload");

        ResponseEntity<ErrorResponse> response = handler.handleStorageException(
                new StorageException(
                        HttpStatus.CONFLICT,
                        "DOCUMENT_DUPLICATE",
                        "contentHash",
                        "이미 동일한 내용의 문서가 등록되어 있어요.",
                        Map.of(
                                "duplicateType", "CONTENT",
                                "duplicateDocumentTitle", "복지카드 신청 안내"
                        )
                ),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrors()).isNotEmpty();
        assertThat(response.getBody().getErrors().get(0).getDetails())
                .containsEntry("duplicateType", "CONTENT")
                .containsEntry("duplicateDocumentTitle", "복지카드 신청 안내");
    }

    @Test
    void mapsMultipartSizeLimitToFileSizeCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/documents/upload");

        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(20L * 1024L * 1024L),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FILE_001_SIZE");
        assertThat(response.getBody().getErrors()).isNotEmpty();
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("file");
    }

    @Test
    void mapsMissingFilePartToFileEmptyCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/documents/upload");

        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestPartException(
                new MissingServletRequestPartException("file"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FILE_001_EMPTY");
        assertThat(response.getBody().getErrors()).isNotEmpty();
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("file");
    }
}
