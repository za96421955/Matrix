package com.matrix.service.service.agent.impl;

import com.matrix.common.constant.Constant;
import com.matrix.common.constant.OutputKeyword;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.util.ContentUtil;
import com.matrix.common.util.JSONSchemaUtil;
import com.matrix.common.util.JSONUtil;
import com.matrix.service.context.PatternDeepContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.PromptDeep;
import com.matrix.service.service.agent.schema.Actions;
import com.matrix.service.service.agent.schema.Fences;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @description 深度模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class DeepPatternService extends AbstractPatternService<PatternRequest> {

    @Override
    public Flux<Response> call(PatternRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }
        // 重置上下文
        this.resetContext(request);
        // 终端
        List<ClientInfo> clients = clientService.getByUserIdAndOnline(request.getUserId());
        // 工具
        request.setTools(this.buildTools());
        // 消息
        request.setMessages(this.buildMessages(request, clients, null));
        // ReAct Agent Call
        return this.call(request, sink -> {
            log.info("[深度模式] userId={}, 执行【开始】", request.getUserId());
            this.executor(sink, request);
            log.info("[深度模式] userId={}, 执行【结束】", request.getUserId());
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
        // 记录模式
        patternContext.setPattern(request.getUserId(), request.getSessionId(), Constant.Pattern.DEEP);

        // 1. 违规初筛
        String primary = this.primary(request.clone());
        if (StringUtils.isBlank(primary)) {
            throw new RuntimeException(primary);
        }

        // 2. 制定目标 (hook) (add context)
        String goal = this.getGoal(request);
        if (StringUtils.isBlank(goal)) {
            return;
        }

        // 3. 制定安全边界 (hook)
        Fences fences = this.getFences(request.clone(), 0);
        if (null == fences) {
            return;
        }

        // 4. 制定信息收集方案 (hook)
        Actions informationActions = this.getInformationActions(request.clone(), 0);
        if (null == informationActions) {
            return;
        }

        // 5. 执行信息收集 (add context)
        this.informationExecute(request, informationActions);

        // 6. 事实核查
        this.informationReview(request.clone());

        // 7. 前瞻决策 (add context)
        this.getForwardLooking(request);

        // 8. 制定执行计划 (hook) (add context)
        String plan = this.getPlan(request);
        if (StringUtils.isBlank(plan)) {
            return;
        }

        // 9. 安全围栏检查
        this.fenceCheck(request.clone(), fences);

        // 10. 递进生成执行方案 (多大深度 3)
        Actions actions = this.getTaskActions(request.clone(), plan, 0, 0);

        // 11. 循环执行方案列表 (hook)
        String result = this.taskExecute(request.clone(), actions);
        if (StringUtils.isBlank(result)) {
            return;
        }

        // 清除缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
        patternDeepContext.clear(request.getUserId(), request.getSessionId());
    }

    /**
     * @description 违规初筛
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String primary(PatternRequest request) {
        if (patternDeepContext.isPrimary(request.getUserId(), request.getSessionId())) {
            return null;
        }

        // 过程不记录上下文
        String result = this.callResultByFlag(request, PromptDeep.Fence.PRIMARY);
        if (result.contains(OutputKeyword.PASS)) {
            patternDeepContext.setPrimary(request.getUserId(), request.getSessionId());
            return null;
        }
        return result;
    }

    /**
     * @description 制定目标
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String getGoal(PatternRequest request) {
        String result = patternDeepContext.getGoal(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(result)) {
            return result;
        }

        PatternRequest localRequest = request.clone();
        String prompt = localRequest.getHook() ? PromptDeep.Goal.DEVELOP_HOOK : PromptDeep.Goal.DEVELOP;
        result = this.callByResult(localRequest, prompt);
        // 待补充检查
        if (localRequest.getHook()) {
            localRequest.getMessages().add(Message.assistant(result));
            String check = this.callByFlag(localRequest, PromptDeep.Check.GOAL);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        // add context
        request.getMessages().add(Message.assistant(result));
        patternDeepContext.setGoal(request.getUserId(), request.getSessionId(), result);
        return result;
    }

    /**
     * @description 制定安全边界
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Fences getFences(PatternRequest request, int retry) {
        String result = patternDeepContext.getFences(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(result)) {
            return JSONUtil.parseObject(result, Fences.class);
        }

        if (retry >= 3) {
            throw new RuntimeException("<安全边界>生成失败, 超出最大重试次数");
        }
        String prompt = request.getHook() ? PromptDeep.Fence.DEVELOP_HOOK : PromptDeep.Fence.DEVELOP;
        // 过程不记录上下文
        result = this.callResultByFlag(request, prompt.formatted(JSONSchemaUtil.generate(Fences.class)));
        // 待补充检查
        if (request.getHook()) {
            request.getMessages().add(Message.assistant(result));
            String check = this.callByFlag(request, PromptDeep.Check.FENCE);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        try {
            String json = ContentUtil.removeJsonMarkers(result);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            Fences fences = JSONUtil.parseObject(json, Fences.class);
            patternDeepContext.setFences(request.getUserId(), request.getSessionId(), fences.toString());
            return fences;
        } catch (Exception e) {
            // 格式错误，重试
            request.getMessages().add(Message.user(PromptDeep.Check.OUTPUT_FORMAT.formatted(e.getMessage())));
            return this.getFences(request, ++retry);
        }
    }

    /**
     * @description 制定信息收集方案
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Actions getInformationActions(PatternRequest request, int retry) {
        String result = patternDeepContext.getInfoActions(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(result)) {
            return JSONUtil.parseObject(result, Actions.class);
        }

        if (retry >= 3) {
            throw new RuntimeException("<信息收集方案>生成失败, 超出最大重试次数");
        }
        String prompt = request.getHook() ? PromptDeep.Information.DEVELOP_HOOK : PromptDeep.Information.DEVELOP;
        // 过程不记录上下文
        result = this.callResultByFlag(request, prompt.formatted(JSONSchemaUtil.generate(Actions.class)));
        // 待补充检查
        if (request.getHook()) {
            request.getMessages().add(Message.assistant(result));
            String check = this.callByFlag(request, PromptDeep.Check.INFO);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        try {
            String json = ContentUtil.removeJsonMarkers(result);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            Actions actions = JSONUtil.parseObject(json, Actions.class);
            patternDeepContext.setInfoActions(request.getUserId(), request.getSessionId(), actions.toString());
            return actions;
        } catch (Exception e) {
            // 格式错误，重试
            request.getMessages().add(Message.user(PromptDeep.Check.OUTPUT_FORMAT.formatted(e.getMessage())));
            return this.getInformationActions(request, ++retry);
        }
    }

    /**
     * @description 执行信息收集
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private void informationExecute(PatternRequest request, Actions actions) {
        if (patternDeepContext.isInfos(request.getUserId(), request.getSessionId())) {
            return;
        }
        PatternRequest localRequest = request.clone();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> results = new LinkedList<>();
        for (String action : actions.getActions()) {
            futures.add(CompletableFuture.runAsync(() -> {
                String result = this.callByResult(localRequest, PromptDeep.Information.EXECUTE.formatted(action));
                results.add(result);
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // add context
        for (String information : results) {
            request.getMessages().add(Message.assistant(information));
        }
        patternDeepContext.setInfos(request.getUserId(), request.getSessionId());
    }

    /**
     * @description 事实核查
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private void informationReview(PatternRequest request) {
        if (patternDeepContext.isInfoReview(request.getUserId(), request.getSessionId())) {
            return;
        }
        // 过程不记录上下文
        String result = this.callResultByFlag(request, PromptDeep.Information.REVIEW);
        if (result.contains(OutputKeyword.PASS)) {
            patternDeepContext.setInfoReview(request.getUserId(), request.getSessionId());
            return;
        }
        throw new RuntimeException(result);
    }

    /**
     * @description 前瞻决策
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private void getForwardLooking(PatternRequest request) {
        if (patternDeepContext.isForwardLooking(request.getUserId(), request.getSessionId())) {
            return;
        }
        PatternRequest localRequest = request.clone();
        String result = this.callByResult(localRequest, PromptDeep.Plan.FORWARD_LOOKING);
        // add context
        request.getMessages().add(Message.assistant(result));
        patternDeepContext.setForwardLooking(request.getUserId(), request.getSessionId());
    }

    /**
     * @description 制定执行计划
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String getPlan(PatternRequest request) {
        String result = patternDeepContext.getPlan(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(result)) {
            return result;
        }

        PatternRequest localRequest = request.clone();
        String prompt = localRequest.getHook() ? PromptDeep.Plan.DEVELOP_HOOK : PromptDeep.Plan.DEVELOP;
        result = this.callByResult(localRequest, prompt);
        // 待补充检查
        if (localRequest.getHook()) {
            localRequest.getMessages().add(Message.assistant(result));
            String check = this.callByFlag(localRequest, PromptDeep.Check.PLAN);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        // add context
        request.getMessages().add(Message.assistant(result));
        patternDeepContext.setPlan(request.getUserId(), request.getSessionId(), result);
        return result;
    }

    /**
     * @description 安全围栏检查
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private void fenceCheck(PatternRequest request, Fences fences) {
        if (patternDeepContext.isFenceCheck(request.getUserId(), request.getSessionId())) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> results = new LinkedList<>();
        for (String fence : fences.getFences()) {
            futures.add(CompletableFuture.runAsync(() -> {
                // 过程不记录上下文
                String result = this.callResultByFlag(request, fence + PromptDeep.Fence.CHECK);
                if (result.contains(OutputKeyword.PASS)) {
                    return;
                }
                results.add(result);
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        if (!CollectionUtils.isEmpty(results)) {
            throw new RuntimeException(results.toString());
        }
        patternDeepContext.setFenceCheck(request.getUserId(), request.getSessionId());
    }

    /**
     * @description 递进生成执行方案
     * <p>
     *      1. 判断执行方案单步/多步
     *      2. 生成执行方案
     *      3. 输出执行方案列表
     * </p>
     *
     * @author 陈晨
     */
    private Actions getTaskActions(PatternRequest request, String plan, int deep, int retry) {
        String result = patternDeepContext.getActions(request.getUserId(), request.getSessionId());
        if (StringUtils.isNotBlank(result)) {
            return JSONUtil.parseObject(result, Actions.class);
        }

        // 最大深度 3
        if (deep >= 3) {
            return Actions.builder().actions(List.of(plan)).build();
        }
        if (retry >= 3) {
            throw new RuntimeException("<执行方案>生成失败, 超出最大重试次数");
        }

        // 1. 判断执行方案单步/多步
        result = this.callByFlag(request, PromptDeep.Action.SINGLE_RUN.formatted(plan));
        if (result.contains(OutputKeyword.SINGLE)) {
            return Actions.builder().actions(List.of(plan)).build();
        }

        // 2. 生成执行方案
        String prompt = PromptDeep.Action.DEVELOP.formatted(plan, JSONSchemaUtil.generate(Actions.class));
        // 过程不记录上下文
        result = this.callResultByFlag(request, prompt);
        try {
            String json = ContentUtil.removeJsonMarkers(result);
            if (StringUtils.isBlank(json)) {
                throw new RuntimeException("json content is empty");
            }
            Actions actions = JSONUtil.parseObject(json, Actions.class);
            // 必须保持执行顺序
            Actions resultAction = Actions.builder().actions(new ArrayList<>()).build();
            for (String action : actions.getActions()) {
                // deep + 1
                Actions next = this.getTaskActions(request, action, deep + 1, 0);
                resultAction.getActions().addAll(next.getActions());
            }
            // 3. 输出执行方案列表, 最终输出时缓存
            if (deep == 0) {
                patternDeepContext.setActions(request.getUserId(), request.getSessionId(), resultAction.toString());
            }
            return resultAction;
        } catch (Exception e) {
            // 格式错误，重试
            request.getMessages().add(Message.user(PromptDeep.Check.OUTPUT_FORMAT.formatted(e.getMessage())));
            return this.getTaskActions(request, plan, deep, retry + 1);
        }
    }

    /**
     * @description 执行信息收集
     * <p>
     *      1. 方案执行 (hook)
     *      2. 结果审查 -> PASS/REVISE
     *      3. 修订建议合并至下一次执行
     *      4. 最后修订 (如果有)
     * </p>
     *
     * @author 陈晨
     */
    private String taskExecute(PatternRequest request, Actions actions) {
        PatternRequest localRequest = request.clone();
        String revise = null;
        for (String action : actions.getActions()) {
            if (patternDeepContext.isAction(request.getUserId(), request.getSessionId(), action)) {
                continue;
            }

            // 执行
            String prompt = localRequest.getHook() ? PromptDeep.Action.EXECUTE_HOOK : PromptDeep.Action.EXECUTE;
            String result = this.callByResult(localRequest, prompt.formatted(action));
            if (StringUtils.isBlank(result)) {
                return null;
            }
            localRequest.getMessages().add(Message.assistant(result));
            patternDeepContext.setAction(request.getUserId(), request.getSessionId(), action);

            // 检查
            revise = this.callByResult(localRequest, PromptDeep.Action.REVIEW);
            if (revise.contains(OutputKeyword.PASS)) {
                revise = null;
            } else {
                localRequest.getMessages().add(Message.user(revise));
            }
        }
        // 最后修订
        if (StringUtils.isNotBlank(revise)) {
            this.callByResult(localRequest, revise);
        }
        return "已完成";
    }

}


