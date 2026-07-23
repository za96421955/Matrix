package com.matrix.service.service.task.impl;

import com.matrix.common.constant.TaskType;
import com.matrix.common.dto.command.ClientCommand;
import com.matrix.common.dto.command.TaskCommand;
import com.matrix.service.service.task.Executor;
import com.matrix.service.service.task.TaskPublish;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 异步执行器
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class AsyncExecutor implements Executor {

    private static final long TASK_TIMEOUT = 60;
    private static final long COMMAND_TIMEOUT = 10;

    @Resource
    private TaskPublish taskPublish;

    @Override
    public Mono<String> executeAuth(Long userId, String command)
            throws Exception {
        // 创建授权任务
        TaskCommand taskCommand = taskPublish.publishTask(TaskCommand.builder()
                .userId(userId)
                .agentName("")
                .type(TaskType.USER_AUTH)
                .body(command)
                .build());
        log.info("[等待授权] userId={}, command={}", userId, command);
        // 等待授权
        return taskPublish.waitForResult(userId, taskCommand.getTaskId(), TASK_TIMEOUT * 5);
    }

    @Override
    public Mono<String> executeTask(Long userId, String clientId, String command)
            throws MqttException {
        // 创建任务
        TaskCommand taskCommand = taskPublish.publishTask(TaskCommand.builder()
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
        return taskPublish.waitForResult(userId, taskCommand.getTaskId(), TASK_TIMEOUT * 3);
    }

    @Override
    public Mono<String> executeCommand(String clientId, String command)
            throws MqttException {
        String taskId = taskPublish.publishCommand(ClientCommand.builder()
                .clientId(clientId)
                .command(command)
                .build());
        return taskPublish.waitForResult(null, taskId, COMMAND_TIMEOUT);
    }

}


