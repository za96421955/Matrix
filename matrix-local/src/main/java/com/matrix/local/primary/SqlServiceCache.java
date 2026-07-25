package com.matrix.local.primary;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.matrix.local.dal.entity.LocalCache;
import com.matrix.local.dal.mapper.LocalCacheMapper;
import com.matrix.local.service.LocalCacheService;
import com.matrix.service.cache.ServiceCache;
import jakarta.annotation.Resource;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 缓存管理
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Component
@Primary
public class SqlServiceCache implements ServiceCache {

    @Resource
    private LocalCacheService localCacheService;

    @Resource
    private LocalCacheMapper localCacheMapper;

    @Getter
    public final Hash hash = new Hash();
    @Getter
    public final Set set = new Set();

    /**
     * keys
     */
    @Override
    public java.util.Set<String> keys(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return Collections.emptySet();
        }
        List<String> list = localCacheService.keys(pattern);
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptySet();
        }
        return new HashSet<>(list);
    }

    @Override
    public void expire(String key, long ttl) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        // 重新 put 实现 TTL 更新
        String value = localCacheService.get(key);
        if (value != null) {
            localCacheService.put(key, value, ttl);
        }
    }

    @Override
    public void delete(String key) {
        localCacheService.delete(key);
    }

    /**
     * String
     */
    @Override
    public void set(String key, String value, long ttl) {
        localCacheService.put(key, value, ttl);
    }

    @Override
    public String get(String key) {
        return localCacheService.get(key);
    }

    /**
     * 原子分布式锁
     * <p> 使用 INSERT OR IGNORE + UNIQUE(cache_key) 约束，单条 SQL 原子完成锁获取，避免 check-then-set 竞态条件 </p>
     */
    @Override
    public boolean lock(String key, long ttl) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        LocalCache cache = new LocalCache();
        cache.setCacheKey(key);
        cache.setCacheValue("1");
        if (ttl > 0) {
            cache.setExpireAt(System.currentTimeMillis() / 1000 + ttl);
        } else {
            cache.setExpireAt(-1L);
        }
        int rows = localCacheMapper.insertIfAbsent(cache);
        return rows > 0;
    }

    /**
     * Hash
     * <p> 整体 JSON 序列化存储，存储 key 前缀 "hash:" </p>
     */
    public class Hash implements ServiceCache.Hash {

        private String storageKey(String key) {
            return "hash:" + key;
        }

        @Override
        public void put(String key, String hashKey, String value, long ttl) {
            String sk = this.storageKey(key);
            String json = localCacheService.get(sk);
            Map<String, String> map = new HashMap<>();
            if (StringUtils.isNotBlank(json)) {
                try {
                    map = JSON.parseObject(json, new TypeReference<>() {});
                } catch (Exception ignore) {}
            }
            map.put(hashKey, value);
            localCacheService.put(sk, JSON.toJSONString(map), ttl);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void putAll(String key, Map map, long ttl) {
            if (map == null || map.isEmpty()) {
                return;
            }
            String sk = this.storageKey(key);
            String json = localCacheService.get(sk);
            Map<String, String> existing = new HashMap<>();
            if (StringUtils.isNotBlank(json)) {
                try {
                    existing = JSON.parseObject(json, new TypeReference<>() {});
                } catch (Exception ignore) {}
            }
            for (Object entryObj : map.entrySet()) {
                Map.Entry<String, String> entry = (Map.Entry<String, String>) entryObj;
                existing.put(entry.getKey(), entry.getValue());
            }
            localCacheService.put(sk, JSON.toJSONString(existing), ttl);
        }

        @Override
        public String get(String key, String hashKey) {
            String sk = this.storageKey(key);
            String json = localCacheService.get(sk);
            if (StringUtils.isBlank(json)) {
                return null;
            }
            try {
                Map<String, String> map = JSON.parseObject(json, new TypeReference<>() {});
                if (map == null) {
                    return null;
                }
                return map.get(hashKey);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public Map<String, String> getAll(String key) {
            String sk = this.storageKey(key);
            String json = localCacheService.get(sk);
            if (StringUtils.isBlank(json)) {
                return Collections.emptyMap();
            }
            try {
                Map<String, String> map = JSON.parseObject(json, new TypeReference<>() {});
                if (map == null) {
                    return Collections.emptyMap();
                }
                return map;
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        }

        @Override
        public java.util.Set<String> keys(String key) {
            return this.getAll(key).keySet();
        }

        @Override
        public java.util.Set<String> values(String key) {
            return new HashSet<>(getAll(key).values());
        }

        @Override
        public void remove(String key, String hashKey) {
            String sk = this.storageKey(key);
            String json = localCacheService.get(sk);
            if (StringUtils.isBlank(json)) {
                return;
            }
            try {
                Map<String, String> map = JSON.parseObject(json, new TypeReference<>() {});
                if (map == null || !map.containsKey(hashKey)) {
                    return;
                }
                map.remove(hashKey);
                if (map.isEmpty()) {
                    localCacheService.delete(sk);
                } else {
                    long remainingTtl = localCacheService.getExpire(sk);
                    if (remainingTtl < 0) {
                        remainingTtl = -1L;
                    }
                    localCacheService.put(sk, JSON.toJSONString(map), remainingTtl);
                }
            } catch (Exception ignore) {}
        }

        @Override
        public Long size(String key) {
            return (long) this.getAll(key).size();
        }

    }

    /**
     * Set
     * <p> JSON 数组序列化存储，存储 key 前缀 "set:" </p>
     */
    public class Set implements ServiceCache.Set {

        private String storageKey(String key) {
            return "set:" + key;
        }

        @Override
        public void add(String key, String value, long ttl) {
            String sk = this.storageKey(key);
            String json = localCacheService.get(sk);
            java.util.Set<String> set = new HashSet<>();
            if (StringUtils.isNotBlank(json)) {
                try {
                    set = JSON.parseObject(json, new TypeReference<>() {});
                } catch (Exception ignore) {}
            }
            set.add(value);
            localCacheService.put(sk, JSON.toJSONString(set), ttl);
        }

        @Override
        public java.util.Set<String> getAll(String key) {
            String sk = storageKey(key);
            String json = localCacheService.get(sk);
            if (StringUtils.isBlank(json)) {
                return Collections.emptySet();
            }
            try {
                java.util.Set<String> set = JSON.parseObject(json, new TypeReference<>() {});
                if (set == null) {
                    return Collections.emptySet();
                }
                return set;
            } catch (Exception e) {
                return Collections.emptySet();
            }
        }

        @Override
        public void remove(String key, String value) {
            String sk = storageKey(key);
            String json = localCacheService.get(sk);
            if (StringUtils.isBlank(json)) {
                return;
            }
            try {
                java.util.Set<String> set = JSON.parseObject(json, new TypeReference<>() {});
                if (set == null || !set.contains(value)) {
                    return;
                }
                set.remove(value);
                if (set.isEmpty()) {
                    localCacheService.delete(sk);
                } else {
                    long remainingTtl = localCacheService.getExpire(sk);
                    if (remainingTtl < 0) {
                        remainingTtl = -1L;
                    }
                    localCacheService.put(sk, JSON.toJSONString(set), remainingTtl);
                }
            } catch (Exception ignore) {}
        }

    }

}
