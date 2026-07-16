package com.matrix.client.mqtt;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MqttRunner implements CommandLineRunner {

    @Resource
    private MqttConnection mqttConnection;

    @Override
    public void run(String... args) {
        mqttConnection.connect();
    }
}
