package com.matrix.service.service.agent.impl;

import com.matrix.common.constant.Constant;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.TaskMode;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

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
    /** call操作 */
    public Flux<Response> call(PatternRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }
        // 终端
        List<ClientInfo> clients = clientService.getByUserIdAndOnline(request.getUserId());
        // 工具
        request.setTools(this.buildTools());
        // 消息
        request.setMessages(this.buildMessages(request, clients, null));
        // ReAct Agent Call
        return this.call(request, sink -> {
            log.info("[执行模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[执行模式] userId={}, 执行【结束】", request.getUserId());
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
        // 记录：执行模式
        patternContext.setPattern(request.getUserId(), request.getSessionId(), Constant.Pattern.EXECUTE);

        // 2. CoT 执行
        int count = 0;
        while (true) {
            log.info("[任务模式] 任务执行, userId={}, sessionId={}, 执行轮次: {}",
                    request.getUserId(), request.getSessionId(), ++count);
            // 【STOP】停止对话
            if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                log.warn("\n\n======================\n\n\tS T O P: 任务模式 CoT【结束】\n\n======================");
                return;
            }
            PatternRequest localRequest = request.clone();

            // 1. 规划
            String plan = this.getPlan(localRequest.clone(), null, this.getPlanMode(localRequest.clone()),
                    false);
            if (StringUtils.isBlank(plan)) {
                throw new RuntimeException("执行计划生成失败");
            }
            localRequest.getMessages().add(Message.assistant(plan));

            // 2. 执行
            if (TaskMode.SERIAL.getValue().equals(this.getActionMode(localRequest))) {
                this.executeTaskAction(localRequest);
            } else {
                this.executeTaskChain(localRequest);
            }

            // 3. 观察
            String observe = this.observe(localRequest, null);
            if (StringUtils.isBlank(observe)) {
                break;
            }
            // 任务继续
            request.getMessages().add(Message.user(observe));
        }

        // 3. 结果总结
//        this.callResultByClone(sink, request, Prompt.Common.REPORT_RESULTS);
        // 清除模式缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
    }

}
