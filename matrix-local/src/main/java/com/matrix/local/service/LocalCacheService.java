package com.matrix.local.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.matrix.local.dal.entity.LocalCache;
import com.matrix.local.dal.mapper.LocalCacheMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本地缓存服务
 * <p> 封装 tbl_local_cache 的 CRUD，替代 RedisTemplate </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class LocalCacheService {

    @Resource
    private LocalCacheMapper localCacheMapper;

    /**
     * @description 存储 KV，支持 TTL（秒）
     * <p> ttlSeconds <= 0 表示永不过期 </p>
     * <p> 使用原子 UPSERT 避免并发 UNIQUE 约束冲突 </p>
     */
    public void put(String key, String value, long ttlSeconds) {
        LocalCache cache = new LocalCache();
        cache.setCacheKey(key);
        cache.setCacheValue(value);
        if (ttlSeconds > 0) {
            cache.setExpireAt(System.currentTimeMillis() / 1000 + ttlSeconds);
        } else {
            cache.setExpireAt(-1L);
        }
        localCacheMapper.upsert(cache);
    }

    /**
     * @description 获取 KV，过期自动删除返回 null
     */
    public String get(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        LocalCache cache = localCacheMapper.selectOne(
                Wrappers.<LocalCache>lambdaQuery().eq(LocalCache::getCacheKey, key));
        if (cache == null) {
            return null;
        }
        if (cache.isExpired()) {
            localCacheMapper.deleteById(cache.getId());
            return null;
        }
        return cache.getCacheValue();
    }

    /**
     * @description 删除 KV
     */
    public void delete(String key) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        localCacheMapper.delete(Wrappers.<LocalCache>lambdaQuery()
                .eq(LocalCache::getCacheKey, key));
    }

    /**
     * @description 按 pattern 匹配 key（* 通配符转 SQL %）
     */
    public List<String> keys(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return Collections.emptyList();
        }
        String likePattern = pattern.replace("*", "%");
        List<LocalCache> list = localCacheMapper.selectList(
                Wrappers.<LocalCache>lambdaQuery()
                        .like(LocalCache::getCacheKey, likePattern.replace("%", "\\%").replace("_", "\\_"))
                        .or()
                        .apply("cache_key LIKE {0}", likePattern));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(c -> !c.isExpired())
                .map(LocalCache::getCacheKey)
                .collect(Collectors.toList());
    }

    /**
     * @description 获取剩余 TTL（秒）
     * <p> -1 永不过期，-2 不存在 </p>
     */
    public long getExpire(String key) {
        if (StringUtils.isBlank(key)) {
            return -2L;
        }
        LocalCache cache = localCacheMapper.selectOne(
                Wrappers.<LocalCache>lambdaQuery().eq(LocalCache::getCacheKey, key));
        if (cache == null) {
            return -2L;
        }
        if (cache.getExpireAt() == null || cache.getExpireAt() <= 0) {
            return -1L;
        }
        long remaining = cache.getExpireAt() - (System.currentTimeMillis() / 1000);
        if (remaining <= 0) {
            localCacheMapper.deleteById(cache.getId());
            return -2L;
        }
        return remaining;
    }

}
