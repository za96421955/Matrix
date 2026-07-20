package com.matrix.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "matrix")
public class MatrixServiceProperties {

    private Service service = new Service();
    private Mqtt mqtt = new Mqtt();
    private Github github = new Github();

    @Data
    public static class Service {

    }

    @Data
    public static class Mqtt {
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

    @Data
    public static class Github {
        private String token;
    }

}


