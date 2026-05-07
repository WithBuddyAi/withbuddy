package com.withbuddy.internal.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class InternalApiModels {

    private InternalApiModels() {
    }

    public record CacheGetRequest(
            @NotBlank @Size(max = 200) String key,
            @Size(max = 40) String namespace
    ) {
    }

    public record CacheGetResponse(
            String key,
            String namespace,
            boolean found,
            JsonNode value
    ) {
    }

    public record CacheGetMultiRequest(
            @NotEmpty @Size(max = 200) List<@NotBlank @Size(max = 200) String> keys,
            @Size(max = 40) String namespace
    ) {
    }

    public record CacheGetMultiResponse(
            String namespace,
            List<CacheGetResponse> items
    ) {
    }

    public record CacheSetRequest(
            @NotBlank @Size(max = 200) String key,
            @Size(max = 40) String namespace,
            @NotNull JsonNode value,
            @Min(1) @Max(86400) Integer ttlSeconds
    ) {
    }

    public record CacheSetMultiItem(
            @NotBlank @Size(max = 200) String key,
            @NotNull JsonNode value
    ) {
    }

    public record CacheSetMultiRequest(
            @Size(max = 40) String namespace,
            @NotEmpty @Size(max = 200) List<@Valid CacheSetMultiItem> items,
            @Min(1) @Max(86400) Integer ttlSeconds
    ) {
    }

    public record CacheSetMultiError(
            String key,
            String detail
    ) {
    }

    public record CacheSetMultiResponse(
            boolean ok,
            int written,
            List<CacheSetMultiError> errors
    ) {
    }

    public record CacheWriteResponse(
            String namespace,
            int affected
    ) {
    }

    public record CacheDeleteRequest(
            @NotBlank @Size(max = 200) String key,
            @Size(max = 40) String namespace
    ) {
    }

    public record TaskCreateRequest(
            @NotBlank @Size(max = 100) String type,
            @NotNull JsonNode payload,
            @Size(max = 500) String callbackUrl,
            @Min(1) @Max(3600) Integer timeoutSeconds,
            @Min(0) @Max(10) Integer retryCount,
            @Size(max = 100) String idempotencyKey
    ) {
    }

    public record TaskCreateResponse(
            String taskId,
            String status,
            boolean deduplicated,
            String createdAt
    ) {
    }

    public record TaskStatusResponse(
            String taskId,
            String type,
            String status,
            JsonNode payload,
            JsonNode result,
            String error,
            String callbackUrl,
            Integer timeoutSeconds,
            Integer retryCount,
            String createdAt,
            String updatedAt
    ) {
    }

    public record TaskRetryRequest(
            @Size(max = 200) String reason
    ) {
    }

    public record TaskActionResponse(
            String taskId,
            String status,
            String queuedAt
    ) {
    }
}
