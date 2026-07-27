package com.matrix.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "matrix.gateway.limit")
public class LimitProperties {

    private int totalQps = 1000;
    private int userQps = 10;
    private int ipQps = 100;

    @Bean
    /** objectMapper操作 */
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
        // 或使用 Jackson2ObjectMapperBuilder.json().build();
    }

}


