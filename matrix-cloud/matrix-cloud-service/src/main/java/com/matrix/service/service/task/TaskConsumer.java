package com.matrix.service.service.task;

import com.matrix.common.constant.TaskStatus;
import com.matrix.common.constant.TaskType;
import com.matrix.common.dto.command.ClientCommand;
import com.matrix.common.dto.command.TaskCommand;
import com.matrix.service.cache.ServiceCache;
import com.matrix.service.dal.entity.TaskInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 任务消费
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class TaskConsumer {

    @Resource
    private ServiceCache serviceCache;
    @Resource
    private TaskService taskService;
    @Resource
    private TaskPublish taskPublish;

    /**
     * 处理单个任务
     */
    public void processTask(TaskCommand taskCommand) {
        if (null == taskCommand) {
            return;
        }
        // 使用Redisson的分布式锁
        String lockKey = "lock:task:" + taskCommand.getTaskId();
        try {
            boolean lock = serviceCache.lock(lockKey, 10);
            if (!lock) {
                return;
            }

            // 获取任务详情（通过 TaskService，走 Redis）
            TaskInfo taskInfo = taskService.getTaskInfo(taskCommand.getUserId(), taskCommand.getTaskId());
            if (null == taskInfo) {
                log.warn("[TaskConsumer] 任务不存在, taskId={}", taskCommand.getTaskId());
                return;
            }
            // 检查任务是否已被处理
            if (!TaskStatus.PENDING.equals(taskInfo.getStatus())) {
                return;
            }
            // 任务执行中
            taskService.updateStatus(taskCommand.getUserId(), taskInfo.getTaskId(), TaskStatus.RUNNING);

            // 下发执行器执行
            if (TaskType.EXECUTOR_COMMAND.equals(taskInfo.getType())) {
                ClientCommand clientCommand = ClientCommand.convert(taskInfo.getContent());
                log.info("[终端执行] clientCommand={}", clientCommand);
                if (null == clientCommand) {
                    return;
                }
                clientCommand.setTaskId(taskInfo.getTaskId());
                taskPublish.publishCommand(clientCommand);
            }
        } catch (Exception e) {
            log.error("[任务异常] userId={}, taskId={}, 异常: {}",
                    taskCommand.getUserId(), taskCommand.getTaskId(), e.getMessage(), e);
            taskService.updateStatusAndResult(taskCommand.getUserId(), taskCommand.getTaskId(), TaskStatus.EXCEPTION, e.getMessage());
        } finally {
            serviceCache.delete(lockKey);
        }
    }

}
