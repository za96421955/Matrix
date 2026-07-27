package com.matrix.service.mqtt;

import com.matrix.service.config.MatrixServiceProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttConnection {

    @Resource
    private MatrixServiceProperties properties;

    private final MqttSubscriber mqttSubscriber;
    private final MqttConsumer mqttConsumer;

    private MqttAsyncClient mqttClient;

    @Autowired
    /** MqttConnection操作 */
    public MqttConnection(@Lazy MqttSubscriber mqttSubscriber,
                          @Lazy MqttConsumer mqttConsumer) {
        this.mqttSubscriber = mqttSubscriber;
        this.mqttConsumer = mqttConsumer;
    }

    @PostConstruct
    /** 初始化资源或配置 */
    public void init() throws MqttException {
        String brokerUrl = properties.getMqtt().getBrokerUrl();
        String clientId = "service-" + UUID.randomUUID();
        log.info("Initializing MQTT client: {}", clientId);
        mqttClient = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
        mqttClient.setCallback(new MqttCallback() {
            @Override
            /** connectComplete操作 */
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connect complete: reconnect={}, serverURI={}", reconnect, serverURI);
                mqttSubscriber.subscribe();
            }

            @Override
            /** disconnected操作 */
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                log.warn("MQTT disconnected: returnCode={}, reason={}",
                        disconnectResponse.getReturnCode(), disconnectResponse.getReasonString());
            }

            @Override
            /** mqttErrorOccurred操作 */
            public void mqttErrorOccurred(MqttException exception) {
                log.error("MQTT error occurred", exception);
            }

            @Override
            /** messageArrived操作 */
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                log.info("Message received: topic={}, payload={}", topic, payload);
                mqttConsumer.handle(topic, payload);
            }

            @Override
            /** deliveryComplete操作 */
            public void deliveryComplete(IMqttToken token) {
                log.debug("Message delivery complete: {}", token.getMessageId());
            }

            @Override
            /** authPacketArrived操作 */
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                log.debug("Auth packet arrived: reasonCode={}", reasonCode);
            }
        });
    }

    /** 建立连接 */
    public void connect() {
        if (mqttClient == null) {
            log.error("MQTT client not initialized");
            return;
        }
        try {
            mqttClient.connect(this.getOptions()).waitForCompletion();
            log.info("MQTT connected successfully");
        } catch (MqttException e) {
            log.error("MQTT connection failed, reason: {}, message: {}", 
                     e.getReasonCode(), e.getMessage(), e);
        }
    }

    private MqttConnectionOptions getOptions() {
        String username = properties.getMqtt().getUsername();
        MqttConnectionOptions options = new MqttConnectionOptions();
        if (StringUtils.isNotBlank(username)) {
            options.setUserName(username);
        }
        if (StringUtils.isNotBlank(properties.getMqtt().getPassword())) {
            options.setPassword(properties.getMqtt().getPassword().getBytes(StandardCharsets.UTF_8));
        }
        options.setCleanStart(properties.getMqtt().isCleanStart());
        options.setKeepAliveInterval(properties.getMqtt().getKeepAlive());
        options.setAutomaticReconnect(true);
        options.setMaxReconnectDelay((int)(properties.getMqtt().getMaxReconnectDelay() * 1000L));
        log.info("Connecting to MQTT broker: {} with username: {}",
                properties.getMqtt().getBrokerUrl(),
                username != null && !username.trim().isEmpty() ? username : "(anonymous)");
        return options;
    }

    @PreDestroy
    /** 断开连接 */
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect().waitForCompletion();
                log.info("MQTT disconnected");
            } catch (MqttException e) {
                log.error("MQTT disconnect failed", e);
            }
        }
    }

    /** 获取Client属性值 */
    public MqttAsyncClient getClient() {
        return mqttClient;
    }

}


