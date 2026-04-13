package com.withbuddy.global.security;

public record StorageApiKeyPrincipal(
        String keyId,
        boolean globalAccess
) {
}
