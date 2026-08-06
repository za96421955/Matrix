package com.matrix.service.service.agent.impl;

import com.matrix.common.constant.Constant;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.TaskMode;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * @description 规划模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class PlanPatternService extends AbstractPatternService<PatternRequest> {

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
        request.setMessages(this.buildMessages(request, clients, null));
        // ReAct Agent Call
        return this.call(request, sink -> {
            log.info("[规划模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[规划模式] userId={}, 执行【结束】", request.getUserId());
        });
    }

    /**
     * @description 执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void executor(FluxSink<Response> sink, PatternRequest request) {
        if (null == sink || null == request) {
            return;
        }
        // 记录模式
        patternContext.setPattern(request.getUserId(), request.getSessionId(), Constant.Pattern.PLAN);

        // 1. 规划
        String plan = this.getPlan(request.clone(), null, TaskMode.PLAN.getValue());
        if (null == plan) {
            // 用户交互
            return;
        }
        if (StringUtils.isBlank(plan)) {
            throw new RuntimeException("执行计划生成失败");
        }
        request.getMessages().add(Message.assistant(plan));

        // 2. 执行
        String result = this.executeTaskAction(request);
        if (null == result) {
            // 用户交互
            return;
        }
        // 清除模式缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
    }

}
