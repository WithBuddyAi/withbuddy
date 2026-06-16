package com.withbuddy.account.auth.turnstile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TurnstileSiteverifyResponse(
        boolean success,
        @JsonProperty("error-codes") List<String> errorCodes
) {
}
