package com.matrix.local.primary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.local.dal.entity.LocalCache;
import com.matrix.service.cache.ServiceCache;
import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

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
    private BaseMapper<LocalCache> baseMapper;

    @Getter
    public final Hash hash = new Hash();
    @Getter
    public final Set set = new Set();

    /**
     * keys
     */
    @Override
    public java.util.Set<String> keys(String pattern) {
        return null;
    }

    @Override
    public void expire(String key, long ttl) {

    }

    @Override
    public void delete(String key) {

    }

    /**
     * String
     */
    @Override
    public void set(String key, String value, long ttl) {

    }

    @Override
    public String get(String key) {
        return null;
    }

    @Override
    public boolean lock(String key, long ttl) {
        return false;
    }

    /**
     * Hash
     */
    public class Hash implements ServiceCache.Hash {

        @Override
        public void put(String key, String hashKey, String value, long ttl) {

            expire(key, ttl);
        }

        @Override
        public void putAll(String key, Map map, long ttl) {

            expire(key, ttl);
        }

        @Override
        public String get(String key, String hashKey) {
            return null;
        }

        @Override
        public Map<String, String> getAll(String key) {
            return null;
        }

        @Override
        public java.util.Set<String> keys(String key) {
            return null;
        }

        @Override
        public java.util.Set<String> values(String key) {
            return null;
        }

        @Override
        public void remove(String key, String hashKey) {

        }

        @Override
        public Long size(String key) {
            return null;
        }
    }

    /**
     * Set
     */
    public class Set implements ServiceCache.Set {

        @Override
        public void add(String key, String value, long ttl) {

            expire(key, ttl);
        }

        @Override
        public java.util.Set<String> getAll(String key) {
            return null;
        }

        @Override
        public void remove(String key, String value) {

        }

    }

}


