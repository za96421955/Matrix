package com.matrix.client.service;

import com.matrix.client.context.ServiceProperties;
import com.matrix.client.util.HttpClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * @description 执行结果 ACK 服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class AckService {

    @Resource
    private ServiceProperties serviceProperties;
    @Resource
    private Fingerprint fingerprint;

    public boolean send(String taskId, String result) throws IOException, InterruptedException {
        String url = serviceProperties.getAck() + "/" + taskId;
        String response = HttpClient.post(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .authorization(serviceProperties.getApiKey())
                .header("X-Device-Id", fingerprint.get())
                .body(result)
                .asString();
        log.info("[指令回执] taskId={}, ACK Response: {}",  taskId, response);
        return true;
    }

}
