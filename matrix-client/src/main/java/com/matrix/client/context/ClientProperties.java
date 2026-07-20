package com.matrix.client.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Data
@Component
@ConfigurationProperties(prefix = "matrix.client")
public class ClientProperties {
    private String name;
    private String desc;
    private Basic basic = new Basic();
    private MqttConfig mqtt = new MqttConfig();

    @Data
    public static class Basic {
        private String basePath = System.getProperty("user.dir");
        private String settingsPath = basePath + "/settings";
        private String modelsPath = settingsPath + "/models.yml";
        private String riskLevelPath = settingsPath + "/risk-level.yml";

        private String agentPath = settingsPath + "/agent";
        private String skillPath = settingsPath + "/skill";
        private String appPath = settingsPath + "/app";
    }

    @Data
    public static class MqttConfig {
        private String brokerUrl;
        private String username;
        private String password;
//        private String clientId;
        private int keepAlive = 60;
        private boolean cleanStart = true;
        private int connectTimeout = 10;
        private int maxReconnectDelay = 128;
//        private List<String> subscribeTopics;
    }

    @Bean
    public ExecutorService executor() {
        return new ThreadPoolExecutor(
                10,
                50,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),   // 队列最多 1000 条等待
                new ThreadPoolExecutor.CallerRunsPolicy()  // 背压：让 MQTT 线程自己跑，减慢接收速度
        );
    }

}


