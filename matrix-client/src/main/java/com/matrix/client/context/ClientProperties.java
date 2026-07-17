package com.matrix.client.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hiot.client")
public class ClientProperties {
    private String name;
    private String desc;

}


