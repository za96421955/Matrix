package com.matrix.client.context;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(name = "matrix.thread-pool.enabled", havingValue = "true")
public class ThreadPoolConfig {

    @Bean
    public ExecutorService threadPoolExecutor() {
        return new ThreadPoolExecutor(
                10,
                50,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),   // 队列最多 1000 条等待
                new ThreadPoolExecutor.CallerRunsPolicy()  // 背压：让 MQTT 线程自己跑，减慢接收速度
        );
    }

}


