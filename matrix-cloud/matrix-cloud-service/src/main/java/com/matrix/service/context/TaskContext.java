package com.matrix.service.context;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.TaskStatus;
import com.matrix.common.constant.TaskType;
import com.matrix.common.enums.RedisKey;
import com.matrix.service.cache.ServiceCache;
import com.matrix.service.dal.entity.TaskInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @description 任务上下文，统一封装 Redis 层对 TaskInfo 的所有操作
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class TaskContext {

    @Resource
    private ServiceCache serviceCache;

    /**
     * 判断是否为任务终态（完成后需删除 Redis key）
     */
    private boolean isFinalStatus(String status) {
        return TaskStatus.COMPLETED.equals(status)
                || TaskStatus.TIMEOUT.equals(status)
                || TaskStatus.EXCEPTION.equals(status);
    }

    // ==================== 新增任务 ====================

    /**
     * 新增任务
     * <p>TaskInfo 序列化为 JSON 写入 Redis Hash；若 type=USER_AUTH，将 taskId 加入待授权 Set</p>
     *
     * @param taskInfo 任务信息
     */
    public void insert(TaskInfo taskInfo) {
        if (null == taskInfo || StringUtils.isBlank(taskInfo.getTaskId())) {
            log.warn("[TaskContext] insert 失败，taskInfo 或 taskId 为空");
            return;
        }

        // 1. 写入 Hash
        String hashKey = RedisKey.TASK_INFO.generateKey(taskInfo.getTaskId());
        serviceCache.set(hashKey, JSONObject.toJSONString(taskInfo), RedisKey.TASK_INFO.getTtl());
        log.debug("[TaskContext] insert Hash, key={}, taskId={}", hashKey, taskInfo.getTaskId());

        // 2. 若为 USER_AUTH 类型，将 taskId 加入待授权 Set
        if (TaskType.USER_AUTH.equals(taskInfo.getType())) {
            String setKey = RedisKey.TASK_WAITING_AUTH_LIST.generateKey(taskInfo.getUserId());
            serviceCache.getSet().add(setKey, taskInfo.getTaskId(), RedisKey.TASK_WAITING_AUTH_LIST.getTtl());
            log.debug("[TaskContext] insert Set, key={}, taskId={}", setKey, taskInfo.getTaskId());
        }
    }

    // ==================== 查询任务 ====================

    /**
     * 根据 taskId 查询任务详情
     *
     * @param taskId 任务 ID
     * @return 任务详情，不存在返回 null
     */
    public TaskInfo getTaskInfo(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            return null;
        }
        String hashKey = RedisKey.TASK_INFO.generateKey(taskId);
        String value = serviceCache.get(hashKey);
        if (null == value) {
            return null;
        }
        try {
            return JSONObject.parseObject(value, TaskInfo.class);
        } catch (Exception e) {
            log.error("[TaskContext] getTaskInfo 反序列化失败, taskId={}", taskId, e);
            return null;
        }
    }

    /**
     * 查询用户等待授权的任务列表
     *
     * @param userId 用户 ID
     * @return 等待授权的任务列表（status=RUNNING）
     */
    public List<TaskInfo> getWaitingAuthList(Long userId) {
        if (null == userId) {
            return Collections.emptyList();
        }
        String setKey = RedisKey.TASK_WAITING_AUTH_LIST.generateKey(userId);
        Set<String> taskIds = serviceCache.getSet().getAll(setKey);
        if (CollectionUtils.isEmpty(taskIds)) {
            return Collections.emptyList();
        }
        List<TaskInfo> result = new ArrayList<>();
        for (String taskId : taskIds) {
            TaskInfo taskInfo = this.getTaskInfo(taskId);
            // 只返回状态为 RUNNING 的任务
            if (null != taskInfo && TaskStatus.RUNNING.equals(taskInfo.getStatus())) {
                result.add(taskInfo);
            }
        }
        // 按创建时间升序排序
        result.sort(Comparator.comparing(TaskInfo::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    // ==================== 更新状态 ====================

    /**
     * 更新任务状态和结果
     * <p>从 Hash 读取 -> 修改字段 -> 写回 Hash（续期 TTL）。若进入终态，自动删除 Redis key。</p>
     *
     * @param userId 用户 ID
     * @param taskId 任务 ID
     * @param status 新状态
     * @param result 执行结果（可选）
     */
    public void updateStatusAndResult(Long userId, String taskId, String status, String result) {
        if (null == userId || StringUtils.isBlank(taskId) || StringUtils.isBlank(status)) {
            log.warn("[TaskContext] updateStatusAndResult 参数不完整, userId={}, taskId={}, status={}", userId, taskId, status);
            return;
        }

        // 1. 读取当前任务
        TaskInfo taskInfo = this.getTaskInfo(taskId);
        if (null == taskInfo) {
            log.warn("[TaskContext] updateStatusAndResult 任务不存在, taskId={}", taskId);
            return;
        }

        // 2. 修改字段
        taskInfo.setStatus(status);
        taskInfo.setUpdateTime(new Date());
        if (StringUtils.isNotBlank(result)) {
            taskInfo.setResult(result);
        }

        // 3. 写回 Hash
        String hashKey = RedisKey.TASK_INFO.generateKey(taskId);
        serviceCache.set(hashKey, JSONObject.toJSONString(taskInfo), RedisKey.TASK_INFO.getTtl());
        log.debug("[TaskContext] updateStatusAndResult, taskId={}, status={}", taskId, status);

        // 4. 若进入终态，清理 Redis key
        if (this.isFinalStatus(status)) {
            this.completeTask(taskId, userId);
        }
    }

    /**
     * 仅更新任务状态
     *
     * @param userId 用户 ID
     * @param taskId 任务 ID
     * @param status 新状态
     */
    public void updateStatus(Long userId, String taskId, String status) {
        this.updateStatusAndResult(userId, taskId, status, null);
    }

    /**
     * @description 任务完成清理
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void completeTask(String taskId, Long userId) {
        if (StringUtils.isBlank(taskId)) {
            return;
        }

        // 1. 删除 Hash key
        String hashKey = RedisKey.TASK_INFO.generateKey(taskId);
        serviceCache.delete(hashKey);
        log.debug("[TaskContext] 删除 Hash key={}", hashKey);

        // 2. 从待授权 Set 中移除 taskId（若存在）
        if (null != userId) {
            String setKey = RedisKey.TASK_WAITING_AUTH_LIST.generateKey(userId);
            serviceCache.getSet().remove(setKey, taskId);
            log.debug("[TaskContext] 从 Set 移除, key={}, taskId={}", setKey, taskId);
        }
    }

}
