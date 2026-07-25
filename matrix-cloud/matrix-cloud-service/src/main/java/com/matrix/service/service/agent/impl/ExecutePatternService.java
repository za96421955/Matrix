package com.matrix.service.service.agent.impl;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.Smart;
import lombok.extern.slf4j.Slf4j;
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
        return this.call(request, true, sink -> {
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
        // 1. 规划任务目标
        Smart smart = this.generateSmart(sink, request.clone());
        log.info("[执行模式] 规划任务目标, userId={}, sessionId={}, smart={}",
                request.getUserId(), request.getSessionId(), smart);
        if (null == smart) {
            return;
        }

        // 2. CoT
        while (true) {
            // 【STOP】停止对话
            if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                log.warn("\n\n======================\n\n\tS T O P: 执行模式 CoT【结束】\n\n======================\n\n");
                return;
            }
            PatternRequest executorRequest = request.clone();

            // 1. 规划
            String plan = this.callResultByClone(sink, executorRequest, Prompt.CoT.PLAN.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound()));
            log.info("[执行模式] 任务规划, userId={}, sessionId={}, plan={}",
                    request.getUserId(), request.getSessionId(), plan);

            // 2. 执行
            executorRequest.getMessages().add(Message.user(plan));
            String result = this.callResultByClone(sink, executorRequest, Prompt.CoT.EXECUTE);
            log.info("[执行模式] 任务执行, userId={}, sessionId={}, result={}",
                    request.getUserId(), request.getSessionId(), result);

            // 3. 观察
            executorRequest.getMessages().add(Message.user(plan));
            executorRequest.getMessages().add(Message.assistant(result));
            String observe = this.callResultByClone(sink, executorRequest, Prompt.CoT.OBSERVE.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound()));
            log.info("[执行模式] 任务执行结果观察, userId={}, sessionId={}, observe={}",
                    request.getUserId(), request.getSessionId(), observe);
            if (observe.contains("TERMINATED")) {
                return;
            }
            if (observe.contains("TRUE")) {
                break;
            }
            request.getMessages().add(Message.assistant(result));
            request.getMessages().add(Message.user(observe));
        }

        // 3. 结果总结
        this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
    }

    /**
     * @description 生成任务目标
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Smart generateSmart(FluxSink<Response> sink, PatternRequest request) {
        String result = this.callResultByClone(sink, request, Prompt.SMART.CONFIRM.formatted(
                JSONSchemaUtil.generate(Smart.class)));
        try {
            return JSON.parseObject(this.removeCodeBlockMarkers(result), Smart.class);
        } catch (Exception e) {
            // 若格式错误，则说明仍在与用户沟通中
            return null;
        }
    }

}


