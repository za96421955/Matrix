package com.matrix.service.context;

import com.matrix.common.enums.CodingPattern;
import com.matrix.common.enums.RedisKey;
import com.matrix.service.cache.ServiceCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * @description 模式上下文
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class PatternContext {

    @Resource
    private ServiceCache serviceCache;

    /**
     * @description 清除模式缓存
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void clear(long userId, long sessionId) {
        this.clearPattern(userId, sessionId);
    }
    public void clearPattern(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
        // 同时清除 smart
        this.clearSmart(userId, sessionId);
    }
    public void clearIsSmart(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 isSmart, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.TASK_PATTERN_IS_SMART;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    public void clearSmart(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 smart, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.TASK_PATTERN_SMART;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
        // 同时清除 isSmart
        this.clearIsSmart(userId, sessionId);
        // 同时清除 plan
        this.clearPlan(userId, sessionId);
    }
    public void clearSets(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 sets, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.TASK_PATTERN_STEPS;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    public void clearPlan(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 plan, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.TASK_PATTERN_PLAN;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
        // 同时清除 sets
        this.clearSets(userId, sessionId);
    }

    /**
     * @description 缓存: pattern
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setPattern(long userId, long sessionId, String pattern) {
        log.info("[模式缓存] 设置模式, userId={}, sessionId={}, pattern={}",
                userId, sessionId, pattern);
        RedisKey redisKey = RedisKey.PATTERN;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, pattern, redisKey.getTtl());
    }
    public String getPattern(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: isSmart
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setIsSmart(long userId, long sessionId, String isSmart) {
        log.info("[模式缓存] 设置模式 isSmart, userId={}, sessionId={}, isSmart={}",
                userId, sessionId, isSmart);
        RedisKey redisKey = RedisKey.TASK_PATTERN_IS_SMART;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, isSmart, redisKey.getTtl());
    }
    public String getIsSmart(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_IS_SMART;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: smart
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setSmart(long userId, long sessionId, String smart) {
        log.info("[模式缓存] 设置模式 smart, userId={}, sessionId={}, isSmart={}",
                userId, sessionId, smart);
        RedisKey redisKey = RedisKey.TASK_PATTERN_SMART;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, smart, redisKey.getTtl());
    }
    public String getSmart(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_SMART;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: sets
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setSets(long userId, long sessionId, String sets) {
        log.info("[模式缓存] 设置模式 sets, userId={}, sessionId={}, isSmart={}",
                userId, sessionId, sets);
        RedisKey redisKey = RedisKey.TASK_PATTERN_STEPS;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, sets, redisKey.getTtl());
    }
    public String getSets(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_STEPS;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: plan
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setPlan(long userId, long sessionId, String plan) {
        log.info("[模式缓存] 设置模式 plan, userId={}, sessionId={}, isSmart={}",
                userId, sessionId, plan);
        RedisKey redisKey = RedisKey.TASK_PATTERN_PLAN;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, plan, redisKey.getTtl());
    }
    public String getPlan(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_PLAN;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

}


