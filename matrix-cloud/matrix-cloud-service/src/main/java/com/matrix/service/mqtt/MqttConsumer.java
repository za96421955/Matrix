package com.matrix.service.mqtt;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.service.context.CompletableContext;
import com.matrix.common.dto.command.TaskCommand;
import com.matrix.service.service.task.TaskConsumer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttConsumer {

    @Resource
    private CompletableContext completableContext;

    private final TaskConsumer taskConsumer;

    @Autowired
    public MqttConsumer(@Lazy TaskConsumer taskConsumer) {
        this.taskConsumer = taskConsumer;
    }

    public void handle(String topic, String payload) {
        log.info("Handling MQTT message: topic={}, payload={}", topic, payload);
        // 完成等待
        completableContext.complete(topic, payload);

        // topic 处理
        if (MqttTopics.TASK_PUBLISH.equals(topic)) {
            try {
                taskConsumer.processTask(JSONObject.parseObject(payload, TaskCommand.class));
            } catch (Exception e) {
                log.error("topic={}, payload={}, 处理异常: {}", topic, payload, e.getMessage(), e);
            }
        }
    }

}


