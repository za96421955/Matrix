package com.matrix.service.service.agent;

import com.matrix.common.dto.model.Request;
import com.matrix.common.dto.model.Response;
import reactor.core.publisher.Flux;

/**
 * 模式服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface PatternService<T extends Request, R extends Response> {

    /**
     * @description 调用 Agent
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Flux<R> call(T request);

}


