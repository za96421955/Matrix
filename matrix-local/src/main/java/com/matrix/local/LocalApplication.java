package com.matrix.local;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = {
                "com.matrix.local",
                "com.matrix.service",
                "com.matrix.client"
        },
        exclude = {
                ManagementWebSecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class
        }
)
@MapperScan(basePackages = {
        "com.matrix.service.dal.mapper",
        "com.matrix.local.dal.mapper"
})
public class LocalApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalApplication.class, args);
    }

}
