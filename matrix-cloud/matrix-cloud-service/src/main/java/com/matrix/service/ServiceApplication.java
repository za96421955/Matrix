package com.matrix.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan(basePackages = { "com.matrix.service.dal.mapper" })
public class ServiceApplication {

    /** 程序入口点 */
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

}


