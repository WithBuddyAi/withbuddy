package com.withbuddy.infrastructure.cache;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CacheKeyBuilder {

    private final AppCacheProperties properties;

    public CacheKeyBuilder(AppCacheProperties properties) {
        this.properties = properties;
    }

    public String build(String namespace, String key) {
        String normalizedNamespace = normalize(namespace, 40, "namespace");
        String normalizedKey = normalize(key, 200, "cache key");
        if (!normalizedNamespace.matches("^[a-zA-Z0-9:_-]+$")) {
            throw new IllegalArgumentException("namespace는 영문/숫자/:/_/- 문자만 사용할 수 있습니다.");
        }
        if (!normalizedKey.matches("^[a-zA-Z0-9:_.-]+$")) {
            throw new IllegalArgumentException("cache key는 영문/숫자/:/_/./- 문자만 사용할 수 있습니다.");
        }
        return String.join(
                ":",
                normalize(properties.getKeyPrefix(), 20, "keyPrefix"),
                normalize(properties.getEnv(), 20, "env"),
                normalizedNamespace,
                normalize(properties.getKeyVersion(), 20, "keyVersion"),
                normalizedKey
        );
    }

    private String normalize(String value, int maxLength, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 길이는 " + maxLength + "자를 초과할 수 없습니다.");
        }
        return normalized;
    }
}
