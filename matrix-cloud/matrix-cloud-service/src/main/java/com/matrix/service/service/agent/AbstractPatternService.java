package com.matrix.service.service.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.OutputKeyword;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Request;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.model.Role;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.TaskMode;
import com.matrix.common.util.ContentUtil;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.service.cache.ServiceCache;
import com.matrix.service.context.ChatContext;
import com.matrix.service.context.PatternContext;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.context.ToolContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.dal.entity.MessageInfo;
import com.matrix.service.service.agent.schema.Smart;
import com.matrix.service.service.agent.schema.TaskActions;
import com.matrix.service.service.agent.schema.TaskChain;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    @Resource
    protected ScenarioClassifier scenarioClassifier;

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
            if (null != sink) {
                sink.next(response);
            }
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
        String result = "";
        if (null != message) {
            result = StringUtils.isNotBlank(message.getContent())
                    ? message.getContent()
                    : message.getReasoning_content();
        }
        return result;
    }
    protected String callResultByClone(PatternRequest request, String prompt) {
        return this.callResultByClone(null, request, prompt);
    }

    /** callNoToolByClone操作 */
    protected String callNoToolByClone(PatternRequest request, String prompt) {
        PatternRequest localRequest = request.clone();
        if (!CollectionUtils.isEmpty(localRequest.getTools())) {
            localRequest.getTools().clear();
        }
        return this.callResultByClone(localRequest, prompt);
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
            if (null != sink) {
                sink.next(toolResult);
            }
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
     * @description 获取执行计划生成模式
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String getPlanMode(PatternRequest request) {
        String planMode = patternContext.getPlanMode(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(planMode)) {
            return planMode;
        }
        try {
            planMode = modelService.callAnswer(request.getMessages(), Prompt.CoT.PLAN_MODE);
            if (planMode.contains(TaskMode.ASPECT.getValue())) {
                planMode = TaskMode.ASPECT.getValue();
            } else if (planMode.contains(TaskMode.EVALUATION.getValue())) {
                planMode = TaskMode.EVALUATION.getValue();
            } else {
                planMode = TaskMode.PLAN.getValue();
            }
            patternContext.setPlanMode(request.getUserId(), request.getSessionId(), planMode);
            log.info("[任务模式] 执行计划生成模式, userId={}, sessionId={}, planMode={}",
                    request.getUserId(), request.getSessionId(), planMode);
            return planMode;
        } catch (Exception e) {
            return TaskMode.PLAN.getValue();
        }
    }

    /**
     * @description 获取执行计划
     * <p>
     *     1 步: 直接 Plan
     *     2-6 步: 素朴切面 (MoA)
     *     7-10 步: 素朴切面 + 评论修正 (MoA)
     *     大于 10 步: 素朴切面 + 思考帽 & SWOT (MoA)
     * </p>
     *
     * @author 陈晨
     */
    protected String getPlan(PatternRequest request, Smart smart, String planMode, boolean interruptible) {
        String plan = patternContext.getPlan(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(plan)) {
            return plan;
        }
        // 直接 Plan
        if (TaskMode.PLAN.getValue().equals(planMode)) {
            String prompt = null == smart ? Prompt.CoT.PLAN : Prompt.CoT.PLAN_SMART.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound());
            plan = this.callResultByClone(request, prompt);
        } else {
            // 多计划综合评估
            List<String> plans;
            // 素朴切面 (MoA)
            if (TaskMode.ASPECT.getValue().equals(planMode)) {
                plans = this.getPlansByAspect(request, smart);
                log.info("[任务模式] 任务规划: 素朴切面, userId={}, sessionId={}, planMode={}, plans={}",
                        request.getUserId(), request.getSessionId(), planMode, plans);
            }
            // 素朴切面 + 评论修正 (MoA)
            else {
                plans = this.getPlansByAspectAndEvaluation(request, smart);
                log.info("[任务模式] 任务规划: 素朴切面 + 思考帽/SWOT修正, userId={}, sessionId={}, planMode={}, plans={}",
                        request.getUserId(), request.getSessionId(), planMode, plans);
            }
            // 融合
            for (int i = 0; i < plans.size(); i++) {
                request.getMessages().add(Message.assistant(
                        "#执行计划 " + ((char) ('A' + i)) + ": \n" +
                                plans.get(i)));
            }
            String prompt = null == smart ? Prompt.MoA.CONVERGE : Prompt.MoA.CONVERGE_SMART.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound());
            plan = this.callResultByClone(request, prompt);
        }
        log.info("[任务模式] 任务规划, userId={}, sessionId={}, planMode={}, plan={}",
                request.getUserId(), request.getSessionId(), planMode, plan);

        // 检查
        if (interruptible) {
            request.getMessages().add(Message.assistant(plan));
            String check = modelService.callAnswer(request.getMessages(), Prompt.Check.PLAN);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        patternContext.setPlan(request.getUserId(), request.getSessionId(), plan);
        return plan;
    }

    /**
     * @description 并行切面, 获取多个执行计划
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private List<String> getPlansByAspect(PatternRequest request, Smart smart) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        for (String direction : Prompt.MoA.DIRECTIONS) {
            futures.add(CompletableFuture.runAsync(() -> {
                String prompt = null == smart ? Prompt.MoA.ASPECT : Prompt.MoA.ASPECT_SMART.formatted(
                        smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                        smart.getRelevant(), smart.getTimeBound(),
                        direction);
                plans.add(this.callResultByClone(request, prompt));
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return plans;
    }

    /**
     * @description 并行切面 + 评论, 获取多个执行计划
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private List<String> getPlansByAspectAndEvaluation(PatternRequest request, Smart smart) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        for (String direction : Prompt.MoA.DIRECTIONS) {
            futures.add(CompletableFuture.runAsync(() -> {
                PatternRequest localRequest = request.clone();
                String prompt = null == smart ? Prompt.MoA.ASPECT : Prompt.MoA.ASPECT_SMART.formatted(
                        smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                        smart.getRelevant(), smart.getTimeBound(),
                        direction);
                localRequest.getMessages().add(Message.user(prompt));
                // 生成执行计划
                String plan = this.callResultByClone(localRequest, null);
                // 多方向评价
                localRequest.getMessages().add(Message.assistant(plan));
                String evaluation = this.callResultByClone(localRequest,
                        Prompt.MoA.EVALUATION_DIRECTION.formatted(String.join("、", Prompt.MoA.DIRECTIONS)));
                // 修正
                localRequest.getMessages().add(Message.user(evaluation));
                plans.add(this.callResultByClone(localRequest, null));
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return plans;
    }

    /**
     * @description 并行切面 + 原则, 获取多个执行计划
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private List<String> getPlansByAspectAndPrinciple(PatternRequest request, Smart smart) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        for (String direction : Prompt.MoA.DIRECTIONS) {
            futures.add(CompletableFuture.runAsync(() -> {
                PatternRequest localRequest = request.clone();
                String prompt = null == smart ? Prompt.MoA.ASPECT : Prompt.MoA.ASPECT_SMART.formatted(
                        smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                        smart.getRelevant(), smart.getTimeBound(),
                        direction);
                localRequest.getMessages().add(Message.user(prompt));
                // 生成执行计划
                String plan = this.callResultByClone(localRequest, null);
                // 思考帽/SWOT评价
                localRequest.getMessages().add(Message.assistant(plan));
                List<String> evaluations = new ArrayList<>();
                for (String principle : Prompt.MoA.PRINCIPLES) {
                    evaluations.add(this.callResultByClone(localRequest,
                            Prompt.MoA.EVALUATION_PRINCIPLE.formatted(principle)));
                }
                // 修正
                for (String evaluation : evaluations) {
                    localRequest.getMessages().add(Message.user(evaluation));
                }
                plans.add(this.callResultByClone(localRequest, null));
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return plans;
    }

    /**
     * @description 获取执行方案生成模式
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String getActionMode(PatternRequest request) {
        String actionMode = patternContext.getActionMode(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(actionMode)) {
            return actionMode;
        }
        try {
            actionMode = modelService.callAnswer(request.getMessages(), Prompt.CoT.ACTION_MODE);
            if (actionMode.contains(TaskMode.PARALLEL.getValue())) {
                actionMode = TaskMode.PARALLEL.getValue();
            } else {
                actionMode = TaskMode.SERIAL.getValue();
            }
            patternContext.setActionMode(request.getUserId(), request.getSessionId(), actionMode);
            log.info("[任务模式] 执行方案生成模式, userId={}, sessionId={}, actionMode={}",
                    request.getUserId(), request.getSessionId(), actionMode);
            return actionMode;
        } catch (Exception e) {
            return TaskMode.SERIAL.getValue();
        }
    }

    /**
     * @description 方案列表执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected void executeTaskAction(PatternRequest request) {
        // 1. 构建任务执行方案列表
        TaskActions actions = this.generateTaskActions(request, 0);
        if (null == actions) {
            throw new RuntimeException("执行方案列表生成失败");
        }
        // 2. 执行
        for (String action : actions.getActions()) {
            // 【STOP】停止对话
            if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                log.warn("\n\n======================\n\n\tS T O P: 任务模式 CoT【结束】\n\n======================");
                return;
            }
            // 方案执行
            request.getMessages().add(Message.assistant(this.actionExecute(request, action)));
        }
    }

    /**
     * @description 方案块执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected void executeTaskChain(PatternRequest request) {
        // 1. 构建任务执行方案列表
        TaskChain actions = this.generateTaskChain(request, 0);
        if (null == actions) {
            throw new RuntimeException("执行方案列表生成失败");
        }
        // 2. 执行
        for (TaskChain.ActionBlock block : actions.getBlocks()) {
            if (block.getIsSerial()) {
                for (String action : block.getActions()) {
                    // 【STOP】停止对话
                    if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                        log.warn("\n\n======================\n\n\tS T O P: 任务模式 CoT【结束】\n\n======================");
                        return;
                    }
                    // 方案执行
                    request.getMessages().add(Message.assistant(this.actionExecute(request, action)));
                }
            } else {
                List<CompletableFuture<Void>> taskFutures = new ArrayList<>();
                PatternRequest localRequest = request.clone();
                List<Message> results = new LinkedList<>();
                for (String action : block.getActions()) {
                    taskFutures.add(CompletableFuture.runAsync(() -> {
                        // 【STOP】停止对话
                        if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                            log.warn("\n\n======================\n\n\tS T O P: 任务模式 CoT【结束】\n\n======================");
                            return;
                        }
                        // 方案执行
                        results.add(Message.assistant(this.actionExecute(localRequest, action)));
                    }));
                }
                // 等待所有并行任务完成
                CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join();
                request.getMessages().addAll(results);
            }
        }
    }

    /**
     * @description 方案执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String actionExecute(PatternRequest request, String action) {
        request.getMessages().add(Message.user(action));
        String result = this.callResultByClone(request, Prompt.CoT.EXECUTE_SUMMARY);
        log.info("[任务模式] 方案执行, userId={}, sessionId={}, result={}",
                request.getUserId(), request.getSessionId(), result);
        return result;
    }

    /**
     * @description 生成执行方案
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private TaskActions generateTaskActions(PatternRequest request, int retry) {
        if (retry >= 3) {
            return null;
        }
        String actions = patternContext.getActions(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(actions)) {
            actions = this.callResultByClone(request, Prompt.CoT.ACTIONS.formatted(
                    JSONSchemaUtil.generate(TaskActions.class)));
        }
        try {
            String json = ContentUtil.removeJsonMarkers(actions);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            TaskActions actionsObj = JSON.parseObject(json, TaskActions.class);
            if (null == actionsObj.getActions()) {
                throw new RuntimeException("actions is empty");
            }
            patternContext.setActions(request.getUserId(), request.getSessionId(), json);
            return actionsObj;
        } catch (Exception e) {
            // 格式错误，重试
            patternContext.clearActions(request.getUserId(), request.getSessionId());
            request.getMessages().add(Message.user(Prompt.Check.OUTPUT_FORMAT.formatted(e.getMessage())));
            return this.generateTaskActions(request, ++retry);
        }
    }

    /**
     * @description 生成执行链
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private TaskChain generateTaskChain(PatternRequest request, int retry) {
        if (retry >= 3) {
            return null;
        }
        String actions = patternContext.getActions(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(actions)) {
            actions = this.callResultByClone(request, Prompt.CoT.ACTIONS.formatted(
                    JSONSchemaUtil.generate(TaskChain.class)));
        }
        try {
            String json = ContentUtil.removeJsonMarkers(actions);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            TaskChain actionsObj = JSON.parseObject(json, TaskChain.class);
            if (null == actionsObj.getBlocks()) {
                throw new RuntimeException("actions is empty");
            }
            patternContext.setActions(request.getUserId(), request.getSessionId(), json);
            return actionsObj;
        } catch (Exception e) {
            // 格式错误，重试
            patternContext.clearActions(request.getUserId(), request.getSessionId());
            request.getMessages().add(Message.user(Prompt.Check.OUTPUT_FORMAT.formatted(e.getMessage())));
            return this.generateTaskChain(request, ++retry);
        }
    }

    /**
     * @description 观察执行结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected Boolean observer(PatternRequest request, Smart smart) {
        String prompt = null == smart ? Prompt.CoT.OBSERVE : Prompt.CoT.OBSERVE_SMART.formatted(
                smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                smart.getRelevant(), smart.getTimeBound());
        String observe = this.callResultByClone(request, prompt);
        log.info("[任务模式] 任务执行结果观察, userId={}, sessionId={}, observe={}",
                request.getUserId(), request.getSessionId(), observe);
        // 任务终止
        if (observe.contains(OutputKeyword.TERMINATED)) {
            // 清除执行计划
            patternContext.clearPlan(request.getUserId(), request.getSessionId());
            return null;
        }
        // 任务完成
        if (observe.contains(OutputKeyword.TRUE)) {
            return false;
        }
        // 任务继续
        request.getMessages().add(Message.user(observe));
        // 清除执行方案
        patternContext.clearActions(request.getUserId(), request.getSessionId());
        return true;
    }

}


