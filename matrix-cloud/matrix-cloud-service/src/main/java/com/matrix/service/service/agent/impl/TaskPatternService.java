package com.matrix.service.service.agent.impl;

import com.alibaba.fastjson2.JSON;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @description 任务模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class TaskPatternService extends AbstractPatternService<PatternRequest> {

    @Resource
    private TaskPatternContext taskPatternContext;

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
        request.setMessages(this.buildMessages(request.getUserId(), request.getSessionId(),
                null, request.getMessages(), clients));
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
        TaskChain taskChain = this.buildTaskChain(sink, request.clone());
        log.info("[任务模式] 任务列表, userId={}, taskChain={}", request.getUserId(), taskChain);
        if (null == taskChain) {
            return null;
        }
        // 2. 执行任务块
        for (TaskChain.ExecutionBlock block : taskChain.getBlocks()) {
            this.executorBlock(sink, request, block);
        }
        // 3. 结果总结
        String result = this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
        request.getMessages().removeLast();
        request.getMessages().add(Message.assistant(result));
        // 清理缓存
        taskPatternContext.clear(request.getUserId(), request.getSessionId());
        return result;
    }

    /**
     * @description 构建任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private TaskChain buildTaskChain(FluxSink<Response> sink, PatternRequest request) {
        // 获取 task chain 缓存
        TaskChain taskChain = taskPatternContext.getTaskChain(request.getUserId(), request.getSessionId());
        if (null != taskChain) {
            return taskChain;
        }
        // 生成任务列表
        taskChain = this.generateTaskChain(sink, request.clone(), 0);
        if (null == taskChain) {
            return null;
        }
        request.getMessages().add(Message.assistant(taskChain.toString()));
        // 任务列表健康检查
        String result = this.callResultByClone(sink, request, Prompt.Task.CHECK_TASK_LIST);
        if (result.contains("true")) {
            // 设置 task chain 缓存
            taskPatternContext.setTaskChain(request.getUserId(), request.getSessionId(), taskChain);
            return taskChain;
        }
        // 添加问题原因, 重新构建任务列表
        request.getMessages().add(Message.user(result));
        return this.buildTaskChain(sink, request);
    }

    /**
     * @description 生成任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private TaskChain generateTaskChain(FluxSink<Response> sink, PatternRequest request, int retry) {
        // 最多重试3次
        if (retry >= 3) {
            return null;
        }
        // 用户消息直接转换 task model
        TaskChain taskChain = null;
        try {
            Message input = request.getMessages().getLast();
            taskChain = JSON.parseObject(this.removeCodeBlockMarkers(input.getContent()), TaskChain.class);
        } catch (Exception ignore) {}
        if (null != taskChain
                && !CollectionUtils.isEmpty(taskChain.getBlocks())
                && !CollectionUtils.isEmpty(taskChain.getBlocks().getFirst().getTasks())) {
            return taskChain;
        }
        // 生成 task chain
        String result = this.callResultByClone(sink, request, Prompt.Task.GENERATE_TASK);
        try {
            return JSON.parseObject(this.removeCodeBlockMarkers(result), TaskChain.class);
        } catch (Exception e) {
            request.getMessages().add(Message.user("任务列表格式错误"));
            return this.generateTaskChain(sink, request, ++retry);
        }
    }

    /**
     * @description 移除 markdown 标记
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String removeCodeBlockMarkers(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 定义开始标记和结束标记
        String startMarker = "```json";
        String endMarker = "```";

        // 检查是否以开始标记开头
        if (!input.contains(startMarker)) {
            return input;
        }

        // 找到结束标记的位置（从末尾找）
        int startIndex = input.indexOf(startMarker);
        int endIndex = input.lastIndexOf(endMarker);
        if (endIndex == -1 || endIndex <= startIndex) {
            return input;
        }

        // 截取开始标记之后、结束标记之前的内容
        int startContentIndex = startIndex + startMarker.length();
        String content = input.substring(startContentIndex, endIndex);

        // 可选：去除内容首尾的空白字符（如换行符）
        content = content.trim();
        return content;
    }

    /**
     * @description 执行任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private void executorBlock(FluxSink<Response> sink, PatternRequest request, TaskChain.ExecutionBlock block) {
        // 顺序执行
        if (null == block.getSeq() || block.getSeq()) {
            for (TaskChain.Task task : block.getTasks()) {
                String result = this.executorTaskRetry(sink, request.clone(), task, 0);
                if (StringUtils.isBlank(result)) {
                    continue;
                }
                request.getMessages().add(Message.user(task.getInput()));
                request.getMessages().add(Message.assistant(result));
            }
        }
        // 并行执行
        else {
            List<CompletableFuture<Void>> taskFutures = new ArrayList<>();
            PatternRequest localRequest = request.clone();
            for (TaskChain.Task task : block.getTasks()) {
                taskFutures.add(CompletableFuture.runAsync(() -> {
                    String result = this.executorTaskRetry(sink, localRequest.clone(), task, 0);
                    if (StringUtils.isBlank(result)) {
                        return;
                    }
                    // 线程安全，保证 user、assistant 成对
                    List<Message> results = new ArrayList<>();
                    results.add(Message.user(task.getInput()));
                    results.add(Message.assistant(result));
                    request.getMessages().addAll(results);
                }));
            }
            // 等待所有并行任务完成
            CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join();
        }
    }

    /**
     * @description 执行任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String executorTaskRetry(FluxSink<Response> sink,
                                     PatternRequest request,
                                     TaskChain.Task task,
                                     int retry) {
        // 最多重试3次
        if (retry >= 3) {
            log.error("[任务模式] 任务执行【结束】, userId={}, task={}, retry={}",
                    request.getUserId(), task.getName(), retry);
            return null;
        }
        // 任务已完成, 直接返回 null
        if (taskPatternContext.isTaskComplete(request.getUserId(), request.getSessionId(), task.getName())) {
            return null;
        }
        // 执行任务
        log.info("[任务模式] 任务执行【开始】, userId={}, task={}",
                request.getUserId(), task.getName());
        try {
            String result = this.executorTask(sink, request, task);
            if (StringUtils.isBlank(result)) {
                return this.executorTaskRetry(sink, request, task, ++retry);
            }
            log.info("[任务模式] 任务执行【完成】, userId={}, task={}, result={}",
                    request.getUserId(), task.getName(), result);
            // 记录任务完成
            taskPatternContext.setTaskComplete(request.getUserId(), request.getSessionId(), task.getName());
            return result;
        } catch (Exception e) {
            log.error("[任务模式] 任务执行【异常】, userId={}, task={}, {}",
                    request.getUserId(), task.getName(), e.getMessage(), e);
            request.getMessages().add(Message.user(e.getMessage()));
            return this.executorTaskRetry(sink, request, task, ++retry);
        }
    }

    /**
     * @description 执行任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String executorTask(FluxSink<Response> sink, PatternRequest request, TaskChain.Task task) {
        // 【STOP】停止对话
        if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
            log.warn("\n\n======================\n\n\tS T O P: 任务【结束】\n\n======================\n\n");
            // 用户主动停止对话, 清理任务缓存
            taskPatternContext.clear(request.getUserId(), request.getSessionId());
            return null;
        }
        // 3. 执行任务, 获取任务结果
        return this.callResultByClone(sink, request, Prompt.Task.EXECUTOR_TASK.formatted(
                task.getName(), task.getInput(), task.getExpectedResult(), task.getWorkingDirectory()));
    }

}


