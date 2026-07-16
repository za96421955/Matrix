package com.matrix.service.mqtt;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MqttRunner implements ApplicationRunner {

    @Resource
    private MqttConnection mqttConnection;

    @Override
    public void run(ApplicationArguments args) {
        mqttConnection.connect();
    }

}


