package com.withbuddy.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheLayerConfig {

    @Bean
    public CacheManager cacheManager(AppCacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        if (properties.getL1().isEnabled()) {
            manager.setCaffeine(Caffeine.from(properties.getL1().getSpec()));
        } else {
            manager.setCaffeine(Caffeine.newBuilder().maximumSize(1));
        }
        return manager;
    }
}
