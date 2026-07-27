package com.matrix.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    
    /** 程序入口点 */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}


