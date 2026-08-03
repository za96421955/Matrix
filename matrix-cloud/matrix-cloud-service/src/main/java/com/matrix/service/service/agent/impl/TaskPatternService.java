package com.matrix.service.service.agent.impl;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.OutputKeyword;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.TaskMode;
import com.matrix.common.util.ContentUtil;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.Smart;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * @description 任务模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class TaskPatternService extends AbstractPatternService<PatternRequest> {

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
            log.info("[任务模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[任务模式] userId={}, 执行【结束】", request.getUserId());
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
        // 记录：任务模式
        patternContext.setPattern(request.getUserId(), request.getSessionId(), Constant.Pattern.TASK);

        // 1. 规划任务目标
        Smart smart = null;
        if (this.isSmart(request)) {
            smart = this.generateSmart(request.clone(), 0);
            log.info("[任务模式] 规划任务目标, userId={}, sessionId={}, smart={}",
                    request.getUserId(), request.getSessionId(), smart);
            if (null == smart) {
                // 用户 todo
                return;
            }
        }

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

            // 1. 规划
            String plan = this.getPlan(request.clone(), smart, this.getPlanMode(request.clone()),
                    true);
            if (null == plan) {
                // 用户 todo
                return;
            }
            if (StringUtils.isBlank(plan)) {
                throw new RuntimeException("执行计划生成失败");
            }
            request.getMessages().add(Message.assistant(plan));

            // 2. 执行
            if (TaskMode.SERIAL.getValue().equals(this.getActionMode(request))) {
                this.executeTaskAction(request);
            } else {
                this.executeTaskChain(request);
            }

            // 3. 观察
            Boolean isContinue = this.observer(request, smart);
            if (null == isContinue) {
                return;
            }
            if (!isContinue) {
                break;
            }
        }

        // 3. 结果总结
        this.callResultByClone(sink, request, Prompt.Common.BRIEF_SUMMARY);
        // 清除模式缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
    }

    /**
     * @description 判断用户任务/需求是否需要 SMART 分析
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private boolean isSmart(PatternRequest request) {
        String isSmart = patternContext.getIsSmart(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(isSmart)) {
            String result = modelService.callAnswer(request.getMessages(), Prompt.Check.IS_SMART);
            patternContext.setIsSmart(request.getUserId(), request.getSessionId(), result);
            isSmart = result;
        }
        return isSmart.contains(OutputKeyword.TRUE);
    }

    /**
     * @description 生成任务目标
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Smart generateSmart(PatternRequest request, int retry) {
        if (retry >= 3) {
            return null;
        }
        String smart = patternContext.getSmart(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(smart)) {
            smart = this.callResultByClone(request, Prompt.CoT.SMART.formatted(
                    JSONSchemaUtil.generate(Smart.class)));
            // 检查
            request.getMessages().add(Message.assistant(smart));
            String check = modelService.callAnswer(request.getMessages(), Prompt.Check.GOAL);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        try {
            String json = ContentUtil.removeJsonMarkers(smart);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            Smart smartObj = JSON.parseObject(json, Smart.class);
            patternContext.setSmart(request.getUserId(), request.getSessionId(), json);
            return smartObj;
        } catch (Exception e) {
            // 格式错误，重试
            request.getMessages().add(Message.user(Prompt.Check.OUTPUT_FORMAT.formatted(e.getMessage())));
            return this.generateSmart(request, ++retry);
        }
    }

}


