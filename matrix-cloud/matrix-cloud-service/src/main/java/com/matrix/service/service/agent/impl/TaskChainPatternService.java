package com.matrix.service.service.agent.impl;

import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.service.service.agent.AbstractTaskPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.TaskChain;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @description 任务链模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class TaskChainPatternService extends AbstractTaskPatternService<TaskChain> {

    @Override
    protected Class<TaskChain> taskType() {
        return TaskChain.class;
    }

    @Override
    protected TaskChain getTaskCache(PatternRequest request) {
        return taskPatternContext.getTaskChain(request.getUserId(), request.getSessionId());
    }

    @Override
    protected void setTaskCache(PatternRequest request, TaskChain task) {
        taskPatternContext.setTaskChain(request.getUserId(), request.getSessionId(), task);
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
        int taskRetry = 0;
        while (++taskRetry <= 3) {
            boolean isTaskRetry = false;

            // 1. 构建任务链
            PatternRequest taskRequest = request.clone();
            TaskChain taskChain = this.buildTask(sink, taskRequest);
            log.info("[任务链模式] 构建任务链, userId={}, taskChain={}", request.getUserId(), taskChain);
            if (null == taskChain) {
                continue;
            }

            // 2. 执行任务块
            for (TaskChain.ExecutionBlock block : taskChain.getBlocks()) {
                // 2.1. 任务执行
                PatternRequest executorRequest = taskRequest.clone();
                int executorRetry = 0;
                while (++executorRetry <= 3) {
                    this.executorBlock(sink, executorRequest, block);

                    // 2.2. 观察任务执行结果是否满足目标
                    ObserverResult observerResult = this.observer(sink, executorRequest.clone(), block.getGoal());
                    if (observerResult.isSuccess()) {
                        break;
                    }
                    executorRequest.getMessages().add(Message.user(observerResult.getReason()));

                    // 2.3. 不满足目标，是否需要重新规划任务
                    if (observerResult.isTaskRetry()) {
                        isTaskRetry = true;
                        request.getMessages().add(Message.user(observerResult.getReason()));
                        break;
                    }
                }

                // 2.4. 需要重新规划任务，清理缓存、重置任务
                if (isTaskRetry) {
                    log.info("[任务链模式] 重新规划任务、清理当前任务缓存, userId={}", request.getUserId());
                    taskPatternContext.clear(request.getUserId(), request.getSessionId());
                    break;
                }

                // 2.5. 满足目标，替换 request，继续下一个任务
                taskRequest = executorRequest;
            }

            // 3. 不需要重新规划，任务链结束
            if (!isTaskRetry) {
                break;
            }
        }

        // 3. 结果总结
        String result = this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
        request.getMessages().removeLast();
        request.getMessages().add(Message.assistant(result));
        // 清理缓存
        taskPatternContext.clear(request.getUserId(), request.getSessionId());
        return result;
    }

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
//        // 1. 构建任务链
//        TaskChain taskChain = this.buildTask(sink, request.clone());
//        log.info("[任务链模式] 任务列表, userId={}, taskChain={}", request.getUserId(), taskChain);
//        if (null == taskChain) {
//            return null;
//        }
//        // 2. 执行任务块
//        for (TaskChain.ExecutionBlock block : taskChain.getBlocks()) {
//            this.executorBlock(sink, request, block);
//        }
//        // 3. 结果总结
//        String result = this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
//        request.getMessages().removeLast();
//        request.getMessages().add(Message.assistant(result));
//        // 清理缓存
//        taskPatternContext.clear(request.getUserId(), request.getSessionId());
//        return result;
//    }

    /**
     * @description 执行任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void executorBlock(FluxSink<Response> sink, PatternRequest request, TaskChain.ExecutionBlock block) {
        // 顺序执行
        if (null == block.getSync() || block.getSync()) {
            for (TaskChain.Task task : block.getTasks()) {
                String result = this.executorTaskRetry(sink, request.clone(), task, 0);
                if (StringUtils.isBlank(result)) {
                    continue;
                }
                request.getMessages().add(Message.user(task.getGoal()));
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
                    results.add(Message.user(task.getGoal()));
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
            log.error("[任务链模式] 任务执行失败, userId={}, task={}, retry={}",
                    request.getUserId(), task.getName(), retry);
            throw new RuntimeException("任务执行失败");
        }
        // 任务已完成, 直接返回 null
        if (taskPatternContext.isTaskComplete(request.getUserId(), request.getSessionId(), task.getName())) {
            return null;
        }
        // 执行任务
        log.info("[任务链模式] 任务执行【开始】, userId={}, task={}",
                request.getUserId(), task.getName());
        try {
            String result = this.executorTask(sink, request, task);
            if (StringUtils.isBlank(result)) {
                return this.executorTaskRetry(sink, request, task, ++retry);
            }
            log.info("[任务链模式] 任务执行【完成】, userId={}, task={}, result={}",
                    request.getUserId(), task.getName(), result);
            // 记录任务完成
            taskPatternContext.setTaskComplete(request.getUserId(), request.getSessionId(), task.getName());
            return result;
        } catch (Exception e) {
            log.error("[任务链模式] 任务执行【异常】, userId={}, task={}, {}",
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
        return this.callResultByClone(sink, request, Prompt.TaskChain.EXECUTOR_TASK.formatted(
                task.getWorkingDirectory(), task.getName(), task.getAction(), task.getGoal(), task.getResult()));
    }

}


