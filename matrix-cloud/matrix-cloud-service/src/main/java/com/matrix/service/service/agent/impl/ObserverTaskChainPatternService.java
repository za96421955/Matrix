//package com.matrix.service.service.agent.impl;
//
//import com.matrix.common.dto.model.Message;
//import com.matrix.common.dto.model.Response;
//import com.matrix.common.dto.request.PatternRequest;
//import com.matrix.common.enums.ErrorCode;
//import com.matrix.service.context.TaskPatternContext;
//import com.matrix.service.dal.entity.ClientInfo;
//import com.matrix.service.service.agent.AbstractPatternService;
//import com.matrix.service.service.agent.Prompt;
//import com.matrix.service.service.agent.schema.TaskChain;
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.FluxSink;
//
//import java.util.List;
//
///**
// * @description 任务链（观察者）模式
// * <p> <功能详细描述> </p>
// *
// * @author 陈晨
// */
//@Slf4j
//@Service
//public class ObserverTaskChainPatternService extends AbstractPatternService<PatternRequest> {
//
//    @Resource
//    private TaskPatternContext taskPatternContext;
//    @Resource
//    private TaskChainPatternService taskChainPatternService;
//
//    @Override
//    public Flux<Response> call(PatternRequest request) {
//        if (request == null) {
//            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
//        }
//        // 终端
//        List<ClientInfo> clients = clientService.getByUserIdAndOnline(request.getUserId());
//        // 工具
//        request.setTools(this.buildTools());
//        // 消息
//        request.setMessages(this.buildMessages(request, clients, null));
//        // ReAct Agent Call
//        return this.call(request, true, sink -> this.executor(sink, request));
//    }
//
//    /**
//     * @description 执行
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    public String executor(FluxSink<Response> sink, PatternRequest request) {
//        if (null == sink || null == request) {
//            return null;
//        }
//        int taskRetry = 0;
//        while (++taskRetry <= 3) {
//            boolean isTaskRetry = false;
//
//            // 1. 构建任务链
//            PatternRequest taskRequest = request.clone();
//            TaskChain taskChain = taskChainPatternService.buildTask(sink, taskRequest);
//            log.info("[任务链（观察者）] 构建任务链, userId={}, taskChain={}", request.getUserId(), taskChain);
//            if (null == taskChain) {
//                continue;
//            }
//
//            // 2. 执行任务块
//            for (TaskChain.ExecutionBlock block : taskChain.getBlocks()) {
//                PatternRequest executorRequest = null;
//                int executorRetry = 0;
//                while (++executorRetry <= 3) {
//                    // 2.1. 任务执行
//                    executorRequest = taskRequest.clone();
//                    taskChainPatternService.executorBlock(sink, executorRequest, block);
//
//                    // 2.2. 观察任务执行结果是否满足目标
//                    String result = this.callResultByClone(sink, executorRequest.clone(),
//                            Prompt.Observer.CHECK_RESULT.formatted(block.getGoal()));
//                    if (result.contains("true")) {
//                        break;
//                    }
//                    log.error("[任务链（观察者）] 任务执行结果不满足目标, userId={}, goal={}, reason={}",
//                            request.getUserId(), block.getGoal(), result);
//                    executorRequest.getMessages().add(Message.user(result));
//
//                    // 2.3. 不满足目标，是否需要重新规划任务
//                    result = this.callResultByClone(sink, executorRequest.clone(),
//                            Prompt.Observer.CHECK_TASK.formatted(block.getGoal()));
//                    if (result.contains("false")) {
//                        continue;
//                    }
//
//                    // 2.4. 需要重新规划任务
//                    log.error("[任务链（观察者）] 任务执行结果不满足目标 & 需要重新规划任务, userId={}, goal={}, reason={}",
//                            request.getUserId(), block.getGoal(), result);
//                    isTaskRetry = true;
//                    request.getMessages().add(Message.user(result));
//                    break;
//                }
//
//                // 2.4. 需要重新规划任务，清理缓存、重置任务
//                if (isTaskRetry) {
//                    log.info("[任务链（观察者）] 重新规划任务、清理当前任务缓存, userId={}", request.getUserId());
//                    taskPatternContext.clear(request.getUserId(), request.getSessionId());
//                    break;
//                }
//
//                // 2.5. 满足目标，替换 request，继续下一个任务
//                taskRequest = executorRequest;
//            }
//
//            // 3. 不需要重新规划，任务链结束
//            if (!isTaskRetry) {
//                break;
//            }
//        }
//
//        // 3. 结果总结
//        String result = this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
//        request.getMessages().removeLast();
//        request.getMessages().add(Message.assistant(result));
//        // 清理缓存
//        taskPatternContext.clear(request.getUserId(), request.getSessionId());
//        return result;
//    }
//
//}
//
//
