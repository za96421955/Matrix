package com.matrix.local.service.task;

import com.matrix.common.constant.TaskType;
import com.matrix.common.dto.command.ClientCommand;
import com.matrix.common.dto.command.TaskCommand;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 本地执行器，替代原 Executor。<br>
 * 注入 LocalTaskPublish 替代 TaskPublish。<br>
 * executeAuth / executeTask / executeCommand 均通过本地调用完成，无需 MQTT。<br>
 * 复用原 Executor 的方法签名和超时逻辑。
 *
 * @author 陈晨
 */
@Primary
@Component
@Slf4j
public class LocalExecutor {

    private static final long TASK_TIMEOUT = 60;
    private static final long COMMAND_TIMEOUT = 10;

    @Resource
    private LocalTaskPublish localTaskPublish;

    /**
     * @description 执行授权任务
     * <p>本地模式：通过 LocalTaskPublish 直接提交并等待，无需 MQTT。</p>
     *
     * @author 陈晨
     */
    public Mono<String> executeAuth(Long userId, String command) {
        // 创建授权任务
        TaskCommand taskCommand = localTaskPublish.publishTask(TaskCommand.builder()
                .userId(userId)
                .agentName("")
                .type(TaskType.USER_AUTH)
                .body(command)
                .build());
        log.info("[本地等待授权] userId={}, command={}", userId, command);
        // 等待授权
        return localTaskPublish.waitForResult(userId, taskCommand.getTaskId(), TASK_TIMEOUT * 5);
    }

    /**
     * @description 执行任务
     * <p>本地模式：通过 LocalTaskPublish 直接提交并等待，无需 MQTT。</p>
     *
     * @author 陈晨
     */
    public Mono<String> executeTask(Long userId, String clientId, String command) {
        // 创建任务
        TaskCommand taskCommand = localTaskPublish.publishTask(TaskCommand.builder()
                .userId(userId)
                .agentName("")
                .type(TaskType.EXECUTOR_COMMAND)
                .body(ClientCommand.builder()
                        .clientId(clientId)
                        .command(command)
                        .build()
                        .toString())
                .build());
        // 等待执行结果
        return localTaskPublish.waitForResult(userId, taskCommand.getTaskId(), TASK_TIMEOUT * 3);
    }

    /**
     * @description 执行指令
     * <p>本地模式：通过 LocalTaskPublish 直接调用 PCCommandExecutor 本地执行。</p>
     *
     * @author 陈晨
     */
    public Mono<String> executeCommand(String clientId, String command) {
        String taskId = localTaskPublish.publishCommand(ClientCommand.builder()
                .clientId(clientId)
                .command(command)
                .build());
        return localTaskPublish.waitForResult(null, taskId, COMMAND_TIMEOUT);
    }

}
