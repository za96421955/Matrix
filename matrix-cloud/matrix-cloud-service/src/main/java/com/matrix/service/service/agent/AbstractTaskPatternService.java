//package com.matrix.service.service.agent;
//
//import com.alibaba.fastjson2.JSON;
//import com.matrix.common.dto.model.Message;
//import com.matrix.common.dto.model.Response;
//import com.matrix.common.dto.request.PatternRequest;
//import com.matrix.common.enums.ErrorCode;
//import com.matrix.common.util.JSONSchemaUtil;
//import com.matrix.service.context.TaskPatternContext;
//import com.matrix.service.dal.entity.ClientInfo;
//import jakarta.annotation.Resource;
//import lombok.Builder;
//import lombok.Data;
//import lombok.extern.slf4j.Slf4j;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.FluxSink;
//
//import java.io.Serial;
//import java.io.Serializable;
//import java.util.List;
//
///**
// * @description 任务模式抽象
// * <p> <功能详细描述> </p>
// *
// * @author 陈晨
// */
//@Slf4j
//public abstract class AbstractTaskPatternService<T> extends AbstractPatternService<PatternRequest> {
//
//    @Resource
//    protected TaskPatternContext taskPatternContext;
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
//        return this.call(request, sink -> this.executor(sink, request));
//    }
//
//    /**
//     * @description 执行
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    public abstract String executor(FluxSink<Response> sink, PatternRequest request);
//
//    /**
//     * @description 构建任务列表
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    public T buildTask(FluxSink<Response> sink, PatternRequest request) {
//        // 获取 task 缓存
//        T task = this.getTaskCache(request);
//        if (null != task) {
//            return task;
//        }
//        // 生成任务列表
//        task = this.generateTask(sink, request.clone(), 0);
//        if (null == task) {
//            return null;
//        }
//        request.getMessages().add(Message.assistant(task.toString()));
//        // 任务列表健康检查
//        String result = this.callResultByClone(sink, request, Prompt.Task.CHECK_TASK_LIST);
//        if (result.contains("true")) {
//            // 设置 task 缓存
//            this.setTaskCache(request, task);
//            return task;
//        }
//        // 添加问题原因, 重新构建任务列表
//        request.getMessages().add(Message.user(result));
//        return this.buildTask(sink, request);
//    }
//
//    protected abstract T getTaskCache(PatternRequest request);
//
//    protected abstract void setTaskCache(PatternRequest request, T task);
//
//    /**
//     * @description 生成任务列表
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    private T generateTask(FluxSink<Response> sink, PatternRequest request, int retry) {
//        // 最多重试3次
//        if (retry >= 3) {
//            return null;
//        }
//        // 用户消息直接转换 task model
//        try {
//            Message input = request.getMessages().getLast();
//            return JSON.parseObject(this.removeCodeBlockMarkers(input.getContent()), taskType());
//        } catch (Exception ignore) {}
//
//        // 生成 task chain
//        String result = this.callResultByClone(sink, request, Prompt.Task.GENERATE_TASK.formatted(
//                JSONSchemaUtil.generate(taskType())));
//        try {
//            return JSON.parseObject(this.removeCodeBlockMarkers(result), taskType());
//        } catch (Exception e) {
//            request.getMessages().add(Message.user("任务列表格式错误"));
//            return this.generateTask(sink, request, ++retry);
//        }
//    }
//
//    protected abstract Class<T> taskType();
//
//    /**
//     * @description 观察任务执行结果
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    protected ObserverResult observer(FluxSink<Response> sink, PatternRequest request, String goal) {
//        // 1. 观察任务执行结果是否满足目标
//        String checkResult = this.callResultByClone(sink, request.clone(),
//                Prompt.Observer.CHECK_RESULT.formatted(goal));
//        if (checkResult.contains("true")) {
//            return ObserverResult.builder()
//                    .success(true)
//                    .build();
//        }
//        log.error("[任务模式 (观察者)] 任务执行结果不满足目标, userId={}, sessionId={}, goal={}, reason={}",
//                request.getUserId(), request.getSessionId(), goal, checkResult);
//        request.getMessages().add(Message.user(checkResult));
//
//        // 2. 不满足目标，是否需要重新规划任务
//        checkResult = this.callResultByClone(sink, request.clone(),
//                Prompt.Observer.CHECK_TASK.formatted(goal));
//        if (checkResult.contains("false")) {
//            return ObserverResult.builder()
//                    .success(false)
//                    .reason(checkResult)
//                    .build();
//        }
//
//        // 3. 需要重新规划任务
//        log.error("[任务模式 (观察者)] 任务执行结果不满足目标 & 需要重新规划任务, userId={}, sessionId={}, goal={}, reason={}",
//                request.getUserId(), request.getSessionId(), goal, checkResult);
//        return ObserverResult.builder()
//                .success(false)
//                .taskRetry(true)
//                .reason(checkResult)
//                .build();
//    }
//
//    @Data
//    @Builder
//    protected static class ObserverResult implements Serializable {
//        @Serial
//        private static final long serialVersionUID = -8757955283244354760L;
//
//        private boolean success;
//        private boolean taskRetry;
//        private String reason;
//    }
//
//}
//
//
