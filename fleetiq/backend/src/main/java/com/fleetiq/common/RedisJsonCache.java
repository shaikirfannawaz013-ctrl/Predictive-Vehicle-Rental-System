package com.fleetiq.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-through JSON cache on Redis. If Redis is unreachable the loader runs directly,
 * so a cache outage slows the API down instead of breaking it.
 */
@Component
public class RedisJsonCache {

    private static final Logger log = LoggerFactory.getLogger(RedisJsonCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisJsonCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public <T> T get(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw != null) {
                return mapper.readValue(raw, type);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for {}: {}", key, e.getMessage());
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis write failed for {}: {}", key, e.getMessage());
        }
        return value;
    }

    public void evict(String... keys) {
        try {
            for (String key : keys) {
                redis.delete(key);
            }
        } catch (Exception e) {
            log.warn("Redis evict failed: {}", e.getMessage());
        }
    }

    /** Returns true the first time a key is claimed within the TTL (used to avoid duplicate alerts). */
    public boolean claimOnce(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (Exception e) {
            return true;
        }
    }
}
