package com.matrix.service.context;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.enums.RedisKey;
import com.matrix.service.cache.ServiceCache;
import com.matrix.service.service.agent.schema.TaskChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @description 任务模式上下文
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class TaskPatternContext {

    @Resource
    private ServiceCache serviceCache;

    public void clear(long userId, long sessionId) {
        // task chain
        RedisKey redisKey = RedisKey.TASK_PATTERN_CHAIN;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.delete(key);
        // task complete
        redisKey = RedisKey.TASK_PATTERN_COMPLETE;
        key = redisKey.generateKey(userId, sessionId);
        serviceCache.delete(key);
    }

    /**
     * @description 设置任务链
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setTaskChain(long userId, long sessionId, TaskChain taskChain) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_CHAIN;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.set(key, taskChain.toString(), redisKey.getTtl());
    }

    /**
     * @description 获取任务链
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public TaskChain getTaskChain(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_CHAIN;
        String key = redisKey.generateKey(userId, sessionId);
        String taskChain = serviceCache.get(key);
        return StringUtils.isBlank(taskChain) ? null : JSON.parseObject(taskChain, TaskChain.class);
    }

    /**
     * @description 设置任务已完成
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setTaskComplete(long userId, long sessionId, String taskName) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_COMPLETE;
        String key = redisKey.generateKey(userId, sessionId);
        serviceCache.getHash().put(key, taskName, "COMPLETE", redisKey.getTtl());
    }

    /**
     * @description 获取已完成任务集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Set<String> getTaskComplete(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.TASK_PATTERN_COMPLETE;
        String key = redisKey.generateKey(userId, sessionId);
        Set<String> keys = serviceCache.getHash().keys(key);
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptySet();
        }
        return new HashSet<>(keys);
    }

    /**
     * @description 判断任务是否已完成
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public boolean isTaskComplete(long userId, long sessionId, String taskName) {
        return this.getTaskComplete(userId, sessionId).contains(taskName);
    }

}


