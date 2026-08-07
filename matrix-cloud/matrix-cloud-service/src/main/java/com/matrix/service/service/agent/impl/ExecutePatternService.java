package com.matrix.service.service.agent.impl;

import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @description 执行模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class ExecutePatternService extends AbstractPatternService<PatternRequest> {

    @Override
    public Flux<Response> call(PatternRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }
        // 重置上下文
        this.resetContext(request);
        // 终端
        List<ClientInfo> clients = clientService.getByUserIdAndOnline(request.getUserId());
        // 工具
        request.setTools(this.buildTools());
        // 消息
        request.setMessages(this.buildMessages(request, clients, Prompt.Action.EXECUTE));
        // ReAct Agent Call
        return this.call(request, sink -> {
            log.info("[执行模式] userId={}, 执行【开始】", request.getUserId());
            patternContext.setStatus(request.getUserId(), request.getSessionId(), "任务执行中");
            this.call(sink, request, false);
            // 清除模式缓存
            patternContext.clear(request.getUserId(), request.getSessionId());
            log.info("[执行模式] userId={}, 执行【结束】", request.getUserId());
        });
    }

}
