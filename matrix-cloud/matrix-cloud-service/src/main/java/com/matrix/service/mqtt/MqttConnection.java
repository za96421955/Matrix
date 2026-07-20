package com.matrix.service.mqtt;

import com.matrix.service.config.ServiceProperties;
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
    private ServiceProperties serviceProperties;

    private final MqttSubscriber mqttSubscriber;
    private final MqttConsumer mqttConsumer;

    private MqttAsyncClient mqttClient;

    @Autowired
    public MqttConnection(@Lazy MqttSubscriber mqttSubscriber,
                          @Lazy MqttConsumer mqttConsumer) {
        this.mqttSubscriber = mqttSubscriber;
        this.mqttConsumer = mqttConsumer;
    }

    @PostConstruct
    public void init() throws MqttException {
        String brokerUrl = serviceProperties.getMqtt().getBrokerUrl();
        String clientId = "service-" + UUID.randomUUID();
        log.info("Initializing MQTT client: {}", clientId);
        mqttClient = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connect complete: reconnect={}, serverURI={}", reconnect, serverURI);
                mqttSubscriber.subscribe();
            }

            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                log.warn("MQTT disconnected: returnCode={}, reason={}",
                        disconnectResponse.getReturnCode(), disconnectResponse.getReasonString());
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                log.error("MQTT error occurred", exception);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                log.info("Message received: topic={}, payload={}", topic, payload);
                mqttConsumer.handle(topic, payload);
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
                log.debug("Message delivery complete: {}", token.getMessageId());
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                log.debug("Auth packet arrived: reasonCode={}", reasonCode);
            }
        });
    }

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
        String username = serviceProperties.getMqtt().getUsername();
        MqttConnectionOptions options = new MqttConnectionOptions();
        if (StringUtils.isNotBlank(username)) {
            options.setUserName(username);
        }
        if (StringUtils.isNotBlank(serviceProperties.getMqtt().getPassword())) {
            options.setPassword(serviceProperties.getMqtt().getPassword().getBytes(StandardCharsets.UTF_8));
        }
        options.setCleanStart(serviceProperties.getMqtt().isCleanStart());
        options.setKeepAliveInterval(serviceProperties.getMqtt().getKeepAlive());
        options.setAutomaticReconnect(true);
        options.setMaxReconnectDelay((int)(serviceProperties.getMqtt().getMaxReconnectDelay() * 1000L));
        log.info("Connecting to MQTT broker: {} with username: {}",
                serviceProperties.getMqtt().getBrokerUrl(),
                username != null && !username.trim().isEmpty() ? username : "(anonymous)");
        return options;
    }

    @PreDestroy
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

    public MqttAsyncClient getClient() {
        return mqttClient;
    }

}


