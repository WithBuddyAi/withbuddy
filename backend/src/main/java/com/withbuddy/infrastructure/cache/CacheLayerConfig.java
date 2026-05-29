package com.withbuddy.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

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

    @Bean("internalApiLocalCache")
    public Cache<String, InternalApiLocalCacheValue> internalApiLocalCache(AppCacheProperties properties) {
        return Caffeine.from(properties.getL1().getSpec()).build();
    }

    @Bean
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            AppCacheProperties properties,
            CacheInvalidationPublisher publisher,
            Cache<String, InternalApiLocalCacheValue> internalApiLocalCache
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        if (!properties.getInvalidation().isEnabled()) {
            return container;
        }

        MessageListener listener = (Message message, byte[] pattern) -> {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            int sep = payload.indexOf('|');
            if (sep < 0) {
                return;
            }

            String sender = payload.substring(0, sep);
            if (publisher.nodeId().equals(sender)) {
                return;
            }

            String cacheKey = payload.substring(sep + 1);
            if (!cacheKey.isBlank()) {
                internalApiLocalCache.invalidate(cacheKey);
            }
        };

        container.addMessageListener(listener, new ChannelTopic(properties.getInvalidation().getChannel()));
        return container;
    }
}
