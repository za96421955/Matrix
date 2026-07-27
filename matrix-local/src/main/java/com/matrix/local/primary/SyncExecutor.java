package com.matrix.local.primary;

import com.matrix.client.service.impl.PCCommandExecutor;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.TaskStatus;
import com.matrix.common.constant.TaskType;
import com.matrix.common.dto.command.TaskCommand;
import com.matrix.common.util.GuidUtil;
import com.matrix.service.context.CompletableContext;
import com.matrix.service.dal.entity.TaskInfo;
import com.matrix.service.service.task.Executor;
import com.matrix.service.service.task.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Date;

/**
 * 异步执行器
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@Primary
public class SyncExecutor implements Executor {

    private static final long TASK_TIMEOUT = 60;
    private static final long COMMAND_TIMEOUT = 10;

    @Resource
    private PCCommandExecutor pcCommandExecutor;
    @Resource
    private CompletableContext completableContext;
    @Resource
    private TaskService taskService;

    @Override
    /** executeAuth操作 */
    public Mono<String> executeAuth(Long userId, String command) {
        // 创建授权任务
        TaskCommand taskCommand = TaskCommand.builder()
                .userId(userId)
                .taskId(GuidUtil.getUUID())
                .agentName("")
                .type(TaskType.USER_AUTH)
                .body(command)
                .build();
        // 新增任务状态
        taskService.insert(TaskInfo.builder()
                .userId(taskCommand.getUserId())
                .agentName(taskCommand.getAgentName())
                .taskId(taskCommand.getTaskId())
                .type(taskCommand.getType())
                .status(TaskStatus.RUNNING)
                .content(taskCommand.getBody())
                .createTime(new Date())
                .creator(Constant.SYSTEM_USER)
                .build());
        log.info("[本地等待授权] userId={}, command={}", userId, command);
        // 等待结果
        return completableContext.dispatch(taskCommand.getTaskId(), TASK_TIMEOUT * 10)
                .onErrorResume(e -> {
                    taskService.updateStatusAndResult(
                            userId, taskCommand.getTaskId(), TaskStatus.TIMEOUT, e.getMessage()
                    );
                    return Mono.error(e);
                });
    }

    @Override
    /** executeTask操作 */
    public Mono<String> executeTask(Long userId, String taskId, String command)
            throws IOException, InterruptedException {
        // 直接调用本地执行器
        String result = pcCommandExecutor.execute(taskId, command);
        // 通过 CompletableContext 完成等待
        completableContext.complete(taskId, result);
        log.info("[本地执行指令] taskId={}, command={}, 执行完成", taskId, command);
        return Mono.just(result);
    }

    @Override
    /** executeCommand操作 */
    public Mono<String> executeCommand(String taskId, String command)
            throws IOException, InterruptedException {
        // 直接调用本地执行器
        String result = pcCommandExecutor.execute(taskId, command);
        log.info("[本地执行指令] taskId={}, command={}, 执行完成", taskId, command);
        return Mono.just(result);
    }

}


