package com.matrix.service.cache;

import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "matrix.redis.enabled", havingValue = "true")
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
    /** expire操作 */
    public void expire(String key, long ttl) {
        if (ttl < 0) {
            return;
        }
        redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
    }

    @Override
    /** 递归删除目录或文件 */
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
    /** 获取指定数据 */
    public String get(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    @Override
    /** lock操作 */
    public boolean lock(String key, long ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, "1", ttl, TimeUnit.SECONDS);
    }

    /**
     * Hash
     */
    public class Hash implements ServiceCache.Hash {

        @Override
        /** put操作 */
        public void put(String key, String hashKey, String value, long ttl) {
            redisTemplate.opsForHash().put(key, hashKey, value);
            expire(key, ttl);
        }

        @Override
        /** putAll操作 */
        public void putAll(String key, Map map, long ttl) {
            redisTemplate.opsForHash().putAll(key, map);
            expire(key, ttl);
        }

        @Override
        /** 获取指定数据 */
        public String get(String key, String hashKey) {
            return (String) redisTemplate.opsForHash().get(key, hashKey);
        }

        @Override
        /** 获取All属性值 */
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
        /** 移除数据条目 */
        public void remove(String key, String hashKey) {
            redisTemplate.opsForHash().delete(key, hashKey);
        }

        @Override
        /** size操作 */
        public Long size(String key) {
            return redisTemplate.opsForHash().size(key);
        }
    }

    /**
     * Set
     */
    public class Set implements ServiceCache.Set {

        @Override
        /** 添加数据条目 */
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
        /** 移除数据条目 */
        public void remove(String key, String value) {
            redisTemplate.opsForSet().remove(key, value);
        }

    }

}


