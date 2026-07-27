package com.matrix.client.mqtt;

import com.matrix.client.context.MatrixClientProperties;
import com.matrix.client.service.Fingerprint;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttConnection {

    private final MqttSubscriber mqttSubscriber;
    private final MqttConsumer mqttConsumer;

    @Resource
    private MatrixClientProperties properties;
    @Resource
    private Fingerprint fingerprint;
    @Resource
    private ExecutorService executor;

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
    public void init() throws MqttException, IOException, InterruptedException {
        // 获取连接地址
        String brokerUrl = this.getMqttConnectionUrl();
        if (StringUtils.isBlank(brokerUrl)) {
            throw new RuntimeException("get mqtt brokerUrl fail");
        }
        log.info("mqtt brokerUrl: {}", brokerUrl);

        // 设置 clientId: ha-ce-[pc-]
        String clientId = fingerprint.get();
        log.info("Initializing MQTT client: {}", clientId);
        mqttClient = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
        mqttClient.setCallback(new MqttCallback() {
            @Override
            /** disconnected操作 */
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                log.warn("MQTT disconnected: returnCode={}, reason={}",
                        disconnectResponse.getReturnCode(), disconnectResponse.getReasonString());
            }

            @Override
            /** mqttErrorOccurred操作 */
            public void mqttErrorOccurred(MqttException e) {
                log.error("MQTT error occurred", e);
            }

            @Override
            /** messageArrived操作 */
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                log.info("Message received topic {}", topic);
                executor.submit(() -> {
                    try {
                        mqttConsumer.handle(topic, payload);
                    } catch (Exception e) {
                        log.error("Message received topic={}, exception: {}",
                                topic, e.getMessage(), e);
                    }
                });
            }

            @Override
            /** connectComplete操作 */
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connect complete, reconnect={}", reconnect);
                // 指令消息订阅
                mqttSubscriber.subscribe();
            }

            @Override
            /** authPacketArrived操作 */
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                log.debug("Auth packet arrived");
            }

            @Override
            /** deliveryComplete操作 */
            public void deliveryComplete(IMqttToken token) {
                log.debug("Delivery complete");
            }
        });
    }

    /**
     * @description MQTT 连接
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void connect() {
        if (mqttClient == null) {
            log.error("MQTT client not initialized");
            return;
        }
        try {
            mqttClient.connect(this.getOptions()).waitForCompletion();
        } catch (MqttException e) {
            log.error("MQTT connection failed {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private MqttConnectionOptions getOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        String username = this.getMqttUsername();
        if (StringUtils.isNotBlank(username)) {
            options.setUserName(username);
        }
        String password = this.getMqttPassword();
        if (StringUtils.isNotBlank(password)) {
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        }
        options.setCleanStart(properties.getMqtt().isCleanStart());
        options.setKeepAliveInterval(properties.getMqtt().getKeepAlive());
        options.setAutomaticReconnect(true);
        options.setMaxReconnectDelay((int)(properties.getMqtt().getMaxReconnectDelay() * 1000L));
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
                log.error("MQTT disconnect failed {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    /** 获取Client属性值 */
    public MqttAsyncClient getClient() {
        return mqttClient;
    }

    /**
     * @description 获取 mqtt 连接地址
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getMqttConnectionUrl() {
        return properties.getMqtt().getBrokerUrl();
    }

    /**
     * @description 获取 mqtt 连接用户
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getMqttUsername() {
        return properties.getMqtt().getUsername();
    }

    /**
     * @description 获取 mqtt 连接密码
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getMqttPassword() {
        return properties.getMqtt().getPassword();
    }

}


