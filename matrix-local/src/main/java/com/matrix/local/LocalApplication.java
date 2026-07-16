package com.matrix.local;

import com.matrix.client.ClientApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Local 应用入口
 * <p> 合并 cloud 和 client 能力，零外部依赖，本地化运行 </p>
 *
 * @author 陈晨
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.matrix.local",
                "com.matrix.service",
                "com.matrix.client",
                "com.matrix.common"
        },
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = {
                                "com\\.matrix\\.service\\.mqtt\\..*",
                                "com\\.matrix\\.client\\.mqtt\\..*"
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
