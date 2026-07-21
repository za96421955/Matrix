package com.matrix.client.mqtt;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.client.service.AckService;
import com.matrix.client.service.CommandExecutor;
import com.matrix.client.service.SystemService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttConsumer {

    @Resource
    private SystemService systemService;
    @Resource
    private CommandExecutor commandExecutor;
    @Resource
    private AckService ackService;

    public void handle(String topic, String payload) {
        String taskId = null;
        try {
            JSONObject request = JSONObject.parseObject(payload);
            taskId = (String) request.get("taskId");
            String command = (String) request.get("command");
            log.debug("[消息处理] topic={}, taskId={}, command={}", topic, taskId, command);
            // 执行指令
            String result = commandExecutor.execute(taskId, command);
            log.info("[消息处理] topic={}, taskId={}, command={}, result={}",
                    topic, taskId, command, result);
            ackService.send(taskId, result);
        } catch (Exception e) {
            log.error("[消息处理] topic={}, payload={}, 指令处理异常: {}",
                    topic, payload, e.getMessage(), e);
            // 发送处理异常信息
            if (StringUtils.isNotBlank(taskId)) {
                try {
                    ackService.send(taskId, e.getMessage());
                } catch (Exception sendExp) {
                    log.error("[消息处理] topic={}, payload={}, 处理异常信息发送异常: {}",
                            topic, payload, sendExp.getMessage(), sendExp);
                }
            }
        }
    }

}


