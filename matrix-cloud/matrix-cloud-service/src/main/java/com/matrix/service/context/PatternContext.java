package com.matrix.service.context;

import com.matrix.common.enums.RedisKey;
import com.matrix.common.util.ContentUtil;
import com.matrix.service.cache.ServiceCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Set;

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
        // 同时清除 status
        this.clearStatus(userId, sessionId);
        // 同时清除 consume
        this.clearConsume(userId, sessionId);
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
    public void clearStatus(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 status, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_STATUS;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    public void clearConsume(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 consume, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_CONSUME;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    public void clearIsSmart(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 isSmart, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_IS_SMART;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    public void clearSmart(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 smart, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_SMART;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
        // 同时清除 isSmart
        this.clearIsSmart(userId, sessionId);
        // 同时清除 plan
        this.clearPlan(userId, sessionId);
    }
    public void clearPlanMode(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 planMode, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_PLAN_MODE;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    public void clearPlan(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 plan, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_PLAN;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
        // 同时清除 planMode
        this.clearPlanMode(userId, sessionId);
        // 同时清除 actions
        this.clearActions(userId, sessionId);
    }
    public void clearActionMode(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 actionMode, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_ACTION_MODE;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    public void clearActions(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 actions, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_ACTIONS;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
        // 同时清除 actionMode
        this.clearActionMode(userId, sessionId);
        // 同时清除 actionResult
        this.clearResult(userId, sessionId);
    }
    public void clearResult(long userId, long sessionId) {
        log.info("[模式缓存] 清除模式 result, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_RESULT;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
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
     * @description 缓存: status
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setStatus(long userId, long sessionId, String status) {
        log.info("[模式缓存] 设置模式 status, userId={}, sessionId={}, status={}",
                userId, sessionId, status);
        RedisKey redisKey = RedisKey.PATTERN_STATUS;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, status, redisKey.getTtl());
        // 上一步结束
        this.end(userId, sessionId);
        // 下一步开始
        this.begin(userId, sessionId);
    }
    public String getStatus(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_STATUS;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: status
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void begin(long userId, long sessionId) {
        log.info("[模式缓存] 设置模式 consume, userId={}, sessionId={}",
                userId, sessionId);
        RedisKey redisKey = RedisKey.PATTERN_CONSUME;
        String key = redisKey.generateKey(userId, sessionId);
        Set<String> keys = serviceCache.keys(key);
        if (CollectionUtils.isEmpty(keys)) {
            serviceCache.getHash().put(key, "begin", System.currentTimeMillis() + "", redisKey.getTtl());
            serviceCache.getHash().put(key, "consume", "0", redisKey.getTtl());
        }
        serviceCache.getHash().put(key, "curr", System.currentTimeMillis() + "", redisKey.getTtl());
    }
    public void end(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_CONSUME;
        String key = redisKey.generateKey(userId, sessionId);
        Set<String> keys = serviceCache.keys(key);
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        long consume = this.getTotalConsume(userId, sessionId) + this.getCurrConsume(userId, sessionId);
        serviceCache.getHash().put(key, "consume", consume + "", redisKey.getTtl());
        serviceCache.getHash().put(key, "curr", "0", redisKey.getTtl());
    }
    public long getTotalConsume(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_CONSUME;
        String key = redisKey.generateKey(userId, sessionId);
        Set<String> keys = serviceCache.keys(key);
        if (CollectionUtils.isEmpty(keys)) {
            return 0;
        }
        return Long.parseLong(serviceCache.getHash().get(key, "consume"));
    }
    public long getCurrConsume(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_CONSUME;
        String key = redisKey.generateKey(userId, sessionId);
        Set<String> keys = serviceCache.keys(key);
        if (CollectionUtils.isEmpty(keys)) {
            return 0;
        }
        long curr = Long.parseLong(serviceCache.getHash().get(key, "curr"));
        return System.currentTimeMillis() - curr;
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
        // 清除后续步骤缓存【特殊顺序，先清理，后设置】
        this.clearSmart(userId, sessionId);
        // 设置
        RedisKey redisKey = RedisKey.PATTERN_IS_SMART;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, isSmart, redisKey.getTtl());
    }
    public String getIsSmart(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_IS_SMART;
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
        log.info("[模式缓存] 设置模式 smart, userId={}, sessionId={}, smart={}",
                userId, sessionId, smart);
        RedisKey redisKey = RedisKey.PATTERN_SMART;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, smart, redisKey.getTtl());
        // 清除后续步骤缓存
        this.clearPlan(userId, sessionId);
    }
    public String getSmart(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_SMART;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: planMode
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setPlanMode(long userId, long sessionId, String planMode) {
        log.info("[模式缓存] 设置模式 planMode, userId={}, sessionId={}, planMode={}",
                userId, sessionId, planMode);
        // 清除后续步骤缓存【特殊顺序，先清理，后设置】
        this.clearPlan(userId, sessionId);
        // 设置
        RedisKey redisKey = RedisKey.PATTERN_PLAN_MODE;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, planMode, redisKey.getTtl());
    }
    public String getPlanMode(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_PLAN_MODE;
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
        log.info("[模式缓存] 设置模式 plan, userId={}, sessionId={}, plan={}",
                userId, sessionId, plan);
        RedisKey redisKey = RedisKey.PATTERN_PLAN;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, plan, redisKey.getTtl());
        // 清除后续步骤缓存
        this.clearActions(userId, sessionId);
    }
    public String getPlan(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_PLAN;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: actionMode
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setActionMode(long userId, long sessionId, String actionMode) {
        log.info("[模式缓存] 设置模式 actionMode, userId={}, sessionId={}, actionMode={}",
                userId, sessionId, actionMode);
        // 清除后续步骤缓存【特殊顺序，先清理，后设置】
        this.clearActions(userId, sessionId);
        // 设置
        RedisKey redisKey = RedisKey.PATTERN_ACTION_MODE;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, actionMode, redisKey.getTtl());
    }
    public String getActionMode(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_ACTION_MODE;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: actions
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setActions(long userId, long sessionId, String actions) {
        log.info("[模式缓存] 设置模式 actions, userId={}, sessionId={}, actions={}",
                userId, sessionId, actions);
        RedisKey redisKey = RedisKey.PATTERN_ACTIONS;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, actions, redisKey.getTtl());
    }
    public String getActions(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_ACTIONS;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }

    /**
     * @description 缓存: result
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setResult(long userId, long sessionId, String action, String result) {
        log.info("[模式缓存] 设置模式 result, userId={}, sessionId={}, result={}",
                userId, sessionId, result);
        RedisKey redisKey = RedisKey.PATTERN_RESULT;
        String key = redisKey.generateKey(userId, sessionId);
        String hashKey = ContentUtil.sha256Hex(action);
        serviceCache.getHash().put(key, hashKey, result, redisKey.getTtl());
    }
    public String getResult(long userId, long sessionId, String action) {
        RedisKey redisKey = RedisKey.PATTERN_RESULT;
        String key = redisKey.generateKey(userId, sessionId);
        String hashKey = ContentUtil.sha256Hex(action);
        return serviceCache.getHash().get(key, hashKey);
    }

}


