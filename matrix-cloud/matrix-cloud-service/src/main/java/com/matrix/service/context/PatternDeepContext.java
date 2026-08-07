package com.matrix.service.context;

import com.matrix.common.enums.RedisKey;
import com.matrix.common.util.ContentUtil;
import com.matrix.service.cache.ServiceCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * @description 深度模式上下文
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class PatternDeepContext {

    @Resource
    private ServiceCache serviceCache;
    @Resource
    private PatternContext patternContext;

    /**
     * @description 清除缓存
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void clear(long userId, long sessionId) {
        patternContext.clearStatus(userId, sessionId);
        this.clearFlag(userId, sessionId);
        this.clearGoal(userId, sessionId);
        this.clearFences(userId, sessionId);
        this.clearInfoActions(userId, sessionId);
        this.clearPlan(userId, sessionId);
        this.clearActions(userId, sessionId);
    }

    /**
     * @description 缓存: flag
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setFlag(long userId, long sessionId, String flag) {
        log.info("[模式缓存] 设置缓存 flag, userId={}, sessionId={}, flag={}",
                userId, sessionId, flag);
        RedisKey redisKey = RedisKey.PATTERN_DEEP_FLAG;
        String key = redisKey.generateKey(userId, sessionId);
        String hashKey = ContentUtil.sha256Hex(flag);
        serviceCache.getHash().put(key, hashKey, "1", redisKey.getTtl());
    }
    public String getFlag(long userId, long sessionId, String flag) {
        RedisKey redisKey = RedisKey.PATTERN_DEEP_FLAG;
        String key = redisKey.generateKey(userId, sessionId);
        String hashKey = ContentUtil.sha256Hex(flag);
        return serviceCache.getHash().get(key, hashKey);
    }
    public void clearFlag(long userId, long sessionId) {
        log.info("[模式缓存] 清除缓存 flag, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_DEEP_FLAG;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }
    /** primary */
    public void setPrimary(long userId, long sessionId) {
        this.setFlag(userId, sessionId, "FLAG_PRIMARY");
    }
    public boolean isPrimary(long userId, long sessionId) {
        String flag = this.getFlag(userId, sessionId, "FLAG_PRIMARY");
        return StringUtils.isNotBlank(flag);
    }
    /** infos */
    public void setInfos(long userId, long sessionId) {
        this.setFlag(userId, sessionId, "FLAG_INFOS");
    }
    public boolean isInfos(long userId, long sessionId) {
        String flag = this.getFlag(userId, sessionId, "FLAG_INFOS");
        return StringUtils.isNotBlank(flag);
    }
    /** infoReview */
    public void setInfoReview(long userId, long sessionId) {
        this.setFlag(userId, sessionId, "FLAG_INFO_REVIEW");
    }
    public boolean isInfoReview(long userId, long sessionId) {
        String flag = this.getFlag(userId, sessionId, "FLAG_INFO_REVIEW");
        return StringUtils.isNotBlank(flag);
    }
    /** forwardLooking */
    public void setForwardLooking(long userId, long sessionId) {
        this.setFlag(userId, sessionId, "FLAG_FORWARD_LOOKING");
    }
    public boolean isForwardLooking(long userId, long sessionId) {
        String flag = this.getFlag(userId, sessionId, "FLAG_FORWARD_LOOKING");
        return StringUtils.isNotBlank(flag);
    }
    /** fenceCheck */
    public void setFenceCheck(long userId, long sessionId) {
        this.setFlag(userId, sessionId, "FLAG_FENCE_CHECK");
    }
    public boolean isFenceCheck(long userId, long sessionId) {
        String flag = this.getFlag(userId, sessionId, "FLAG_FENCE_CHECK");
        return StringUtils.isNotBlank(flag);
    }
    /** action */
    public void setAction(long userId, long sessionId, String action) {
        this.setFlag(userId, sessionId, action);
    }
    public boolean isAction(long userId, long sessionId, String action) {
        String flag = this.getFlag(userId, sessionId, action);
        return StringUtils.isNotBlank(flag);
    }

    /**
     * @description 缓存: goal
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setGoal(long userId, long sessionId, String goal) {
        log.info("[模式缓存] 设置缓存 goal, userId={}, sessionId={}, goal={}",
                userId, sessionId, goal);
        RedisKey redisKey = RedisKey.PATTERN_DEEP_GOAL;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, goal, redisKey.getTtl());
    }
    public String getGoal(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_DEEP_GOAL;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }
    public void clearGoal(long userId, long sessionId) {
        log.info("[模式缓存] 清除缓存 goal, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_DEEP_GOAL;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }

    /**
     * @description 缓存: fences
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setFences(long userId, long sessionId, String fences) {
        log.info("[模式缓存] 设置缓存 fences, userId={}, sessionId={}, fences={}",
                userId, sessionId, fences);
        RedisKey redisKey = RedisKey.PATTERN_DEEP_FENCES;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, fences, redisKey.getTtl());
    }
    public String getFences(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_DEEP_FENCES;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }
    public void clearFences(long userId, long sessionId) {
        log.info("[模式缓存] 清除缓存 fences, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_DEEP_FENCES;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }

    /**
     * @description 缓存: infoActions
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setInfoActions(long userId, long sessionId, String infoActions) {
        log.info("[模式缓存] 设置缓存 infoActions, userId={}, sessionId={}, infoActions={}",
                userId, sessionId, infoActions);
        RedisKey redisKey = RedisKey.PATTERN_DEEP_INFO_ACTIONS;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, infoActions, redisKey.getTtl());
    }
    public String getInfoActions(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_DEEP_INFO_ACTIONS;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }
    public void clearInfoActions(long userId, long sessionId) {
        log.info("[模式缓存] 清除缓存 infoActions, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_DEEP_INFO_ACTIONS;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }

    /**
     * @description 缓存: plan
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setPlan(long userId, long sessionId, String plan) {
        log.info("[模式缓存] 设置缓存 plan, userId={}, sessionId={}, plan={}",
                userId, sessionId, plan);
        RedisKey redisKey = RedisKey.PATTERN_DEEP_PLAN;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, plan, redisKey.getTtl());
    }
    public String getPlan(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_DEEP_PLAN;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }
    public void clearPlan(long userId, long sessionId) {
        log.info("[模式缓存] 清除缓存 plan, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_DEEP_PLAN;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }

    /**
     * @description 缓存: actions
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setActions(long userId, long sessionId, String actions) {
        log.info("[模式缓存] 设置缓存 actions, userId={}, sessionId={}, actions={}",
                userId, sessionId, actions);
        RedisKey redisKey = RedisKey.PATTERN_DEEP_ACTIONS;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, actions, redisKey.getTtl());
    }
    public String getActions(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.PATTERN_DEEP_ACTIONS;
        String key = redisKey.generateKey(userId, sessionId);
        return serviceCache.get(key);
    }
    public void clearActions(long userId, long sessionId) {
        log.info("[模式缓存] 清除缓存 actions, userId={}, sessionId={}", userId, sessionId);
        try {
            RedisKey redisKey = RedisKey.PATTERN_DEEP_ACTIONS;
            String key = redisKey.generateKey(userId, sessionId);
            serviceCache.delete(key);
        } catch (Exception ignore) {}
    }

}


