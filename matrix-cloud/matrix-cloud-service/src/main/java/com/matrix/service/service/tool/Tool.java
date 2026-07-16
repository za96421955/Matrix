package com.matrix.service.service.tool;

import reactor.core.publisher.Flux;

/**
 * 工具
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Tool<R> {

    String name();

    String description();

    Class<R> requestType();

    String systemPrompt(Long userId, Long sessionId, String clientId);

    boolean isAnswer();

    Flux<String> execute(Long userId, Long sessionId, String toolCallId, R request);

}


