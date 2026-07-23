package com.matrix.service.service.agent.impl;

import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.service.service.agent.AbstractTaskPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.TaskGraph;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description 任务图模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class TaskGraphPatternService extends AbstractTaskPatternService<TaskGraph> {

    @Override
    protected Class<TaskGraph> taskType() {
        return TaskGraph.class;
    }

    @Override
    protected TaskGraph getTaskCache(PatternRequest request) {
        return taskPatternContext.getTaskGraph(request.getUserId(), request.getSessionId());
    }

    @Override
    protected void setTaskCache(PatternRequest request, TaskGraph task) {
        taskPatternContext.setTaskGraph(request.getUserId(), request.getSessionId(), task);
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

            // 1. 构建任务图
            TaskGraph taskGraph = this.buildTask(sink, request.clone());
            log.info("[任务图模式] 任务列表, userId={}, taskGraph={}", request.getUserId(), taskGraph);
            if (null == taskGraph) {
                continue;
            }

            // 2. 执行任务图
            while (true) {
                PatternRequest executorRequest = request.clone();

                // 2.1. 获取待执行任务
                TaskGraph.Task task = this.getNextTask(sink, executorRequest, taskGraph, 0);
                if (null == task) {
                    log.info("[任务图模式] 任务结束, userId={}, sessionId={}",
                            request.getUserId(), request.getSessionId());
                    break;
                }

                // 2.2. 执行任务
                String taskResult = this.executorTaskRetry(sink, executorRequest.clone(), task, 0);
                if (StringUtils.isBlank(taskResult)) {
                    continue;
                }
                executorRequest.getMessages().add(Message.user(task.getGoal()));
                executorRequest.getMessages().add(Message.assistant(taskResult));

                // 2.3. 观察任务执行结果是否满足目标
                ObserverResult observerResult = this.observer(sink, executorRequest.clone(), task.getGoal());
                if (observerResult.isSuccess()) {
                    request.getMessages().add(Message.user(task.getGoal()));
                    request.getMessages().add(Message.assistant(taskResult));
                    continue;
                }
                executorRequest.getMessages().add(Message.user(observerResult.getReason()));

                // 2.4. 不满足目标，是否需要重新规划任务
                if (observerResult.isTaskRetry()) {
                    isTaskRetry = true;
                    request.getMessages().add(Message.user(observerResult.getReason()));
                    break;
                }
            }

            // 3. 需要重新规划
            if (isTaskRetry) {
                log.info("[任务图模式] 重新规划任务、清理当前任务缓存, userId={}", request.getUserId());
                taskPatternContext.clear(request.getUserId(), request.getSessionId());
                continue;
            }

            // 4. 检查最终目标, 不满足则重新规划任务
            ObserverResult observerResult = this.observer(sink, request.clone(), taskGraph.getUltimateGoal());
            if (!observerResult.isSuccess()) {
                log.info("[任务图模式] 任务最终结果检查不通过, 重新规划任务、清理当前任务缓存, userId={}, sessionId={}, reason={}",
                        request.getUserId(), request.getSessionId(), observerResult.getReason());
                taskPatternContext.clear(request.getUserId(), request.getSessionId());
                request.getMessages().add(Message.user(observerResult.getReason()));
                continue;
            }
            break;
        }

        // 5. 结果总结
        String result = this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
        request.getMessages().removeLast();
        request.getMessages().add(Message.assistant(result));
        // 清理缓存
        taskPatternContext.clear(request.getUserId(), request.getSessionId());
        return result;
    }

    /**
     * @description 获取下一个任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private TaskGraph.Task getNextTask(FluxSink<Response> sink,
                                       PatternRequest request,
                                       TaskGraph taskGraph,
                                       int retry) {
        if (retry >= 3) {
            log.error("[任务图模式] 获取任务失败, taskName 生成失败, userId={}, retry={}",
                    request.getUserId(), retry);
            throw new RuntimeException("获取任务失败");
        }
        // 获取已完成 taskName
        Set<String> completeTasks = taskPatternContext.getTaskComplete(request.getUserId(), request.getSessionId());
        // agent call 获取 next task name
        String taskName = this.callResultByClone(sink, request.clone(),
                Prompt.TaskGraph.NEXT.formatted(taskGraph.toString(), completeTasks.toString()));
        if (StringUtils.isBlank(taskName)) {
            return null;
        }
        // 任务已完成 或 taskName 生成错误，重新生成
        boolean isComplete = taskPatternContext.isTaskComplete(request.getUserId(), request.getSessionId(), taskName);
        Map<String, TaskGraph.Task> taskMap = taskGraph.getTasks().stream()
                .collect(Collectors.toMap(TaskGraph.Task::getName, task -> task));
        if (isComplete || null == taskMap.get(taskName)) {
            return this.getNextTask(sink, request.clone(), taskGraph, ++retry);
        }
        return taskMap.get(taskName);
    }

    /**
     * @description 执行任务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String executorTaskRetry(FluxSink<Response> sink,
                                     PatternRequest request,
                                     TaskGraph.Task task,
                                     int retry) {
        // 最多重试3次
        if (retry >= 3) {
            log.error("[任务图模式] 任务执行失败, userId={}, task={}, retry={}",
                    request.getUserId(), task.getName(), retry);
            throw new RuntimeException("任务执行失败");
        }
        // 任务已完成, 直接返回 null
        if (taskPatternContext.isTaskComplete(request.getUserId(), request.getSessionId(), task.getName())) {
            return null;
        }
        // 执行任务
        log.info("[任务图模式] 任务执行【开始】, userId={}, task={}",
                request.getUserId(), task.getName());
        try {
            String result = this.executorTask(sink, request, task);
            if (StringUtils.isBlank(result)) {
                return this.executorTaskRetry(sink, request, task, ++retry);
            }
            log.info("[任务图模式] 任务执行【完成】, userId={}, task={}, result={}",
                    request.getUserId(), task.getName(), result);
            // 记录任务完成
            taskPatternContext.setTaskComplete(request.getUserId(), request.getSessionId(), task.getName());
            return result;
        } catch (Exception e) {
            log.error("[任务图模式] 任务执行【异常】, userId={}, task={}, {}",
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
    private String executorTask(FluxSink<Response> sink, PatternRequest request, TaskGraph.Task task) {
        // 【STOP】停止对话
        if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
            log.warn("\n\n======================\n\n\tS T O P: 任务【结束】\n\n======================\n\n");
            // 用户主动停止对话, 清理任务缓存
            taskPatternContext.clear(request.getUserId(), request.getSessionId());
            return null;
        }
        // 3. 执行任务, 获取任务结果
        return this.callResultByClone(sink, request, Prompt.TaskGraph.EXECUTOR_TASK.formatted(
                task.getWorkingDirectory(), task.getName(), task.getAction(), task.getGoal(), task.getExpect()));
    }

}


