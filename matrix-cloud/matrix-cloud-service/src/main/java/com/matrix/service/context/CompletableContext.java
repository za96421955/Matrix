package com.matrix.service.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 等待结果
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class CompletableContext {
    private static final Map<String, CompletableFuture<String>> PENDING = new ConcurrentHashMap<>();

    /**
     * @description 异步派遣任务，等待结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public Mono<String> dispatch(String topic, long timeoutSeconds) {
        CompletableFuture<String> future = new CompletableFuture<>();
        PENDING.put(topic, future);
        return Mono.fromFuture(future)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doFinally(s -> PENDING.remove(topic));
    }

    /**
     * @description 任务完成
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void complete(String topic, String result) {
        CompletableFuture<String> future = PENDING.remove(topic);
        if (future != null) {
            future.complete(result);
        }
    }

}
