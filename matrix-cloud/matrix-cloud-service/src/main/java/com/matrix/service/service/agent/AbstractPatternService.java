package com.matrix.service.service.agent;

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
import com.matrix.common.util.DateUtil;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.common.util.JSONUtil;
import com.matrix.service.context.ChatContext;
import com.matrix.service.context.PatternContext;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.context.ToolContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.dal.entity.MessageInfo;
import com.matrix.service.service.agent.schema.Actions;
import com.matrix.service.service.agent.schema.Smart;
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
                    this.call(sink, request, false);
                }
                sink.complete();
            } catch (Exception e) {
                // 记录 Error 消息
                this.saveErrorMessage(request.getUserId(), request.getSessionId(), e.getMessage());
                sink.error(e);
            } finally {
                // 记录耗时
                long total = patternContext.getTotalConsume(request.getUserId(), request.getSessionId());
                long curr = patternContext.getCurrConsume(request.getUserId(), request.getSessionId());
                String consume = "耗时: " + DateUtil.formatTime(total + curr);
                this.saveMessage(request.getUserId(), request.getSessionId(), Role.FLAG, Response.content(consume), true);
                // 清除 consume
                String pattern = patternContext.getPattern(request.getUserId(), request.getSessionId());
                if (StringUtils.isBlank(pattern)) {
                    patternContext.clearConsume(request.getUserId(), request.getSessionId());
                }
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * @description ReAct Agent Call
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected Response call(FluxSink<Response> sink, PatternRequest request, boolean flag) {
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
            this.saveMessage(userId, sessionId, Role.ASSISTANT, response, flag);

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

    /**
     * @description 克隆调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String callByClone(FluxSink<Response> sink, PatternRequest request, String prompt, boolean flag) {
        PatternRequest localRequest = request.clone();
        if (StringUtils.isNotBlank(prompt)) {
            localRequest.getMessages().add(Message.user(prompt));
        }
        try {
            Response response = this.call(sink, localRequest, flag);
            Message message = null != response ? response.getMessage() : null;
            if (null != message) {
                return StringUtils.isNotBlank(message.getContent())
                        ? message.getContent()
                        : message.getReasoning_content();
            }
            throw new RuntimeException("response is null");
        } catch (Exception e) {
            // 记录 Error 消息
            this.saveErrorMessage(request.getUserId(), request.getSessionId(), e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @description 结果类调用（clone）
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String callByResult(PatternRequest request, String prompt) {
        return this.callByClone(null, request, prompt, false);
    }

    protected String callResultByFlag(PatternRequest request, String prompt) {
        return this.callByClone(null, request, prompt, true);
    }

    /**
     * @description 标记类调用（clone）
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String callByFlag(PatternRequest request, String prompt) {
        PatternRequest localRequest = request.clone();
        if (!CollectionUtils.isEmpty(localRequest.getTools())) {
            localRequest.getTools().clear();
        }
        return this.callByClone(null, localRequest, prompt, true);
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
                            JSONUtil.parseObject(toolCall.getFunction().getArguments(), tool.requestType()));
                    // 用一个容器收集最终的答案
                    AtomicReference<String> resultHolder = new AtomicReference<>("");
                    flux.doOnNext(output -> {
                            try {
                                Response response = JSONUtil.parseObject(output, Response.class);
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
            this.saveMessage(userId, sessionId, Role.TOOL, toolResult, false);
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
    protected void saveMessage(Long userId, Long sessionId, String role, Response response, boolean flag) {
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
            } else if (flag) {
                save.setRole(Role.FLAG);
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
     * @description 重置上下文
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected void resetContext(PatternRequest request) {
        // 获取模式缓存
//        String pattern = patternContext.getPattern(request.getUserId(), request.getSessionId());
//        if (StringUtils.isBlank(pattern)) {
//            return;
//        }
//        log.info("[任务模式] 获取模式缓存, userId={}, sessionId={}, pattern={}",
//                request.getUserId(), request.getSessionId(), pattern);

        // 判断模式缓存是否重置
        String smart = patternContext.getSmart(request.getUserId(), request.getSessionId());
        String plan = patternContext.getPlan(request.getUserId(), request.getSessionId());
        patternContext.setStatus(request.getUserId(), request.getSessionId(), "判断是否重置缓存");
        String reset = this.callByFlag(request, Prompt.Check.RESET.formatted(smart, plan));
        log.info("[直接回答] userId={}, sessionId={}, result={}",
                request.getUserId(), request.getSessionId(), reset);
        if (reset.contains(OutputKeyword.PASS)) {
            return;
        }
        // 重置缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
        log.info("[任务模式] 清除模式缓存, userId={}, sessionId={}, reset={}",
                request.getUserId(), request.getSessionId(), reset);
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
            patternContext.setStatus(request.getUserId(), request.getSessionId(), "判断执行计划生成模式");
            planMode = this.callByFlag(request, Prompt.Plan.PLAN_MODE);
            log.info("[直接回答] userId={}, sessionId={}, result={}",
                    request.getUserId(), request.getSessionId(), planMode);
            if (planMode.contains(TaskMode.REVIEW.getValue())) {
                planMode = TaskMode.REVIEW.getValue();
//            } else if (planMode.contains(TaskMode.EVALUATION.getValue())) {
//                planMode = TaskMode.EVALUATION.getValue();
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
    protected String getPlan(PatternRequest request, Smart smart, String planMode) {
        String plan = patternContext.getPlan(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(plan)) {
            return plan;
        }

        // 生成执行计划
        String prompt = null == smart
                ? Prompt.Plan.PLAN.formatted(request.getHook() ? Prompt.Plan.PLAN_HOOK : "")
                : Prompt.Plan.PLAN_SMART.formatted(request.getHook() ? Prompt.Plan.PLAN_HOOK : "",
                        smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                        smart.getRelevant(), smart.getTimeBound());
        patternContext.setStatus(request.getUserId(), request.getSessionId(), "执行计划-生成");
        plan = this.callByResult(request, prompt);
        log.info("[任务模式] 执行计划 - 生成, userId={}, sessionId={}, planMode={}, plan={}",
                request.getUserId(), request.getSessionId(), planMode, plan);

        // 待补充检查
        if (request.getHook()) {
            request.getMessages().add(Message.assistant(plan));
            patternContext.setStatus(request.getUserId(), request.getSessionId(), "执行计划-信息补充检查");
            String check = this.callByFlag(request, Prompt.Check.PLAN);
            log.info("[直接回答] userId={}, sessionId={}, result={}",
                    request.getUserId(), request.getSessionId(), check);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }

        // 审查修订
        if (TaskMode.REVIEW.getValue().equals(planMode)) {
            plan = this.review(request, plan);
            log.info("[任务模式] 执行计划 - 审查修订, userId={}, sessionId={}, planMode={}, plan={}",
                    request.getUserId(), request.getSessionId(), planMode, plan);
        }

        // 领域专家审查、修订
        plan = this.domainReview(request.clone(), plan);
        patternContext.setPlan(request.getUserId(), request.getSessionId(), plan);
        log.info("[任务模式] 执行计划 - 定稿, userId={}, sessionId={}, planMode={}, plan={}",
                request.getUserId(), request.getSessionId(), planMode, plan);
        return plan;
    }

    /**
     * @description 审查、修订
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String review(PatternRequest request, String plan) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> revises = new ArrayList<>();
        for (String direction : Prompt.Plan.DIRECTIONS) {
            futures.add(CompletableFuture.runAsync(() -> {
                patternContext.setStatus(request.getUserId(), request.getSessionId(), "执行计划-审查");
                String result = this.callResultByFlag(request, Prompt.Plan.DIRECTION_REVIEW.formatted(direction));
                // 计算结果
                int indexPass = result.indexOf(OutputKeyword.PASS);
                int indexRevise = result.indexOf(OutputKeyword.REVISE);
                boolean isPass = indexPass >= 0
                        && (indexRevise < 0 || indexRevise > indexPass);
                if (!isPass) {
                    revises.add(result);
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 修订
        if (!CollectionUtils.isEmpty(revises)) {
            StringBuilder revise = new StringBuilder();
            for (String r : revises) {
                revise.append(r).append("\n---\n");
            }
            patternContext.setStatus(request.getUserId(), request.getSessionId(), "执行计划-修订");
            return this.callByResult(request, Prompt.Plan.CONVERGE.formatted(revise));
        }
        return plan;
    }

    /**
     * @description 领域专家审查、修订
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String domainReview(PatternRequest request, String plan) {
        // 审查
        request.getMessages().add(Message.assistant(plan));
        patternContext.setStatus(request.getUserId(), request.getSessionId(), "执行计划-领域专家审查");
        String result = this.callResultByFlag(request, Prompt.Plan.DOMAIN_REVIEW);
        // 计算结果
        int indexPass = result.indexOf(OutputKeyword.PASS);
        int indexRevise = result.indexOf(OutputKeyword.REVISE);
        int indexTerminate = result.indexOf(OutputKeyword.TERMINATE);
        boolean isPass = indexPass >= 0
                && (indexRevise < 0 || indexRevise > indexPass)
                && (indexTerminate < 0 || indexTerminate > indexPass);
        boolean isRevise = indexRevise >= 0
                && (indexTerminate < 0 || indexTerminate > indexRevise);
        // 审查通过
        if (isPass) {
            return plan;
        }
        // 修订
        if (isRevise) {
            patternContext.setStatus(request.getUserId(), request.getSessionId(), "执行计划-领域专家修订");
            return this.callByResult(request, Prompt.Plan.REVISE.formatted(result));
        }
        // 终止执行
        throw new RuntimeException(result);
    }

    /**
     * @description 方案列表执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String executeTaskAction(PatternRequest request) {
        // 1. 构建任务执行方案列表
        Actions actions = this.generateTaskActions(request, 0);
        if (null == actions) {
            throw new RuntimeException("执行方案列表生成失败");
        }
        // 2. 执行
        for (int i = 0; i < actions.getActions().size(); i++) {
            String action = actions.getActions().get(i);
            // 【STOP】停止对话
            if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                log.warn("\n\n======================\n\n\tS T O P: 任务模式 CoT【结束】\n\n======================");
                return null;
            }
            // 方案执行
            request.getMessages().add(Message.user(action));
            String result = this.actionExecute(request.clone(), action, i + 1, actions.getActions().size());
            request.getMessages().add(Message.assistant(result));
            // 待补充检查
            if (request.getHook()) {
                patternContext.setStatus(request.getUserId(), request.getSessionId(), "执行结果-信息补充检查");
                String check = this.callByFlag(request, Prompt.Check.RESULT);
                log.info("[直接回答] userId={}, sessionId={}, result={}",
                        request.getUserId(), request.getSessionId(), check);
                if (check.contains(OutputKeyword.TODO)) {
                    return null;
                }
            }
        }
        return "已完成";
    }

    /**
     * @description 方案执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String actionExecute(PatternRequest request, String action, int curr, int size) {
        String result = patternContext.getResult(request.getUserId(), request.getSessionId(), action);
        if (StringUtils.isNotBlank(result)) {
            return result;
        }
        String title = action.length() > 15 ? (action.substring(0, 15) + "...") : action;
        String status = "执行任务（%s/%s）：%s".formatted(curr, size, title);
        patternContext.setStatus(request.getUserId(), request.getSessionId(), status);
        result = this.callByResult(request, Prompt.Action.EXECUTE_SUMMARY.formatted(action));
        log.info("[任务模式] 方案执行, userId={}, sessionId={}, result={}",
                request.getUserId(), request.getSessionId(), result);
        patternContext.setResult(request.getUserId(), request.getSessionId(), action, result);
        return result;
    }

    /**
     * @description 生成执行方案
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Actions generateTaskActions(PatternRequest request, int retry) {
        if (retry >= 3) {
            return null;
        }
        String actions = patternContext.getActions(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(actions)) {
            patternContext.setStatus(request.getUserId(), request.getSessionId(), "生成执行方案列表");
            actions = this.callByResult(request, Prompt.Action.ACTIONS.formatted(
                    JSONSchemaUtil.generate(Actions.class)));
        }
        try {
            String json = ContentUtil.removeJsonMarkers(actions);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            Actions actionsObj = JSONUtil.parseObject(json, Actions.class);
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
     * @description 观察执行结果
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    protected String observe(PatternRequest request, Smart smart) {
        String prompt = null == smart ? Prompt.Observe.OBSERVE : Prompt.Observe.OBSERVE_SMART.formatted(
                smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                smart.getRelevant(), smart.getTimeBound());
        patternContext.setStatus(request.getUserId(), request.getSessionId(), "观察执行结果");
        String observe = this.callResultByFlag(request, prompt);
        log.info("[任务模式] 任务执行结果观察, userId={}, sessionId={}, observe={}",
                request.getUserId(), request.getSessionId(), observe);

        // 计算结果
        int indexPass = observe.indexOf(OutputKeyword.PASS);
        int indexContinue = observe.indexOf(OutputKeyword.CONTINUE);
        int indexTerminate = observe.indexOf(OutputKeyword.TERMINATE);
        boolean isPass = indexPass >= 0
                && (indexContinue < 0 || indexContinue > indexPass)
                && (indexTerminate < 0 || indexTerminate > indexPass);
        boolean isContinue = indexContinue >= 0
                && (indexTerminate < 0 || indexTerminate > indexContinue);
        // 任务完成
        if (isPass) {
            return null;
        }
        // 清除执行计划
        patternContext.clearPlan(request.getUserId(), request.getSessionId());
        // 任务继续
        if (isContinue) {
            return observe;
        }
        // 任务终止
        throw new RuntimeException(observe);
    }

}


