package com.withbuddy.infrastructure.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class CacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final AppCacheProperties properties;
    private final String nodeId;

    public CacheInvalidationPublisher(StringRedisTemplate redisTemplate, AppCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.nodeId = StringUtils.hasText(properties.getInvalidation().getNodeId())
                ? properties.getInvalidation().getNodeId().trim()
                : UUID.randomUUID().toString();
    }

    public String nodeId() {
        return nodeId;
    }

    public void publishKeyInvalidation(String cacheKey) {
        if (!properties.getInvalidation().isEnabled()) {
            return;
        }
        String payload = nodeId + "|" + cacheKey;
        redisTemplate.convertAndSend(properties.getInvalidation().getChannel(), payload);
    }
}
