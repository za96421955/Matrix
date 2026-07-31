package com.matrix.service.service.agent.impl;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.Smart;
import com.matrix.service.service.agent.schema.TaskActions;
import com.matrix.service.service.agent.schema.TaskChain;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.LinkedList;
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

    @Override
    /** call操作 */
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
        return this.call(request, sink -> {
            log.info("[任务模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[任务模式] userId={}, 执行【结束】", request.getUserId());
        });
    }

    /**
     * @description 执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void executor(FluxSink<Response> sink, PatternRequest request) {
        if (null == sink || null == request) {
            return;
        }
        // 记录：任务模式
        patternContext.setPattern(request.getUserId(), request.getSessionId(), Constant.Pattern.TASK);

        // 1. 规划任务目标
        Smart smart = null;
        if (this.isSmart(request)) {
            smart = this.generateSmart(request.clone(), 0);
            log.info("[任务模式] 规划任务目标, userId={}, sessionId={}, smart={}",
                    request.getUserId(), request.getSessionId(), smart);
            if (null == smart) {
                // 用户 todo
                return;
            }
        }

        // 2. CoT 执行
        int count = 0;
        while (true) {
            log.info("[任务模式] 任务执行, userId={}, sessionId={}, 执行轮次: {}",
                    request.getUserId(), request.getSessionId(), ++count);
            // 【STOP】停止对话
            if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                log.warn("\n\n======================\n\n\tS T O P: 任务模式 CoT【结束】\n\n======================");
                return;
            }

            // 1.1. 粗估执行步骤
            int steps = this.getSteps(request.clone(), 0);
            log.info("[任务模式] 粗估执行步骤数, userId={}, sessionId={}, steps={}",
                    request.getUserId(), request.getSessionId(), steps);
            // 1.2. 规划
            String plan = this.getPlan(request.clone(), smart, steps);
            log.info("[任务模式] 任务规划, userId={}, sessionId={}, steps={}, plan={}",
                    request.getUserId(), request.getSessionId(), steps, plan);
            if (null == plan) {
                // 用户 todo
                return;
            }
            if (StringUtils.isBlank(plan)) {
                throw new RuntimeException("执行计划生成失败");
            }
            request.getMessages().add(Message.assistant(plan));

            // 2. 执行
            if (steps <= 20) {
                this.executeTaskAction(request);
            } else {
                this.executeTaskChain(request);
            }

            // 3. 观察
            String prompt = null == smart ? Prompt.Common.OBSERVE : Prompt.CoT.OBSERVE_SMART.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound());
            String observe = this.callResultByClone(request, prompt);
            log.info("[任务模式] 任务执行结果观察, userId={}, sessionId={}, observe={}",
                    request.getUserId(), request.getSessionId(), observe);
            // 任务终止
            if (observe.contains("TERMINATED")) {
                // 清除执行计划
                patternContext.clearPlan(request.getUserId(), request.getSessionId());
                return;
            }
            // 任务完成
            if (observe.contains("TRUE")) {
                break;
            }
            // 任务继续
            request.getMessages().add(Message.user(observe));
            // 清除执行方案
            patternContext.clearActions(request.getUserId(), request.getSessionId());
        }

        // 3. 结果总结
        this.callResultByClone(sink, request, Prompt.Common.SUMMARY);
        // 清除模式缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
    }

    /**
     * @description 判断用户任务/需求是否需要 SMART 分析
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private boolean isSmart(PatternRequest request) {
        String isSmart = patternContext.getIsSmart(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(isSmart)) {
            String result = this.callNoToolByClone(request, Prompt.Check.IS_SMART);
            patternContext.setIsSmart(request.getUserId(), request.getSessionId(), result);
            isSmart = result;
        }
        return isSmart.contains("true");
    }

    /**
     * @description 生成任务目标
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Smart generateSmart(PatternRequest request, int retry) {
        if (retry >= 3) {
            return null;
        }
        String smart = patternContext.getSmart(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(smart)) {
            smart = this.callResultByClone(request, Prompt.CoT.SMART.formatted(
                    JSONSchemaUtil.generate(Smart.class)));
            // 检查
            request.getMessages().add(Message.assistant(smart));
            String check = this.callNoToolByClone(request, Prompt.Check.GOAL);
            if (check.contains("TODO")) {
                return null;
            }
        }
        try {
            String json = this.removeCodeBlockMarkers(smart);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            Smart smartObj = JSON.parseObject(json, Smart.class);
            patternContext.setSmart(request.getUserId(), request.getSessionId(), json);
            return smartObj;
        } catch (Exception e) {
            // 格式错误，重试
            request.getMessages().add(Message.user("格式错误: " + e.getMessage()));
            return this.generateSmart(request, ++retry);
        }
    }

    /**
     * @description 粗略估计执行步骤
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private int getSteps(PatternRequest request, int retry) {
        if (retry >= 3) {
            return -1;
        }
        String sets = patternContext.getSets(request.getUserId(), request.getSessionId());
        if (StringUtils.isBlank(sets)) {
            sets = this.callNoToolByClone(request, Prompt.CoT.STEPS);
        }
        try {
            int setsInt = Integer.parseInt(sets);
            patternContext.setSets(request.getUserId(), request.getSessionId(), sets);
            return setsInt;
        } catch (Exception e) {
            request.getMessages().add(Message.user("格式错误: " + e.getMessage()));
            return this.getSteps(request, ++retry);
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
    private String getPlan(PatternRequest request, Smart smart, int steps) {
        String plan = patternContext.getPlan(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(plan)) {
            return plan;
        }
        // <= 7 步: 直接 Plan
        if (steps <= 7) {
            String prompt = null == smart ? Prompt.CoT.PLAN : Prompt.CoT.PLAN_SMART.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound());
            plan = this.callResultByClone(request, prompt);
            log.info("[任务模式] 任务规划: Plan, userId={}, sessionId={}, steps={}, plan={}",
                    request.getUserId(), request.getSessionId(), steps, plan);
            return plan;
        } else {
            // 多计划综合评估
            List<String> plans;
            // <= 20 步: 素朴切面 (MoA)
            if (steps <= 20) {
                plans = this.getPlansByAspect(request, smart);
                log.info("[任务模式] 任务规划: 素朴切面, userId={}, sessionId={}, steps={}, plans={}",
                        request.getUserId(), request.getSessionId(), steps, plans);
            }
            // > 20 步: 素朴切面 + 评论修正 (MoA)
            else {
                plans = this.getPlansByAspectAndEvaluation(request, smart);
                log.info("[任务模式] 任务规划: 素朴切面 + 思考帽/SWOT修正, userId={}, sessionId={}, steps={}, plans={}",
                        request.getUserId(), request.getSessionId(), steps, plans);
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

        // 检查
        request.getMessages().add(Message.assistant(plan));
        String check = this.callNoToolByClone(request, Prompt.Check.PLAN);
        if (check.contains("TODO")) {
            return null;
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
     * @description 方案列表执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private void executeTaskAction(PatternRequest request) {
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
            PatternRequest actionRequest = request.clone();
            actionRequest.getMessages().add(Message.user(action));
            String result = this.callResultByClone(actionRequest, Prompt.Common.EXECUTE);
            log.info("[任务模式] 任务执行, userId={}, sessionId={}, result={}",
                    request.getUserId(), request.getSessionId(), result);
            request.getMessages().add(Message.assistant(result));
        }
    }

    /**
     * @description 方案块执行
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private void executeTaskChain(PatternRequest request) {
        // 1. 构建任务执行方案列表
        TaskChain actions = this.generateTaskChain(request, 0);
        if (null == actions) {
            throw new RuntimeException("执行方案列表生成失败");
        }
        // 2. 执行
        for (TaskChain.ActionBlock block : actions.getBlocks()) {
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
                    PatternRequest actionRequest = localRequest.clone();
                    actionRequest.getMessages().add(Message.user(action));
                    String result = this.callResultByClone(actionRequest, Prompt.Common.EXECUTE);
                    log.info("[任务模式] 任务执行, userId={}, sessionId={}, result={}",
                            request.getUserId(), request.getSessionId(), result);
                    results.add(Message.assistant(result));
                }));
            }
            // 等待所有并行任务完成
            CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join();
            request.getMessages().addAll(results);
        }
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
            actions = this.callResultByClone(request, Prompt.Common.ACTIONS.formatted(
                    JSONSchemaUtil.generate(TaskActions.class)));
        }
        try {
            String json = this.removeCodeBlockMarkers(actions);
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
            request.getMessages().add(Message.user("格式错误: " + e.getMessage()));
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
            actions = this.callResultByClone(request, Prompt.Common.ACTIONS.formatted(
                    JSONSchemaUtil.generate(TaskChain.class)));
        }
        try {
            String json = this.removeCodeBlockMarkers(actions);
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
            request.getMessages().add(Message.user("格式错误: " + e.getMessage()));
            return this.generateTaskChain(request, ++retry);
        }
    }

}


