package com.matrix.service.service.task;

import com.matrix.common.constant.Constant;
import com.matrix.common.constant.TaskStatus;
import com.matrix.common.dto.command.ClientCommand;
import com.matrix.common.dto.command.TaskCommand;
import com.matrix.common.util.GuidUtil;
import com.matrix.service.dal.entity.TaskInfo;
import com.matrix.service.mqtt.MqttPublisher;
import com.matrix.service.mqtt.MqttSubscriber;
import com.matrix.service.mqtt.MqttTopics;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * 任务发布, 等待执行结果
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class TaskPublish {

    @Resource
    private MqttPublisher mqttPublisher;
    @Resource
    private MqttSubscriber mqttSubscriber;

    private final TaskService taskService;

    public TaskPublish(@Lazy TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * @description 提交任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public TaskCommand publishTask(TaskCommand taskCommand) throws MqttException {
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
        // 发布任务
        mqttPublisher.publish(MqttTopics.TASK_PUBLISH, taskCommand.toString());
        log.info("[发布任务] taskCommand={}, 发送完成", taskCommand);
        return taskCommand;
    }

    /**
     * @description 提交终端执行指令 (无需用户授权)
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String publishCommand(ClientCommand command) throws MqttException {
        if (null == command || StringUtils.isBlank(command.getClientId())) {
            return null;
        }
        if (StringUtils.isBlank(command.getTaskId())) {
            command.setTaskId(GuidUtil.getUUID());
        }
        mqttPublisher.publishToCe(command.getClientId(), command.toString());
        log.info("[发布指令] command={}, 发送完成", command);
        return command.getTaskId();
    }

    /**
     * @description 等待结果（支持超时）
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Mono<String> waitForResult(Long userId, String taskId, long timeoutSeconds) {
        // 尝试获取结果
        TaskInfo taskInfo = taskService.getTaskInfo(userId, taskId);
        if (null != taskInfo && StringUtils.isNotBlank(taskInfo.getResult())) {
            return Mono.just(taskInfo.getResult());
        }
        // 订阅结果
        String topic = MqttTopics.TASK_RESULT.replaceAll("\\+", taskId);
        try {
            return mqttSubscriber.subscribeWaitResult(topic, timeoutSeconds)
                    .onErrorResume(e -> {
                        if (null != taskInfo) {
                            taskService.updateStatusAndResult(
                                    userId, taskId, TaskStatus.TIMEOUT, e.getMessage()
                            );
                        }
                        return Mono.error(e);
                    });
        } catch (Exception e) {
            if (null != taskInfo) {
                taskService.updateStatusAndResult(userId, taskId, TaskStatus.TIMEOUT, e.getMessage());
            }
            return Mono.error(e);
        }
    }

}
