package com.matrix.client.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "matrix.service")
public class ServiceProperties {
    private String apiKey;
    private int retryMax = 5;
    private long retryBaseDelay = 1000;
    private String base;
    private String register;
    private String heartbeat;
    private String ack;

    public String getRegister() {
        return base + register;
    }

    public String getHeartbeat() {
        return base + heartbeat;
    }

    public String getAck() {
        return base + ack;
    }

}


