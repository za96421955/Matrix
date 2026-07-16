package com.matrix.local.context;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.TaskStatus;
import com.matrix.common.constant.TaskType;
import com.matrix.local.service.LocalCacheService;
import com.matrix.service.dal.entity.TaskInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 本地任务上下文，基于 LocalCacheService（SQLite tbl_local_cache）替代 Redis
 * <p>与原始 TaskContext 方法签名保持一致，通过 @Primary 优先生效</p>
 *
 * @author 陈晨
 */
@Slf4j
@Primary
@Component
public class LocalTaskContext {

    /** Hash 中存储 TaskInfo JSON 的字段名 */
    private static final String FIELD_TASK_INFO = "taskInfo";

    /** 任务信息 Hash 缓存前缀 */
    private static final String TASK_INFO_PREFIX = "task:info:";

    /** 等待授权 Set 缓存前缀 */
    private static final String WAITING_AUTH_PREFIX = "task:waiting-auth:";

    /** 默认 TTL（秒）= 1小时 */
    private static final long DEFAULT_TTL = 3600L;

    @Resource
    private LocalCacheService localCacheService;

    // ==================== 终态判断 ====================

    /**
     * 判断是否为任务终态（完成后需清理缓存）
     */
    public boolean isFinalStatus(String status) {
        return TaskStatus.COMPLETED.equals(status)
                || TaskStatus.TIMEOUT.equals(status)
                || TaskStatus.EXCEPTION.equals(status);
    }

    // ==================== 新增任务 ====================

    /**
     * 新增任务
     * <p>TaskInfo 序列化为 JSON 写入 Hash；若 type=USER_AUTH，将 taskId 加入待授权 Set</p>
     *
     * @param taskInfo 任务信息
     */
    public void insert(TaskInfo taskInfo) {
        if (null == taskInfo || StringUtils.isBlank(taskInfo.getTaskId())) {
            log.warn("[LocalTaskContext] insert 失败，taskInfo 或 taskId 为空");
            return;
        }

        // 1. 写入 Hash
        String hashKey = TASK_INFO_PREFIX + taskInfo.getTaskId();
        localCacheService.putHash(hashKey, FIELD_TASK_INFO, JSONObject.toJSONString(taskInfo));
        // putHash 内部已处理 TTL，确保 Hash 整体有过期时间
        log.debug("[LocalTaskContext] insert Hash, key={}, taskId={}", hashKey, taskInfo.getTaskId());

        // 2. 若为 USER_AUTH 类型，将 taskId 加入待授权 Set
        if (TaskType.USER_AUTH.equals(taskInfo.getType())) {
            String setKey = WAITING_AUTH_PREFIX + taskInfo.getUserId();
            addToSet(setKey, taskInfo.getTaskId());
            log.debug("[LocalTaskContext] insert Set, key={}, taskId={}", setKey, taskInfo.getTaskId());
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
        String hashKey = TASK_INFO_PREFIX + taskId;
        String value = localCacheService.getHash(hashKey, FIELD_TASK_INFO);
        if (null == value) {
            return null;
        }
        try {
            return JSONObject.parseObject(value, TaskInfo.class);
        } catch (Exception e) {
            log.error("[LocalTaskContext] getTaskInfo 反序列化失败, taskId={}", taskId, e);
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
        String setKey = WAITING_AUTH_PREFIX + userId;
        Set<String> taskIds = getSetMembers(setKey);
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
     * <p>从 Hash 读取 -> 修改字段 -> 写回 Hash。若进入终态，自动清理缓存。</p>
     *
     * @param userId 用户 ID
     * @param taskId 任务 ID
     * @param status 新状态
     * @param result 执行结果（可选）
     */
    public void updateStatusAndResult(Long userId, String taskId, String status, String result) {
        if (null == userId || StringUtils.isBlank(taskId) || StringUtils.isBlank(status)) {
            log.warn("[LocalTaskContext] updateStatusAndResult 参数不完整, userId={}, taskId={}, status={}", userId, taskId, status);
            return;
        }

        // 1. 读取当前任务
        TaskInfo taskInfo = this.getTaskInfo(taskId);
        if (null == taskInfo) {
            log.warn("[LocalTaskContext] updateStatusAndResult 任务不存在, taskId={}", taskId);
            return;
        }

        // 2. 修改字段
        taskInfo.setStatus(status);
        taskInfo.setUpdateTime(new Date());
        if (StringUtils.isNotBlank(result)) {
            taskInfo.setResult(result);
        }

        // 3. 写回 Hash
        String hashKey = TASK_INFO_PREFIX + taskId;
        localCacheService.putHash(hashKey, FIELD_TASK_INFO, JSONObject.toJSONString(taskInfo));
        log.debug("[LocalTaskContext] updateStatusAndResult, taskId={}, status={}", taskId, status);

        // 4. 若进入终态，清理缓存
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
     * 任务完成清理
     * <p>删除 Hash key，并从待授权 Set 中移除 taskId</p>
     *
     * @param taskId 任务 ID
     * @param userId 用户 ID
     */
    public void completeTask(String taskId, Long userId) {
        if (StringUtils.isBlank(taskId)) {
            return;
        }

        // 1. 删除 Hash key
        String hashKey = TASK_INFO_PREFIX + taskId;
        localCacheService.delete(hashKey);
        log.debug("[LocalTaskContext] 删除 Hash key={}", hashKey);

        // 2. 从待授权 Set 中移除 taskId（若存在）
        if (null != userId) {
            String setKey = WAITING_AUTH_PREFIX + userId;
            removeFromSet(setKey, taskId);
            log.debug("[LocalTaskContext] 从 Set 移除, key={}, taskId={}", setKey, taskId);
        }
    }

    // ==================== Set 操作（基于 SQLite 实现） ====================

    /**
     * 向 Set 中添加元素
     * <p>存储为 JSON 数组字符串</p>
     */
    private void addToSet(String key, String value) {
        Set<String> members = getSetMembers(key);
        members.add(value);
        saveSet(key, members);
    }

    /**
     * 从 Set 中移除元素
     */
    private void removeFromSet(String key, String value) {
        Set<String> members = getSetMembers(key);
        members.remove(value);
        if (members.isEmpty()) {
            localCacheService.delete(key);
        } else {
            saveSet(key, members);
        }
    }

    /**
     * 获取 Set 所有成员
     */
    private Set<String> getSetMembers(String key) {
        String json = localCacheService.get(key);
        if (StringUtils.isBlank(json)) {
            return new LinkedHashSet<>();
        }
        try {
            JSONArray array = JSONArray.parseArray(json);
            Set<String> result = new LinkedHashSet<>();
            for (int i = 0; i < array.size(); i++) {
                result.add(array.getString(i));
            }
            return result;
        } catch (Exception e) {
            log.error("[LocalTaskContext] getSetMembers 反序列化失败, key={}", key, e);
            return new LinkedHashSet<>();
        }
    }

    /**
     * 保存 Set 到 SQLite（JSON 数组格式）
     */
    private void saveSet(String key, Set<String> members) {
        JSONArray array = new JSONArray();
        array.addAll(members);
        localCacheService.put(key, array.toJSONString(), DEFAULT_TTL);
    }

}
