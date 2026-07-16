package com.matrix.local.service.task;

import com.matrix.common.constant.Constant;
import com.matrix.common.constant.TaskStatus;
import com.matrix.common.dto.command.ClientCommand;
import com.matrix.common.dto.command.TaskCommand;
import com.matrix.common.util.GuidUtil;
import com.matrix.client.service.impl.PCCommandExecutor;
import com.matrix.service.context.CompletableContext;
import com.matrix.service.dal.entity.TaskInfo;
import com.matrix.service.service.task.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * 本地任务发布，替代原 TaskPublish。<br>
 * 移除 MqttPublisher/MqttSubscriber 注入。<br>
 * publishTask：直接调用 TaskService.insert，通过 CompletableContext 本地等待。<br>
 * publishCommand：直接调用 PCCommandExecutor.execute 本地执行。<br>
 * waitForResult：使用 CompletableContext.dispatch 本地等待。<br>
 * completeTask：直接回调 CompletableContext.complete，更新 LocalTaskContext。
 *
 * @author 陈晨
 */
@Component
@Slf4j
public class LocalTaskPublish {

    @Resource
    private PCCommandExecutor pcCommandExecutor;
    @Resource
    private CompletableContext completableContext;

    private final TaskService taskService;

    public LocalTaskPublish(@Lazy TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * @description 提交任务
     * <p>本地模式：直接插入 TaskInfo 到 SQLite，不通过 MQTT 发布。</p>
     *
     * @author 陈晨
     */
    public TaskCommand publishTask(TaskCommand taskCommand) {
        taskCommand.setTaskId(GuidUtil.getUUID());
        // 新增任务状态
        taskService.insert(TaskInfo.builder()
                        .userId(taskCommand.getUserId())
                        .agentName(taskCommand.getAgentName())
                        .taskId(taskCommand.getTaskId())
                        .type(taskCommand.getType())
                        .status(TaskStatus.PENDING)
                        .content(taskCommand.getBody())
                        .createTime(new Date())
                        .creator(Constant.SYSTEM_USER)
                .build());
        log.info("[本地发布任务] taskCommand={}, 插入完成", taskCommand);
        return taskCommand;
    }

    /**
     * @description 提交终端执行指令 (无需用户授权)
     * <p>本地模式：直接调用 PCCommandExecutor.execute 本地执行，无需 MQTT 下发。</p>
     *
     * @author 陈晨
     */
    public String publishCommand(ClientCommand command) {
        if (null == command || StringUtils.isBlank(command.getClientId())) {
            return null;
        }
        if (StringUtils.isBlank(command.getTaskId())) {
            command.setTaskId(GuidUtil.getUUID());
        }
        // 直接调用本地执行器
        String result = pcCommandExecutor.execute(command.getTaskId(), command.getCommand());
        // 通过 CompletableContext 完成等待
        completableContext.complete(command.getTaskId(), result);
        log.info("[本地执行指令] command={}, 执行完成", command);
        return command.getTaskId();
    }

    /**
     * @description 等待结果（支持超时）
     * <p>本地模式：使用 CompletableContext.dispatch 本地 CompletableFuture 等待。</p>
     *
     * @author 陈晨
     */
    public Mono<String> waitForResult(Long userId, String taskId, long timeoutSeconds) {
        // 尝试获取结果
        TaskInfo taskInfo = taskService.getTaskInfo(userId, taskId);
        if (null != taskInfo && StringUtils.isNotBlank(taskInfo.getResult())) {
            return Mono.just(taskInfo.getResult());
        }
        // 本地等待结果，使用 taskId 作为 topic key
        return completableContext.dispatch(taskId, timeoutSeconds)
                .onErrorResume(e -> {
                    if (null != taskInfo) {
                        taskService.updateStatusAndResult(
                                userId, taskId, TaskStatus.TIMEOUT, e.getMessage()
                        );
                    }
                    return Mono.error(e);
                });
    }

    /**
     * @description 更新任务结果
     * <p>本地模式：通过 CompletableContext.complete 完成本地等待，并更新 TaskContext。</p>
     *
     * @author 陈晨
     */
    public void completeTask(Long userId, String taskId, String result) {
        if (null == result) {
            result = "";
        }
        // 本地完成等待
        completableContext.complete(taskId, result);
        log.info("[本地任务完成] taskId={}, result={}, 通知完成", taskId, result);
        // 更新任务结果
        TaskInfo taskInfo = taskService.getTaskInfo(userId, taskId);
        if (null == taskInfo || TaskStatus.COMPLETED.equals(taskInfo.getStatus())) {
            return;
        }
        taskService.updateStatusAndResult(taskInfo.getUserId(), taskInfo.getTaskId(), TaskStatus.COMPLETED, result);
        log.info("[本地任务完成] taskId={}, result={}", taskId, result);
    }

}
