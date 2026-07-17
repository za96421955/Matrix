package com.matrix.local;

import com.matrix.client.ClientApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@MapperScan(basePackages = {
        "com.matrix.service.dal.mapper",
        "com.matrix.local.dal.mapper"
})
@ComponentScan(
        basePackages = {
                "com.matrix.service",
                "com.matrix.local",
                "com.matrix.client",
                "com.matrix.common"
        },
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = {
                                "com\\.matrix\\.service\\.mqtt\\..*"
                        }
                ),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        value = ClientApplication.class
                )
        }
)
public class LocalApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalApplication.class, args);
    }

}
