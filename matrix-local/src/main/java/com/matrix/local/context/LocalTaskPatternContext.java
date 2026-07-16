package com.matrix.local.context;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.enums.RedisKey;
import com.matrix.local.service.LocalCacheService;
import com.matrix.service.service.agent.schema.TaskChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description 本地任务模式上下文
 * <p> 基于 LocalCacheService (tbl_local_cache) 实现，替代 RedisTemplate </p>
 *
 * @author 陈晨
 */
@Slf4j
@Primary
@Component
public class LocalTaskPatternContext {

    @Resource
    private LocalCacheService localCacheService;

    /**
     * @description 清除任务链与已完成任务记录
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void clear(long userId, long sessionId) {
        // task chain
        String chainKey = RedisKey.TASK_PATTERN_CHAIN.generateKey(userId, sessionId);
        localCacheService.delete(chainKey);
        // task complete: 删除所有匹配前缀的已完成任务
        String completePrefix = buildCompletePrefix(userId, sessionId);
        List<String> completeKeys = localCacheService.keys(completePrefix + "*");
        if (!CollectionUtils.isEmpty(completeKeys)) {
            completeKeys.forEach(key -> localCacheService.delete(key));
        }
    }

    /**
     * @description 设置任务链
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setTaskChain(long userId, long sessionId, TaskChain taskChain) {
        String key = RedisKey.TASK_PATTERN_CHAIN.generateKey(userId, sessionId);
        localCacheService.put(key, taskChain.toString(), RedisKey.TASK_PATTERN_CHAIN.getTtl());
    }

    /**
     * @description 获取任务链
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public TaskChain getTaskChain(long userId, long sessionId) {
        String key = RedisKey.TASK_PATTERN_CHAIN.generateKey(userId, sessionId);
        String taskChainStr = localCacheService.get(key);
        if (StringUtils.isBlank(taskChainStr)) {
            return null;
        }
        try {
            return JSON.parseObject(taskChainStr, TaskChain.class);
        } catch (Exception e) {
            log.warn("解析任务链 JSON 失败, key={}, value={}", key, taskChainStr, e);
            return null;
        }
    }

    /**
     * @description 设置任务已完成
     * <p> 以 flat KV 方式存储，key = pattern:complete:{userId}:{sessionId}:{taskName} </p>
     *
     * @author 陈晨
     */
    public void setTaskComplete(long userId, long sessionId, String taskName) {
        String key = buildCompleteKey(userId, sessionId, taskName);
        localCacheService.put(key, "COMPLETE", RedisKey.TASK_PATTERN_COMPLETE.getTtl());
    }

    /**
     * @description 获取已完成任务集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Set<String> getTaskComplete(long userId, long sessionId) {
        String prefix = buildCompletePrefix(userId, sessionId);
        List<String> keys = localCacheService.keys(prefix + "*");
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptySet();
        }
        return keys.stream()
                .map(k -> k.substring(prefix.length()))
                .collect(Collectors.toSet());
    }

    /**
     * @description 判断任务是否已完成
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public boolean isTaskComplete(long userId, long sessionId, String taskName) {
        String key = buildCompleteKey(userId, sessionId, taskName);
        return localCacheService.hasKey(key);
    }

    /**
     * @description 构建已完成任务前缀
     * <p> 格式: matrix:task:pattern:complete:{userId}:{sessionId}: </p>
     */
    private String buildCompletePrefix(long userId, long sessionId) {
        return RedisKey.TASK_PATTERN_COMPLETE.generateKey(userId, sessionId) + ":";
    }

    /**
     * @description 构建已完成任务完整 Key
     * <p> 格式: matrix:task:pattern:complete:{userId}:{sessionId}:{taskName} </p>
     */
    private String buildCompleteKey(long userId, long sessionId, String taskName) {
        return buildCompletePrefix(userId, sessionId) + taskName;
    }

}
