package com.matrix.service.service.agent.impl;

import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.service.context.TaskPatternContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.TaskChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * @description 观察者模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class ObserverPatternService extends AbstractPatternService<PatternRequest> {

    @Resource
    private TaskPatternContext taskPatternContext;
    @Resource
    private TaskPatternService taskPatternService;

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
        return this.call(request, true, sink -> this.executor(sink, request));
    }

    /**
     * @description 执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String executor(FluxSink<Response> sink, PatternRequest request) {
        if (null == sink || null == request) {
            return null;
        }
        // 1. 构建任务链
        TaskChain taskChain = taskPatternService.buildTaskChain(sink, request.clone());
        log.info("[观察者模式] 任务列表, userId={}, taskChain={}", request.getUserId(), taskChain);
        if (null == taskChain) {
            return null;
        }
        // 2. 执行任务块
        for (TaskChain.ExecutionBlock block : taskChain.getBlocks()) {
            PatternRequest executorRequest;
            int retry = 0;
            do {
                // 最多重试3次
                if (++retry > 3) {
                    log.error("[观察者模式] 任务执行【结束】, userId={}, task={}, retry={}",
                            request.getUserId(), block.getGoal(), retry);
                    return null;
                }
                // 任务执行
                executorRequest = request.clone();
                taskPatternService.executorBlock(sink, executorRequest, block);
                // 观察任务执行结果是否满足目标
                String result = this.callResultByClone(sink, executorRequest, Prompt.Observer.CHECK_RESULT.formatted(
                        block.getGoal()));
                if (result.contains("true")) {
                    break;
                }
                executorRequest.getMessages().add(Message.user(result));
                // 不满足目标，是否需要重新规划任务
                result = this.callResultByClone(sink, executorRequest, Prompt.Observer.CHECK_TASK.formatted(
                        block.getGoal()));
                if (result.contains("false")) {
                    continue;
                }
                executorRequest.getMessages().add(Message.user(result));
            } while (true);
            // 满足目标，替换 request，继续下一个任务
            request = executorRequest;
        }
        // 3. 结果总结
        String result = this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
        request.getMessages().removeLast();
        request.getMessages().add(Message.assistant(result));
        // 清理缓存
        taskPatternContext.clear(request.getUserId(), request.getSessionId());
        return result;
    }

}


