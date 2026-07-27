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
@ConfigurationProperties(prefix = "matrix")
public class MatrixClientProperties {

    private Client client = new Client();
    private Service service = new Service();
    private Mqtt mqtt = new Mqtt();

    @Data
    public static class Client {
        private String name;
        private String desc;
        private final Basic basic = new Basic();
    }

    @Data
    public static class Basic {
        private String basePath = System.getProperty("user.dir");
        private String settingsPath = basePath + "/settings";
        private String modelsPath = settingsPath + "/models.yml";
        private String riskLevelPath = settingsPath + "/risk-level.yml";

//        private String agentPath = settingsPath + "/agent";
        private String skillPath = settingsPath + "/skill";
        private String appPath = settingsPath + "/app";
    }

    @Data
    public static class Service {
        private String apiKey;
        private int retryMax = 5;
        private long retryBaseDelay = 1000;
        private String base;
        private String register;
        private String heartbeat;
        private String ack;

        /** 获取Register属性值 */
        public String getRegister() {
            return base + register;
        }

        /** 获取Heartbeat属性值 */
        public String getHeartbeat() {
            return base + heartbeat;
        }

        /** 获取Ack属性值 */
        public String getAck() {
            return base + ack;
        }
    }

    @Data
    public static class Mqtt {
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

}


