package com.matrix.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "matrix.service")
public class ServiceProperties {
    private MqttConfig mqtt = new MqttConfig();

    @Data
    public static class MqttConfig {
        private String brokerUrl;
        private String clientId;
        private String username;
        private String password;
        private int keepAlive = 60;
        private boolean cleanStart = true;
        private int connectTimeout = 10;
        private int maxReconnectDelay = 128;
        private List<String> subscribeTopics;
    }

}


