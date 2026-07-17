package com.matrix.client.mqtt;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.client.context.ClientProperties;
import com.matrix.client.context.ServiceProperties;
import com.matrix.client.service.Fingerprint;
import com.matrix.client.service.RegisterService;
import com.matrix.client.util.HttpClient;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class MqttConnection {

    private final MqttSubscriber mqttSubscriber;
    private final MqttConsumer mqttConsumer;
    private final RegisterService registerService;

    @Resource
    private ClientProperties clientProperties;
    @Resource
    private ServiceProperties serviceProperties;
    @Resource
    private Fingerprint fingerprint;
    @Resource
    private ExecutorService executor;

    private MqttAsyncClient mqttClient;

    @Autowired
    public MqttConnection(@Lazy MqttSubscriber mqttSubscriber,
                          @Lazy MqttConsumer mqttConsumer,
                          @Lazy RegisterService registerService) {
        this.mqttSubscriber = mqttSubscriber;
        this.mqttConsumer = mqttConsumer;
        this.registerService = registerService;
    }

    @PostConstruct
    public void init() throws MqttException, IOException, InterruptedException {
        // 获取连接地址
        String brokerUrl = this.getMqttConnectionUrl();
        if (StringUtils.isBlank(brokerUrl)) {
            throw new RuntimeException("get mqtt brokerUrl fail");
        }
        log.info("mqtt brokerUrl: {}", brokerUrl);

        // 设置 clientId: ha-ce-[pc-]
        // TODO 终端属于个人所有，所以单环境仅允许注册一个同一终端，多次注册会剔除前者或造成广播？未测试
        String clientId = "ha-ce-pc-" + fingerprint.get();
        log.info("Initializing MQTT client: {}", clientId);
        mqttClient = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                log.warn("MQTT disconnected: returnCode={}, reason={}",
                        disconnectResponse.getReturnCode(), disconnectResponse.getReasonString());
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                log.error("MQTT error occurred", e);
            }

            @Override
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
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connect complete, reconnect={}", reconnect);
                // 指令消息订阅
                mqttSubscriber.subscribe();
                // 终端注册
                registerService.register();
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                log.debug("Auth packet arrived");
            }

            @Override
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
        options.setCleanStart(clientProperties.getMqtt().isCleanStart());
        options.setKeepAliveInterval(clientProperties.getMqtt().getKeepAlive());
        options.setAutomaticReconnect(true);
        options.setMaxReconnectDelay((int)(clientProperties.getMqtt().getMaxReconnectDelay() * 1000L));
        return options;
    }

    @PreDestroy
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
        if (StringUtils.isNotBlank(clientProperties.getMqtt().getBrokerUrl())) {
            return clientProperties.getMqtt().getBrokerUrl();
        }
        String response = HttpClient.post(clientProperties.getMqtt().getLoginUrl())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .authorization(serviceProperties.getApiKey())
                .asString();
        return (String) JSONObject.parseObject(response).get("mqttHost");
    }

    /**
     * @description 获取 mqtt 连接用户
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getMqttUsername() {
        if (StringUtils.isNotBlank(clientProperties.getMqtt().getBrokerUrl())) {
            return clientProperties.getMqtt().getUsername();
        }
        return serviceProperties.getApiKey();
    }

    /**
     * @description 获取 mqtt 连接密码
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String getMqttPassword() {
        if (StringUtils.isNotBlank(clientProperties.getMqtt().getBrokerUrl())) {
            return clientProperties.getMqtt().getPassword();
        }
        return serviceProperties.getApiKey();
    }

}


