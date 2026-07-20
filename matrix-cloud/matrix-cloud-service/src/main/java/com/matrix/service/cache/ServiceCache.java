package com.matrix.service.cache;

import java.util.Map;

/**
 * 缓存管理
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface ServiceCache {

    Hash getHash();

    Set getSet();

    java.util.Set<String> keys(String pattern);

    void expire(String key, long ttl);

    void delete(String key);

    void set(String key, String value, long ttl);

    String get(String key);

    boolean lock(String key, long ttl);

    interface Hash {

        void put(String key, String hashKey, String value, long ttl);

        void putAll(String key, Map map, long ttl);

        String get(String key, String hashKey);

        Map<String, String> getAll(String key);

        java.util.Set<String> keys(String key);

        java.util.Set<String> values(String key);

        void remove(String key, String hashKey);

        Long size(String key);

    }


    interface Set {

        void add(String key, String value, long ttl);

        java.util.Set<String> getAll(String key);

        void remove(String key, String value);

    }

}


