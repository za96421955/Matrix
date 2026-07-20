package com.matrix.service.mqtt;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.mqtt.enabled", havingValue = "true")
public class MqttRunner implements ApplicationRunner {

    @Resource
    private MqttConnection mqttConnection;

    @Override
    public void run(ApplicationArguments args) {
        mqttConnection.connect();
    }

}


