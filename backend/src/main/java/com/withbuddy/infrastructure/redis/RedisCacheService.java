package com.withbuddy.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisCacheService {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> CONSUME_DOWNLOAD_TOKEN_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('exists', KEYS[1]) == 0 then
                return -1
            end

            local documentId = redis.call('hget', KEYS[1], 'documentId')
            local source = redis.call('hget', KEYS[1], 'source')
            if documentId ~= ARGV[1] or source ~= ARGV[2] then
                return -2
            end

            local remaining = tonumber(redis.call('hget', KEYS[1], 'remaining') or '-1')
            if remaining <= 0 then
                redis.call('del', KEYS[1])
                return -3
            end

            remaining = remaining - 1
            if remaining <= 0 then
                redis.call('del', KEYS[1])
                return 0
            end

            redis.call('hset', KEYS[1], 'remaining', tostring(remaining))
            return remaining
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public boolean putIfAbsent(String key, String value, Duration ttl) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(result);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public List<String> listRange(String key, long start, long end) {
        List<String> values = redisTemplate.opsForList().range(key, start, end);
        return values == null ? List.of() : values;
    }

    public void listRightPush(String key, String value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    public void listTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    public long increment(String key) {
        Long result = redisTemplate.opsForValue().increment(key);
        return result == null ? 0L : result;
    }

    public Optional<Long> getExpireSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            return Optional.empty();
        }
        return Optional.of(ttl);
    }

    public void putHash(String key, Map<String, String> values, Duration ttl) {
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttl);
    }

    public Long consumeDownloadToken(String key, String documentId, String source) {
        Long result = redisTemplate.execute(
                CONSUME_DOWNLOAD_TOKEN_SCRIPT,
                Collections.singletonList(key),
                documentId,
                source
        );
        return result == null ? -1L : result;
    }

    public boolean releaseLock(String key, String lockValue) {
        Long result = redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(key), lockValue);
        return Long.valueOf(1L).equals(result);
    }

    public void deleteByPrefix(String prefix) {
        redisTemplate.execute((RedisConnection connection) -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(1000)
                    .build();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    if (keyBytes != null) {
                        redisTemplate.delete(new String(keyBytes, StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception ignored) {
                // cleanup 보조 경로이므로 실패 시 호출 흐름을 깨지 않는다.
            }
            return null;
        });
    }
}
