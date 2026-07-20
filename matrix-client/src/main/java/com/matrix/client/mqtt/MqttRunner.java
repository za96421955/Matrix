package com.matrix.client.mqtt;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttRunner implements CommandLineRunner {

    @Resource
    private MqttConnection mqttConnection;

    @Override
    public void run(String... args) {
        mqttConnection.connect();
    }
}
