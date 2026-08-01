package com.matrix.service.service.task.impl;

import com.matrix.common.constant.MqttTopic;
import com.matrix.common.constant.TaskStatus;
import com.matrix.service.dal.entity.TaskInfo;
import com.matrix.service.mqtt.MqttPublisher;
import com.matrix.service.service.task.TaskComplete;
import com.matrix.service.service.task.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 任务发布, 等待执行结果
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttTaskComplete implements TaskComplete {

    @Resource
    private MqttPublisher mqttPublisher;

    private final TaskService taskService;
    /** MqttTaskComplete操作 */
    public MqttTaskComplete(@Lazy TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * @description 更新任务结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void completeTask(Long userId, String taskId, String result) throws MqttException {
        if (null == result) {
            result = "";
        }
        // 发布结果通知
        String topic = MqttTopic.TASK_RESULT.replaceAll("\\+", taskId);
        mqttPublisher.publish(topic, result);
        log.info("[任务完成] topic={}, result={}, 通知完成", topic, result);
        // 更新任务结果
        TaskInfo taskInfo = taskService.getTaskInfo(userId, taskId);
        if (null == taskInfo || TaskStatus.COMPLETED.equals(taskInfo.getStatus())) {
            return;
        }
        taskService.updateStatusAndResult(taskInfo.getUserId(), taskInfo.getTaskId(), TaskStatus.COMPLETED, result);
        log.info("[任务完成] taskId={}, result={}", taskId, result);
    }

}


