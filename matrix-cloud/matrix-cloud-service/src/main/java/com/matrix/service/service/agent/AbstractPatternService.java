package com.matrix.service.service.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Request;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.model.Role;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.service.cache.ServiceCache;
import com.matrix.service.context.ChatContext;
import com.matrix.service.context.PatternContext;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.context.ToolContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.dal.entity.MessageInfo;
import com.matrix.service.service.app.ApplicationService;
import com.matrix.service.service.chat.MessageService;
import com.matrix.service.service.task.Executor;
import com.matrix.service.service.tool.Tool;
import com.matrix.service.service.tool.impl.TimerTool;
import com.matrix.service.service.user.ClientService;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @description 模式服务抽象
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
public abstract class AbstractPatternService<T extends PatternRequest> implements PatternService<T, Response> {

    @Resource
    protected ServiceCache serviceCache;
    @Resource
    protected RegisterContext registerContext;
    @Resource
    protected ChatContext chatContext;
    @Resource
    protected ToolContext toolContext;
    @Resource
    protected PatternContext patternContext;

    @Resource
    protected Executor executor;
    @Resource
    protected ModelService modelService;
    @Resource
    protected ApplicationService applicationService;
    @Resource
    protected ClientService clientService;
    @Resource
    protected MessageService messageService;

    /**
     * @description ReAct Agent Call
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected Flux<Response> call(PatternRequest request, @Nullable Consumer<FluxSink<Response>> customCall) {
        return Flux.create(sink -> {
            try {
                if (customCall != null) {
                    customCall.accept(sink);
                } else {
                    this.call(sink, request);
                }
                sink.complete();
            } catch (Exception e) {
                // 记录 Error 消息
                this.saveErrorMessage(request.getUserId(), request.getSessionId(), e.getMessage());
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * @description ReAct Agent Call
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected Response call(FluxSink<Response> sink, PatternRequest request) {
        // 获取参数
        long userId = request.getUserId();
        long sessionId = request.getSessionId();
        request.setStream(false);
        // 获取模型
        String modelName = StringUtils.isNotBlank(request.getModel()) ?
                request.getModel() : Constant.Model.DEEPSEEK_V4_FLASH;
        RegisterCommand.Model model = registerContext.getModel(request.getUserId(), modelName);

        // ReAct 执行
        AtomicBoolean isContinue = new AtomicBoolean(false);
        Response response = null;
        do {
            // 【STOP】停止对话
            if (!TimerTool.getName().equals(request.getToolName())
                    && !chatContext.isConversationByCache(userId, sessionId)) {
                break;
            }
            isContinue.set(false);

            // 模型同步调用
            response = modelService.call(model, request.clone());
            log.info("\n\n>>> reasoning: \n{} \n\n >>> answer: \n{} \n\n >>> toolCalls: \n{}\n\n",
                    response.getReasoning(), response.getAnswer(), response.getToolCalls());
            // SSE 响应
            sink.next(response);
            // 模型调用失败, 抛出异常
            Response.Error error = response.getError();
            if (null != error) {
                log.error("[ReActAgent] model={}, 模型请求失败: {}", model.getModel(), error.getMessage());
                throw new RuntimeException(error.getMessage());
            }
            // 记录 Assistant 消息
            this.saveMessage(userId, sessionId, Role.ASSISTANT, response);

            // 【STOP】停止对话
            if (!TimerTool.getName().equals(request.getToolName())
                    && !chatContext.isConversationByCache(userId, sessionId)) {
                log.warn("\n\n======================\n\n\tS T O P: 模型调用【结束】\n\n======================\n\n");
                break;
            }

            // 若有工具调用, 则调用工具
            if (!CollectionUtils.isEmpty(response.getToolCalls())) {
                // messages 必须添加完整的 assistant 消息
                request.getMessages().add(Message.builder()
                        .role(Role.ASSISTANT)
                        .content(response.getAnswer())
                        .reasoning_content(response.getReasoning())
                        .tool_calls(response.getToolCalls())
                        .build());
                // 添加工具调用结果
                request.getMessages().addAll(this.toolCall(sink, request, response.getToolCalls()));
                isContinue.set(true);
            }
        } while (isContinue.get());
        return response;
    }

    /** callResultByClone操作 */
    protected String callResultByClone(FluxSink<Response> sink, PatternRequest request, String prompt) {
        PatternRequest localRequest = request.clone();
        if (StringUtils.isNotBlank(prompt)) {
            localRequest.getMessages().add(Message.user(prompt));
        }
        Response response = this.call(sink, localRequest);
        Message message = null != response ? response.getMessage() : null;
        return null != message ? message.getContent() : "";
    }

