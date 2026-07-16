package com.matrix.service.service.agent.impl;

import com.matrix.common.constant.Constant;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.ChatRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.InformationPattern;
import com.matrix.service.context.InformationPatternContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * @description 资料模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class InformationPatternService extends AbstractPatternService<ChatRequest> {

    @Resource
    private InformationPatternContext informationPatternContext;
    @Resource
    private TaskPatternService taskPatternService;

    @Override
    public Flux<Response> call(ChatRequest request) {
        if (request == null || StringUtils.isBlank(request.getItemPath())) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }
        // 终端
        List<ClientInfo> clients = clientService.getByUserIdAndOnline(request.getUserId());
        if (CollectionUtils.isEmpty(clients)) {
            return Flux.just(Response.error(ErrorCode.CLIENT_NOT_FOUND.getMessage()));
        }
        // 工具
        request.setTools(this.buildTools());
        // 消息
        request.setMessages(this.buildMessages(request.getUserId(), request.getSessionId(),
                null, request.getMessages(), clients));
        // ReAct Agent Call
        return this.call(request, true, sink -> {
            log.info("[资料模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[资料模式] userId={}, 执行【结束】", request.getUserId());
        });
    }

    /**
     * @description 执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void executor(FluxSink<Response> sink, ChatRequest request) {
        if (null == sink || null == request) {
            return;
        }
        // 获取环节
        int no = informationPatternContext.getPatternNo(request.getUserId(), request.getSessionId());
        // agent call
        String result = this.callNoToolByClone(sink, request, Prompt.Pattern.GET_PATTERN_NO.formatted(
                request.getItemPath(), InformationPattern.getPrompt(), no));
        try {
            no = Integer.parseInt(result);
        } catch (Exception e) {
            no = InformationPattern.DEMAND_ANALYZE.getNo();
            log.info("[资料模式] userId={}, no={}, 新任务/环节信息不存在, 重新设置环节",
                    request.getUserId(), no);
        }
        // 设置环节
        informationPatternContext.setPatternNo(request.getUserId(), request.getSessionId(), no);
        log.info("[资料模式] userId={}, no={}, 设置环节", request.getUserId(), no);

        // 环节处理
        while ((no = informationPatternContext.getPatternNo(request.getUserId(), request.getSessionId())) > 0) {
            // 1. 需求分析
            if (InformationPattern.DEMAND_ANALYZE.eq(no)) {
                // agent call
                result = this.callResultByClone(sink, request, Prompt.Information.DEMAND_ANALYZE.formatted(
                        request.getItemPath(), Constant.PASS));
                request.getMessages().add(Message.assistant(result));
                log.info("[资料模式] userId={}, result={}, 需求分析", request.getUserId(), result);
                // 下一环节
                if (result.contains(Constant.PASS)) {
                    informationPatternContext.next(request.getUserId(), request.getSessionId(),
                            InformationPattern.PLAN);
                    continue;
                } else {
                    break;
                }
            }

            // 2. 任务规划
            if (InformationPattern.PLAN.eq(no)) {
                // agent call
                result = this.callResultByClone(sink, request, Prompt.Information.PLAN_OPERATION.formatted(
                        request.getItemPath(), Constant.PASS));
                request.getMessages().add(Message.assistant(result));
                log.info("[资料模式] userId={}, result={}, 任务规划", request.getUserId(), result);
                // 下一环节
                if (result.contains(Constant.PASS)) {
                    informationPatternContext.next(request.getUserId(), request.getSessionId(),
                            InformationPattern.EXECUTOR);
                    continue;
                } else {
                    break;
                }
            }

            // 3. 任务执行
            if (InformationPattern.EXECUTOR.eq(no)) {
                // agent call
                String prompt = Prompt.Pattern.PROJECT_DIRECTORY.formatted(request.getItemPath()) + "执行<资料整理>任务";
                request.getMessages().add(Message.user(prompt));
                result = taskPatternService.executor(sink, request);
                log.info("[资料模式] userId={}, result={}, 任务执行", request.getUserId(), result);
                // 下一环节
                informationPatternContext.next(request.getUserId(), request.getSessionId(),
                        InformationPattern.OUTPUT);
                continue;
            }

            // 4. 任务输出
            if (InformationPattern.OUTPUT.eq(no)) {
                // agent call
                result = this.callResultByClone(sink, request, Prompt.Information.OUTPUT_HTML.formatted(
                        request.getItemPath()));
                request.getMessages().add(Message.assistant(result));
                log.info("[资料模式] userId={}, result={}, 任务输出", request.getUserId(), result);
                // 任务结束, 清理缓存
                informationPatternContext.clear(request.getUserId(), request.getSessionId());
                break;
            }

            // 没有经过任何处理, 退出
            log.warn("[资料模式] userId={}, no={}, 无法处理的环节【退出】", request.getUserId(), no);
            break;
        }
    }

}


