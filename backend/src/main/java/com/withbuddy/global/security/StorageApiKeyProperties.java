package com.withbuddy.global.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.storage-api")
public class StorageApiKeyProperties {

    private boolean enabled = true;
    private List<ApiKey> keys = new ArrayList<>();

    public Optional<ApiKey> findActiveKey(String keyValue) {
        if (!StringUtils.hasText(keyValue)) {
            return Optional.empty();
        }

        return keys.stream()
                .filter(ApiKey::isActive)
                .filter(key -> StringUtils.hasText(key.getValue()))
                .filter(key -> key.getValue().equals(keyValue))
                .findFirst();
    }

    @Getter
    @Setter
    public static class ApiKey {
        private String id;
        private String value;
        private boolean globalAccess = true;
        private boolean active = true;
    }
}
