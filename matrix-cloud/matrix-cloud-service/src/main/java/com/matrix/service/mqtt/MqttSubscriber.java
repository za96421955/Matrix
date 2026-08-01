package com.matrix.service.mqtt;

import com.matrix.common.constant.MqttTopic;
import com.matrix.common.constant.SystemParam;
import com.matrix.service.context.CompletableContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttSubscriber {

    @Resource
    private MqttConnection mqttConnection;
    @Resource
    private CompletableContext completableContext;

    /** 订阅消息或话题 */
    public void subscribe(String topic) throws MqttException {
        if (StringUtils.isBlank(topic)) {
            return;
        }
        MqttAsyncClient client = mqttConnection.getClient();
        if (client == null) {
            return;
        }
        client.subscribe(topic, SystemParam.MQTT_QOS).waitForCompletion();
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
        try {
            for (String topic : MqttTopic.SUBSCRIBE_TOPICS) {
                try {
                    this.subscribe(topic);
                    log.info("Subscribed to: {}", topic);
                } catch (Exception e) {
                    log.error("Failed to subscribe to: {}", topic, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to subscribe to MQTT topics", e);
        }
    }

    /** subscribeWaitResult操作 */
    public Mono<String> subscribeWaitResult(String topic, long timeoutSeconds) throws MqttException {
        // 订阅结果
        this.subscribe(topic);
        log.info("[结果订阅] topic={}, 开始订阅", topic);
        return completableContext.dispatch(topic, timeoutSeconds)
                .doFinally(signalType -> {
                    // 取消订阅
                    try {
                        this.unsubscribe(topic);
                        log.info("[结果订阅] topic={}, 取消订阅", topic);
                    } catch (MqttException e) {
                        log.error("[结果订阅] topic={}, 取消订阅异常: {}", topic, e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
    }

}