    /** callNoToolByClone操作 */
    protected String callNoToolByClone(FluxSink<Response> sink, PatternRequest request, String prompt) {
        PatternRequest localRequest = request.clone();
        if (!CollectionUtils.isEmpty(localRequest.getTools())) {
            localRequest.getTools().clear();
        }
        return this.callResultByClone(sink, localRequest, prompt);
    }


    /**
     * @description 工具调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected List<Message> toolCall(FluxSink<Response> sink,
                                     PatternRequest request,
                                     List<Response.ToolCall> toolCalls) {
        long userId = request.getUserId();
        long sessionId = request.getSessionId();
        List<Message> messages = new ArrayList<>();
        StringBuilder systemMessage = new StringBuilder();
        // 工具调度
        for (Response.ToolCall toolCall : toolCalls) {
            // 【STOP】停止对话
            if (!TimerTool.getName().equals(request.getToolName())
                    && !chatContext.isConversationByCache(userId, sessionId)) {
                break;
            }
            // 获取工具执行结果
            String result;
            try {
                Tool tool = toolContext.getTool(toolCall.getFunction().getName());
                if (null == tool) {
                    // 调用用户应用
                    result = applicationService.call(userId, sessionId, toolCall.getId(),
                            toolCall.getFunction().getName(), toolCall.getFunction().getArguments());
                } else {
                    // 调用系统工具
                    Flux<String> flux = tool.execute(userId, sessionId, toolCall.getId(),
                            JSON.parseObject(toolCall.getFunction().getArguments(), tool.requestType()));
                    // 用一个容器收集最终的答案
                    AtomicReference<String> resultHolder = new AtomicReference<>("");
                    flux.doOnNext(output -> {
                            try {
                                Response response = JSON.parseObject(output, Response.class);
                                sink.next(response);
                                if (StringUtils.isNotBlank(response.getAnswer())) {
                                    resultHolder.set(response.getAnswer());
                                }
                            } catch (Exception ex) {
                                resultHolder.set(output);
                            }
                        })
                        .doOnError(e -> resultHolder.set(e.getMessage()))
                        .blockLast();   // 阻塞直到流结束，拿到最后一次设置的值
                    result = resultHolder.get();
                    if (tool.isAnswer()) {
                        systemMessage.append(result).append("\n---\n");
                    }
                }
            } catch (Exception e) {
                log.error("[工具执行] userId={}, 异常: {}", userId, e.getMessage(), e);
                result = e.getMessage();
            }
            // 设置工具调用结果
            Response toolResult = Response.tool(toolCall.getId(), result);
            messages.add(toolResult.getMessage());
            // SSE 响应
            sink.next(toolResult);
            // 记录工具结果消息
            this.saveMessage(userId, sessionId, Role.TOOL, toolResult);
        }
        // 追加系统消息
        if (!systemMessage.isEmpty()) {
            messages.add(Message.system("## 输出结果原文拼接以下内容：\n```\n" +
                    systemMessage + "\n```"));
        }
        return messages;
    }

    /**
     * @description 构建消息集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected List<Message> buildMessages(PatternRequest request,
                                          List<ClientInfo> clientList,
                                          String prompt) {
        List<Message> buildMessages = new ArrayList<>(request.getMessages());

        // clients 不为空, 则设置 skill、client
        if (!CollectionUtils.isEmpty(clientList)) {
            // tool system prompt (如：读取 agent 记忆)
            for (Tool<?> tool : toolContext.getTools()) {
                for (ClientInfo client : clientList) {
                    String systemPrompt = tool.systemPrompt(request.getUserId(), request.getSessionId(),
                            client.getClientId());
                    if (StringUtils.isBlank(systemPrompt)) {
                        continue;
                    }
                    buildMessages.addFirst(Message.system(systemPrompt));
                }
            }
            // Skill
            StringBuilder sb = new StringBuilder();
            sb.append("## 可用 Skill 列表\n```");
            for (RegisterCommand.Skill skill : registerContext.getSkills(request.getUserId())) {
                if (null == skill) {
                    continue;
                }
                sb.append(skill.toPrompt(null));
            }
            sb.append("```");
            buildMessages.addFirst(Message.system(sb.toString()));

            // client
            StringBuilder clients = new StringBuilder();
            clients.append("## 终端列表");
            for (ClientInfo client : clientList) {
                if (null == client) {
                    continue;
                }
                clients.append(client.getPromptInfo());
            }
            buildMessages.addFirst(Message.system(clients.toString()));
        }

        StringBuilder firstUserMessage = new StringBuilder();
        // 操作终端
        if (StringUtils.isNotBlank(request.getClientId())) {
            firstUserMessage.append(Prompt.Common.OPERATION_CLIENT_ID.formatted(request.getClientId()));
        }
        // 工作目录
        if (StringUtils.isNotBlank(request.getItemPath())) {
            firstUserMessage.append(Prompt.Common.WORKING_DIRECTORY.formatted(request.getItemPath()));
        }
        // prompt
        if (StringUtils.isNotBlank(prompt)) {
            firstUserMessage.append(prompt);
        }
        if (StringUtils.isNotBlank(firstUserMessage)) {
            buildMessages.addFirst(Message.user(firstUserMessage.toString()));
        }
        return buildMessages;
    }

    /**
     * @description 构建 Agent 工具集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected List<Request.Tool> buildTools() {
        return this.buildTools(null);
    }

    /**
     * @description 构建 Agent 工具集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected List<Request.Tool> buildTools(Long userId) {
        Map<String, Request.Tool> tools = new HashMap<>();
        // 用户应用
        if (null != userId) {
            for (RegisterCommand.Application app : registerContext.getApps(userId)) {
                if (null == app) {
                    continue;
                }
                tools.put(app.getName(), this.buildTool(app, app.getDescription()));
            }
        }
        // 系统工具, 全部添加, 与用户工具重名则覆盖
        for (Tool<?> tool : toolContext.getTools()) {
            if (null == tool) {
                continue;
            }
            // 添加工具
            tools.put(tool.name(), this.buildTool(tool, tool.description()));
        }
        return new ArrayList<>(tools.values());
    }

    protected Request.Tool buildTool(Tool<?> tool, String description) {
        if (null == tool) {
            return null;
        }
        // 替换描述
        String toolDesc = description;
        if (StringUtils.isNotBlank(description)) {
            toolDesc = description;
        }
        return Request.Tool.init(tool.name(), toolDesc, tool.requestType());
    }

    protected Request.Tool buildTool(RegisterCommand.Application app, String description) {
        if (null == app) {
            return null;
        }
        // 替换描述
        String toolDesc = app.getDescription();
        if (StringUtils.isNotBlank(description)) {
            toolDesc = description;
        }
        toolDesc = "exclusive clientId: " + app.getClientId() + "\n---\n" + toolDesc;
        return Request.Tool.init(app.getName(), toolDesc, JSONSchemaUtil.generate(app.getInput()));
    }

    /**
     * @description 记录消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected void saveMessage(Long userId, Long sessionId, String role, Response response) {
        Message message = response.getMessage();
        if (null == message) {
            log.info("[记录消息] userId={}, sessionId={}, message is null", userId, sessionId);
            return;
        }
        try {
            MessageInfo save = MessageInfo.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .role(role)
                    .content(message.getContent())
                    .reasoning_content(message.getReasoning_content())
                    .tool_call_id(message.getTool_call_id())
                    .build();
            if (!CollectionUtils.isEmpty(message.getTool_calls())) {
                save.setTool_calls(JSONArray.toJSONString(message.getTool_calls()));
            }
            messageService.save(save);
            log.info("[记录消息] userId={}, sessionId={}, 消息入库: {}",
                    userId, sessionId, JSONObject.toJSONString(save));
        } catch (Exception e) {
            log.error("[记录消息] userId={}, sessionId={}, 记录消息异常: {}",
                    userId, sessionId, e.getMessage(), e);
        }
    }

    /**
     * @description 记录错误消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected void saveErrorMessage(Long userId, Long sessionId, String error) {
        if (StringUtils.isBlank(error)) {
            return;
        }
        try {
            MessageInfo save = MessageInfo.builder()
                    .role(Role.ERROR)
                    .userId(userId)
                    .sessionId(sessionId)
                    .content(error)
                    .build();
            messageService.save(save);
            log.info("[记录消息] userId={}, sessionId={}, 错误消息入库: {}",
                    userId, sessionId, JSONObject.toJSONString(save));
        } catch (Exception e) {
            log.error("[记录消息] userId={}, sessionId={}, 错误记录消息异常: {}",
                    userId, sessionId, e.getMessage(), e);
        }
    }

    /**
     * @description 移除 markdown 标记
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String removeCodeBlockMarkers(String input) {
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
        int startIndex = input.lastIndexOf(startMarker);
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


