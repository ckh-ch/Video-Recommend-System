package org.example.videorecommend.config;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class RedisCacheUtil {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 从 Redis 获取缓存（简单类型），未命中则查 MySQL 并回写缓存
     */
    public <T> T get(String key, long ttl, Class<T> type, Supplier<T> dbSupplier) {
        if (redisTemplate == null) return dbSupplier.get();
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) return objectMapper.readValue(cached, type);
        } catch (Exception e) { /* fall through */ }
        T result = dbSupplier.get();
        set(key, ttl, result);
        return result;
    }

    /**
     * 从 Redis 获取缓存（泛型 List/Map），未命中则查 MySQL 并回写缓存
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, long ttl, TypeReference<T> typeRef, Supplier<T> dbSupplier) {
        if (redisTemplate == null) return dbSupplier.get();
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) return objectMapper.readValue(cached, typeRef);
        } catch (Exception e) { /* fall through */ }
        T result = dbSupplier.get();
        set(key, ttl, result);
        return result;
    }

    private <T> void set(String key, long ttl, T result) {
        if (result == null) return;
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);
        } catch (Exception e) { /* ignore */ }
    }
}
