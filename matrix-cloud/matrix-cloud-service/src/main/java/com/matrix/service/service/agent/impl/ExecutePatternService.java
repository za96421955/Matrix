package com.matrix.service.service.agent.impl;

import com.alibaba.fastjson2.JSON;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.agent.schema.Smart;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @description 执行模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class ExecutePatternService extends AbstractPatternService<PatternRequest> {

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
            log.info("[执行模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[执行模式] userId={}, 执行【结束】", request.getUserId());
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
        // 1. 规划任务目标
        Smart smart = this.generateSmart(sink, request.clone(), 0);
        log.info("[执行模式] 规划任务目标, userId={}, sessionId={}, smart={}",
                request.getUserId(), request.getSessionId(), smart);
        if (null == smart) {
            return;
        }

        // 2. 粗估执行步骤数
        int steps = this.getSteps(sink, request.clone(), 0);
        log.info("[执行模式] 粗估执行步骤数, userId={}, sessionId={}, steps={}",
                request.getUserId(), request.getSessionId(), steps);
        if (steps <= 0) {
            return;
        }

        // 3. CoT
        int count = 0;
        while (true) {
            // 【STOP】停止对话
            if (!chatContext.isConversationByCache(request.getUserId(), request.getSessionId())) {
                log.warn("\n\n======================\n\n\tS T O P: 执行模式 CoT【结束】\n\n======================");
                log.warn("执行轮次: {}", count);
                log.warn("======================\n\n\tS T O P: 执行模式 CoT【结束】\n\n======================\n\n");
                return;
            }
            count++;
            PatternRequest executorRequest = request.clone();

            // 1. 规划
            String plan = this.getPlan(sink, executorRequest.clone(), smart, steps);
            log.info("[执行模式] 任务规划, userId={}, sessionId={}, steps={}, plan={}",
                    request.getUserId(), request.getSessionId(), steps, plan);

            // 2. 执行
            executorRequest.getMessages().add(Message.user(plan));
            String result = this.callResultByClone(sink, executorRequest, Prompt.CoT.EXECUTE);
            log.info("[执行模式] 任务执行, userId={}, sessionId={}, result={}",
                    request.getUserId(), request.getSessionId(), result);

            // 3. 观察
            executorRequest.getMessages().add(Message.user(plan));
            executorRequest.getMessages().add(Message.assistant(result));
            String observe = this.callResultByClone(sink, executorRequest, Prompt.CoT.OBSERVE.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound()));
            log.info("[执行模式] 任务执行结果观察, userId={}, sessionId={}, observe={}",
                    request.getUserId(), request.getSessionId(), observe);
            if (observe.contains("TERMINATED")) {
                return;
            }
            if (observe.contains("TRUE")) {
                break;
            }
            request.getMessages().add(Message.assistant(result));
            request.getMessages().add(Message.user(observe));
        }

        // 4. 结果总结
        this.callResultByClone(sink, request, Prompt.Task.SUMMARY_RESULT);
    }

    /**
     * @description 生成任务目标
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Smart generateSmart(FluxSink<Response> sink, PatternRequest request, int retry) {
        if (retry >= 3) {
            return null;
        }
        String result = this.callResultByClone(sink, request, Prompt.SMART.CONFIRM.formatted(
                JSONSchemaUtil.generate(Smart.class)));
        // 检查
        request.getMessages().add(Message.assistant(result));
        String check = this.callResultByClone(sink, request, Prompt.SMART.CONFIRM_CHECK);
        if (check.contains("TODO")) {
            return null;
        }
        try {
            String json = this.removeCodeBlockMarkers(result);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            return JSON.parseObject(json, Smart.class);
        } catch (Exception e) {
            // 格式错误，重试
            request.getMessages().add(Message.user("格式错误: " + e.getMessage()));
            return this.generateSmart(sink, request, ++retry);
        }
    }

    /**
     * @description 获取执行步骤
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private int getSteps(FluxSink<Response> sink, PatternRequest request, int retry) {
        if (retry >= 3) {
            return -1;
        }
        String result = this.callNoToolByClone(sink, request, Prompt.CoT.STEPS);
        try {
            return Integer.parseInt(result);
        } catch (Exception e) {
            request.getMessages().add(Message.user("格式错误: " + e.getMessage()));
            return this.getSteps(sink, request, ++retry);
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
    private String getPlan(FluxSink<Response> sink, PatternRequest request, Smart smart, int steps) {
        // 1 - 5 步: 直接 Plan
        if (steps <= 5) {
            String plan = this.callResultByClone(sink, request, Prompt.CoT.PLAN.formatted(
                    smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                    smart.getRelevant(), smart.getTimeBound()));
            log.info("[执行模式] 任务规划: Plan, userId={}, sessionId={}, steps={}, plan={}",
                    request.getUserId(), request.getSessionId(), steps, plan);
            return plan;
        }
        // 多计划综合评估
        List<String> plans;
        // 6 - 15 步: 素朴切面 (MoA)
        if (steps <= 15) {
            plans = this.getPlansByAspect(sink, request, smart);
            log.info("[执行模式] 任务规划: 素朴切面, userId={}, sessionId={}, steps={}, plans={}",
                    request.getUserId(), request.getSessionId(), steps, plans);
        }
        // 大于 15 步: 素朴切面 + 评论修正 (MoA)
        else {
            plans = this.getPlansByAspectAndEvaluation(sink, request, smart);
            log.info("[执行模式] 任务规划: 素朴切面 + 评论修正, userId={}, sessionId={}, steps={}, plans={}",
                    request.getUserId(), request.getSessionId(), steps, plans);
        }
        // 大于 10 步: 素朴切面 + 思考帽/SWOT修正 (MoA)
//        else {
//            plans = this.getPlansByAspectAndPrinciple(sink, request, smart);
//            log.info("[执行模式] 任务规划: 素朴切面 + 思考帽/SWOT修正, userId={}, sessionId={}, steps={}, plans={}",
//                    request.getUserId(), request.getSessionId(), steps, plans);
//        }
        // 融合
        for (int i = 0; i < plans.size(); i++) {
            request.getMessages().add(Message.assistant(
                    "#执行计划 " + ((char) ('A' + i)) + ": \n" +
                            plans.get(i)));
        }
        return this.callResultByClone(sink, request, Prompt.MoA.CONVERGE.formatted(
                smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                smart.getRelevant(), smart.getTimeBound()));
    }

    /**
     * @description 并行切面, 获取多个执行计划
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private List<String> getPlansByAspect(FluxSink<Response> sink, PatternRequest request, Smart smart) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        for (String direction : Prompt.MoA.DIRECTIONS) {
            futures.add(CompletableFuture.runAsync(() -> {
                plans.add(this.callResultByClone(sink, request, Prompt.MoA.ASPECT.formatted(
                        smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                        smart.getRelevant(), smart.getTimeBound(),
                        direction)));
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
    private List<String> getPlansByAspectAndEvaluation(FluxSink<Response> sink, PatternRequest request, Smart smart) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        for (String direction : Prompt.MoA.DIRECTIONS) {
            futures.add(CompletableFuture.runAsync(() -> {
                PatternRequest localRequest = request.clone();
                localRequest.getMessages().add(Message.user(Prompt.MoA.ASPECT.formatted(
                        smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                        smart.getRelevant(), smart.getTimeBound(),
                        direction)));
                // 生成执行计划
                String plan = this.callResultByClone(sink, localRequest, null);
                // 多方向评价
                localRequest.getMessages().add(Message.assistant(plan));
                String evaluation = this.callResultByClone(sink, localRequest,
                        Prompt.MoA.EVALUATION_DIRECTION.formatted(String.join("、", Prompt.MoA.DIRECTIONS)));
                // 修正
                localRequest.getMessages().add(Message.user(evaluation));
                plans.add(this.callResultByClone(sink, localRequest, null));
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
    private List<String> getPlansByAspectAndPrinciple(FluxSink<Response> sink, PatternRequest request, Smart smart) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        for (String direction : Prompt.MoA.DIRECTIONS) {
            futures.add(CompletableFuture.runAsync(() -> {
                PatternRequest localRequest = request.clone();
                localRequest.getMessages().add(Message.user(Prompt.MoA.ASPECT.formatted(
                        smart.getSpecific(), smart.getMeasurable(), smart.getAchievable(),
                        smart.getRelevant(), smart.getTimeBound(),
                        direction)));
                // 生成执行计划
                String plan = this.callResultByClone(sink, localRequest, null);
                // 思考帽/SWOT评价
                localRequest.getMessages().add(Message.assistant(plan));
                List<String> evaluations = new ArrayList<>();
                for (String principle : Prompt.MoA.PRINCIPLES) {
                    evaluations.add(this.callResultByClone(sink, localRequest,
                            Prompt.MoA.EVALUATION_PRINCIPLE.formatted(principle)));
                }
                // 修正
                for (String evaluation : evaluations) {
                    localRequest.getMessages().add(Message.user(evaluation));
                }
                plans.add(this.callResultByClone(sink, localRequest, null));
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return plans;
    }

}


