package com.matrix.service.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.matrix.common.enums.RedisKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 缓存上下文服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class ChatContext {
    private static final Cache<String, Boolean> CONVERSATION_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .maximumSize(30000)  // 缓存 3 秒
            .build();

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * @description 用户对话中
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void inConversation(Long userId, Long sessionId) {
        if (null == sessionId) {
            return;
        }
        log.warn("\n\n======================\n\n\tS T A R T: inConversation\n\n======================\n\n");
        RedisKey redisKey = RedisKey.CONVERSATION;
        String key = redisKey.generateKey(userId, sessionId);
        String value = System.currentTimeMillis() + "";
        redisTemplate.opsForValue().set(key, value, redisKey.getTtl(), TimeUnit.SECONDS);
        String cacheKey = userId + "@@@" + sessionId;
        CONVERSATION_CACHE.put(cacheKey, true);
    }

    /**
     * @description 用户停止对话
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void stopConversation(Long userId, Long sessionId) {
        log.warn("\n\n======================\n\n\tS T O P: stopConversation\n\n======================\n\n");
        RedisKey redisKey = RedisKey.CONVERSATION;
        String key = redisKey.generateKey(userId, sessionId);
        redisTemplate.delete(key);
        String cacheKey = userId + "@@@" + sessionId;
        CONVERSATION_CACHE.put(cacheKey, false);
    }

    /**
     * @description 用户是否正在对话
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public boolean isConversation(Long userId, Long sessionId) {
        if (null == sessionId) {
            return false;
        }
        RedisKey redisKey = RedisKey.CONVERSATION;
        String key = redisKey.generateKey(userId, sessionId);
        Object value = redisTemplate.opsForValue().get(key);
        return null != value;
    }

    /**
     * @description 用户是否正在对话
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public boolean isConversationByCache(Long userId, Long sessionId) {
        if (null == sessionId) {
            return false;
        }
        // -1L 表示系统任务（如定时任务），始终允许执行
        if (-1L == sessionId) {
            return true;
        }
        // 1. 先查本地缓存
        String cacheKey = userId + "@@@" + sessionId;
        Boolean cached = CONVERSATION_CACHE.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            // 2. 缓存未命中，查询 Redis
            RedisKey redisKey = RedisKey.CONVERSATION;
            String key = redisKey.generateKey(userId, sessionId);
            Object value = redisTemplate.opsForValue().get(key);
            boolean result = null != value;
            CONVERSATION_CACHE.put(cacheKey, result);
            return result;
        } catch (Exception e) {
//            log.error(e.getMessage(), e);
            CONVERSATION_CACHE.put(cacheKey, true);
            return true;
        }
    }

}


