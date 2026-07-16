package com.matrix.local.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.matrix.common.enums.RedisKey;
import com.matrix.local.service.LocalCacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Primary
@Component
public class LocalChatContext {

    private static final Cache<String, Boolean> CONVERSATION_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .maximumSize(30000)
            .build();

    @Resource
    private LocalCacheService localCacheService;

    /**
     * 用户对话中
     */
    public void inConversation(Long userId, Long sessionId) {
        if (null == sessionId) {
            return;
        }
        log.warn("\n\n======================\n\n\tS T A R T: inConversation\n\n======================\n\n");
        RedisKey redisKey = RedisKey.CONVERSATION;
        String key = redisKey.generateKey(userId, sessionId);
        String value = System.currentTimeMillis() + "";
        localCacheService.put(key, value, redisKey.getTtl());
        String cacheKey = userId + "@@@" + sessionId;
        CONVERSATION_CACHE.put(cacheKey, true);
    }

    /**
     * 用户停止对话
     */
    public void stopConversation(Long userId, Long sessionId) {
        log.warn("\n\n======================\n\n\tS T O P: stopConversation\n\n======================\n\n");
        RedisKey redisKey = RedisKey.CONVERSATION;
        String key = redisKey.generateKey(userId, sessionId);
        localCacheService.delete(key);
        String cacheKey = userId + "@@@" + sessionId;
        CONVERSATION_CACHE.put(cacheKey, false);
    }

    /**
     * 用户是否正在对话
     */
    public boolean isConversation(Long userId, Long sessionId) {
        if (null == sessionId) {
            return false;
        }
        RedisKey redisKey = RedisKey.CONVERSATION;
        String key = redisKey.generateKey(userId, sessionId);
        return localCacheService.hasKey(key);
    }

    /**
     * 用户是否正在对话（带 Caffeine 本地缓存）
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
            // 2. 缓存未命中，查询 SQLite (LocalCacheService)
            RedisKey redisKey = RedisKey.CONVERSATION;
            String key = redisKey.generateKey(userId, sessionId);
            boolean result = localCacheService.hasKey(key);
            CONVERSATION_CACHE.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            CONVERSATION_CACHE.put(cacheKey, true);
            return true;
        }
    }

}
