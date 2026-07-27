package com.matrix.local.primary;

import com.matrix.common.constant.TaskStatus;
import com.matrix.service.context.CompletableContext;
import com.matrix.service.dal.entity.TaskInfo;
import com.matrix.service.service.task.TaskComplete;
import com.matrix.service.service.task.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 任务发布, 等待执行结果
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@Primary
public class LocalTaskComplete implements TaskComplete {

    @Resource
    private CompletableContext completableContext;

    private final TaskService taskService;
    /** LocalTaskComplete操作 */
    public LocalTaskComplete(@Lazy TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * @description 更新任务结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void completeTask(Long userId, String taskId, String result) {
        if (null == result) {
            result = "";
        }
        // 更新任务结果
        TaskInfo taskInfo = taskService.getTaskInfo(userId, taskId);
        if (null == taskInfo || TaskStatus.COMPLETED.equals(taskInfo.getStatus())) {
            return;
        }
        taskService.updateStatusAndResult(taskInfo.getUserId(), taskInfo.getTaskId(), TaskStatus.COMPLETED, result);
        log.info("[任务完成] taskId={}, result={}", taskId, result);
        // 通过 CompletableContext 完成等待
        completableContext.complete(taskId, result);
        log.info("[任务完成] taskId={}, 执行完成", taskId);
    }

}
