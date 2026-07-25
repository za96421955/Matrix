package com.matrix.local.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.local.dal.entity.LocalCache;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocalCacheMapper extends BaseMapper<LocalCache> {

    /**
     * 原子 UPSERT：不存在则插入，存在则更新 cache_value 和 expire_at
     * <p>解决并发场景下 delete+insert 的 UNIQUE 约束冲突问题</p>
     */
    @Insert("INSERT INTO tbl_local_cache (cache_key, cache_value, expire_at, create_time) " +
            "VALUES (#{cacheKey}, #{cacheValue}, #{expireAt}, strftime('%s','now')) " +
            "ON CONFLICT(cache_key) DO UPDATE SET " +
            "cache_value = excluded.cache_value, " +
            "expire_at = excluded.expire_at")
    int upsert(LocalCache cache);

    /**
     * 原子 INSERT OR IGNORE：仅当 key 不存在时插入
     * <p>用于分布式锁的原子获取，返回 1 表示锁获取成功，0 表示 key 已存在</p>
     */
    @Insert("INSERT INTO tbl_local_cache (cache_key, cache_value, expire_at, create_time) " +
            "VALUES (#{cacheKey}, #{cacheValue}, #{expireAt}, strftime('%s','now')) " +
            "ON CONFLICT(cache_key) DO NOTHING")
    int insertIfAbsent(LocalCache cache);

    /**
     * 批量删除过期缓存
     */
    @Insert("DELETE FROM tbl_local_cache WHERE expire_at > 0 AND expire_at < strftime('%s','now')")
    int deleteExpired();

}
