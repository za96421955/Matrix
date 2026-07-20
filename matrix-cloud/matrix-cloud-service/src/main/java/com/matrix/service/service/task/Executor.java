package com.matrix.service.service.task;

import reactor.core.publisher.Mono;

/**
 * 执行器
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Executor {

    /**
     * @description 执行任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Mono<String> executeAuth(Long userId, String command) throws Exception;

    /**
     * @description 执行任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Mono<String> executeTask(Long userId, String clientId, String command) throws Exception;

    /**
     * @description 执行指令
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Mono<String> executeCommand(String clientId, String command) throws Exception;

}


