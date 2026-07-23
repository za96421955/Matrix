package com.matrix.service.service.agent;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.service.context.TaskPatternContext;
import com.matrix.service.dal.entity.ClientInfo;
import jakarta.annotation.Resource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * @description 任务模式抽象
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public abstract class AbstractTaskPatternService<T> extends AbstractPatternService<PatternRequest> {

    @Resource
    protected TaskPatternContext taskPatternContext;

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
    public abstract String executor(FluxSink<Response> sink, PatternRequest request);

    /**
     * @description 构建任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public T buildTask(FluxSink<Response> sink, PatternRequest request) {
        // 获取 task 缓存
        T task = this.getTaskCache(request);
        if (null != task) {
            return task;
        }
        // 生成任务列表
        task = this.generateTask(sink, request.clone(), 0);
        if (null == task) {
            return null;
        }
        request.getMessages().add(Message.assistant(task.toString()));
        // 任务列表健康检查
        String result = this.callResultByClone(sink, request, Prompt.Task.CHECK_TASK_LIST);
        if (result.contains("true")) {
            // 设置 task 缓存
            this.setTaskCache(request, task);
            return task;
        }
        // 添加问题原因, 重新构建任务列表
        request.getMessages().add(Message.user(result));
        return this.buildTask(sink, request);
    }

    protected abstract T getTaskCache(PatternRequest request);

    protected abstract void setTaskCache(PatternRequest request, T task);

    /**
     * @description 生成任务列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private T generateTask(FluxSink<Response> sink, PatternRequest request, int retry) {
        // 最多重试3次
        if (retry >= 3) {
            return null;
        }
        // 用户消息直接转换 task model
        try {
            Message input = request.getMessages().getLast();
            return JSON.parseObject(this.removeCodeBlockMarkers(input.getContent()), taskType());
        } catch (Exception ignore) {}

        // 生成 task chain
        String result = this.callResultByClone(sink, request, Prompt.Task.GENERATE_TASK.formatted(
                JSONSchemaUtil.generate(taskType())));
        try {
            return JSON.parseObject(this.removeCodeBlockMarkers(result), taskType());
        } catch (Exception e) {
            request.getMessages().add(Message.user("任务列表格式错误"));
            return this.generateTask(sink, request, ++retry);
        }
    }

    protected abstract Class<T> taskType();

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

}


