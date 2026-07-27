package com.matrix.local;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.matrix.local",
        "com.matrix.service",
        "com.matrix.client"
})
@MapperScan(basePackages = {
        "com.matrix.service.dal.mapper",
        "com.matrix.local.dal.mapper"
})
@EnableScheduling
public class LocalApplication {

    /** 程序入口点 */
    public static void main(String[] args) {
        SpringApplication.run(LocalApplication.class, args);
    }

}
