package com.matrix.client.mqtt;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttSubscriber {

    @Resource
    private MqttConnection mqttConnection;

    /** 订阅消息或话题 */
    public void subscribe(String topic) throws MqttException {
        if (StringUtils.isBlank(topic)) {
            return;
        }
        MqttAsyncClient client = mqttConnection.getClient();
        if (client == null) {
            return;
        }
        client.subscribe(topic, 1).waitForCompletion();
    }

    /** unsubscribe操作 */
    public void unsubscribe(String topic) throws MqttException {
        if (StringUtils.isBlank(topic)) {
            return;
        }
        MqttAsyncClient client = mqttConnection.getClient();
        if (client == null) {
            return;
        }
        client.unsubscribe(topic).waitForCompletion();
    }

    /** 订阅消息或话题 */
    public void subscribe() {
        String topic = "matrix/client/command/{clientId}";
        try {
            topic = topic.replaceFirst("\\{clientId\\}", mqttConnection.getClient().getClientId());
            try {
                this.subscribe(topic);
                log.info("Subscribed to: {}", topic);
            } catch (Exception e) {
                log.error("Failed to subscribe to: {}, {}", topic, e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Failed to subscribe to MQTT topics: {}", e.getMessage(), e);
        }
    }

}


