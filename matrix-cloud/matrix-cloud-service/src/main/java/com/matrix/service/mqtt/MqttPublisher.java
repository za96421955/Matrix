package com.matrix.service.mqtt;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttPublisher {

    @Resource
    private MqttConnection mqttConnection;

    /**
     * 发布消息到指定主题
     *
     * @param topic   主题
     * @param payload 消息内容
     * @param qos     服务质量等级（0, 1, 2）
     */
    public void publish(String topic, String payload, int qos) throws MqttException {
        MqttAsyncClient client = mqttConnection.getClient();
        if (client == null || !client.isConnected()) {
            log.error("MQTT client not connected, cannot publish to: {}", topic);
            return;
        }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(false);
        client.publish(topic, message).waitForCompletion();
        log.info("Published message to topic: {}", topic);
    }

    /**
     * 发布消息到指定主题
     *
     * @param topic   主题
     * @param payload 消息内容
     */
    public void publish(String topic, String payload) throws MqttException {
        publish(topic, payload, 1);
    }

    /**
     * 发送命令到 CE
     *
     * @param payload     命令内容
     */
    public void publishToCe(String clientExecutorId, String payload) throws MqttException {
        String topic = MqttTopics.MATRIX_CLIENT_COMMAND.replaceAll("\\+", clientExecutorId);
        this.publish(topic, payload);
    }

}
