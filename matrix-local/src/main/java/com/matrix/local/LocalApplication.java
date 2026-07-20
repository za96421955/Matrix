package com.matrix.local;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.matrix.local",
        "com.matrix.service",
        "com.matrix.client"
})
@MapperScan(basePackages = {
        "com.matrix.service.dal.mapper",
        "com.matrix.local.dal.mapper"
})
public class LocalApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalApplication.class, args);
    }

}
