package com.matrix.service.cache;

import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 缓存管理
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Component
@ConditionalOnMissingBean(ServiceCache.class)
public class RedisServiceCache implements ServiceCache {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Getter
    public final Hash hash = new Hash();
    @Getter
    public final Set set = new Set();

    /**
     * keys
     */
    @Override
    public java.util.Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    @Override
    public void expire(String key, long ttl) {
        if (ttl < 0) {
            return;
        }
        redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * String
     */
    @Override
    public void set(String key, String value, long ttl) {
        redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
    }

    @Override
    public String get(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean lock(String key, String value, long ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl, TimeUnit.SECONDS);
    }

    /**
     * Hash
     */
    public class Hash implements ServiceCache.Hash {

        @Override
        public void put(String key, String hashKey, String value, long ttl) {
            redisTemplate.opsForHash().put(key, hashKey, value);
            expire(key, ttl);
        }

        @Override
        public void putAll(String key, Map map, long ttl) {
            redisTemplate.opsForHash().putAll(key, map);
            expire(key, ttl);
        }

        @Override
        public String get(String key, String hashKey) {
            return (String) redisTemplate.opsForHash().get(key, hashKey);
        }

        @Override
        public Map<String, String> getAll(String key) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<Object, Object> entry : redisTemplate.opsForHash().entries(key).entrySet()) {
                result.put((String) entry.getKey(), (String) entry.getValue());
            }
            return result;
        }

        @Override
        public java.util.Set<String> keys(String key) {
            return redisTemplate.opsForHash().keys(key).stream()
                    .map(o -> (String) o)
                    .collect(Collectors.toSet());
        }

        @Override
        public java.util.Set<String> values(String key) {
            return redisTemplate.opsForHash().values(key).stream()
                    .map(o -> (String) o)
                    .collect(Collectors.toSet());
        }

        @Override
        public void remove(String key, String hashKey) {
            redisTemplate.opsForHash().delete(key, hashKey);
        }

        @Override
        public Long size(String key) {
            return redisTemplate.opsForHash().size(key);
        }
    }

    /**
     * Set
     */
    public class Set implements ServiceCache.Set {

        @Override
        public void add(String key, String value, long ttl) {
            redisTemplate.opsForSet().add(key, value);
            expire(key, ttl);
        }

        @Override
        public java.util.Set<String> getAll(String key) {
            return redisTemplate.opsForSet().members(key).stream()
                    .map(o -> (String) o)
                    .collect(Collectors.toSet());
        }

        @Override
        public void remove(String key, String value) {
            redisTemplate.opsForSet().remove(key, value);
        }

    }

}


