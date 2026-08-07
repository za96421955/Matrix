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
        if (StringUtils.isNotBlank(primary)) {
            throw new RuntimeException(primary);
        }

        // 2. 制定目标 (hook) (add context)
        String goal = this.getGoal(request.clone());
        if (StringUtils.isNotBlank(goal)) {
            return;
        }
        request.getMessages().add(Message.assistant(goal));

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
        List<String> infos = this.informationExecute(request.clone(), informationActions);
        for (String information : infos) {
            request.getMessages().add(Message.assistant(information));
        }

        // 6. 事实核查
        String infoReview = this.informationReview(request.clone());
        if (StringUtils.isNotBlank(infoReview)) {
            throw new RuntimeException(infoReview);
        }

        // 7. 前瞻决策 (add context)
        String forwardLooking = this.getForwardLooking(request.clone());
        request.getMessages().add(Message.assistant(forwardLooking));

        // 8. 制定执行计划 (hook) (add context)
        String plan = this.getPlan(request.clone());
        if (StringUtils.isNotBlank(plan)) {
            return;
        }
        request.getMessages().add(Message.assistant(plan));

        // 9. 安全围栏检查
        List<String> fenceCheckResults = this.fenceCheck(request.clone(), fences);
        if (!CollectionUtils.isEmpty(fenceCheckResults)) {
            throw new RuntimeException(fenceCheckResults.toString());
        }

        // TODO 10. 递进生成执行方案 (多大深度 3)
        // TODO 10.1. 判断执行方案单步/多步
        // TODO 10.2. 生成执行方案 -> 10.1
        // TODO 10.3. 输出执行方案列表

        // TODO 11. 循环执行方案列表
        // TODO 11.1. 方案执行 (hook)
        // TODO 11.2. 结果审查 -> PASS/REVISE
        // TODO 11.3. 修订建议合并至下一次执行 -> 11.1.

        // TODO 12. 最后一次结果修订 (如果有)

        // 清除模式缓存
        patternContext.clear(request.getUserId(), request.getSessionId());
    }

    /**
     * @description 违规初筛
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String primary(PatternRequest request) {
        String result = this.callResultByFlag(request, PromptDeep.Fence.PRIMARY);
        if (result.contains(OutputKeyword.PASS)) {
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
        String prompt = request.getHook() ? PromptDeep.Goal.DEVELOP_HOOK : PromptDeep.Goal.DEVELOP;
        String result = this.callByResult(request, prompt);
        // 待补充检查
        if (request.getHook()) {
            request.getMessages().add(Message.assistant(result));
            String check = this.callByFlag(request, PromptDeep.Check.GOAL);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        return result;
    }

    /**
     * @description 制定安全边界
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Fences getFences(PatternRequest request, int retry) {
        if (retry >= 3) {
            throw new RuntimeException("<安全边界>生成失败, 超出最大重试次数");
        }
        String prompt = request.getHook() ? PromptDeep.Fence.DEVELOP_HOOK : PromptDeep.Fence.DEVELOP;
        String result = this.callByResult(request, prompt.formatted(JSONSchemaUtil.generate(Fences.class)));
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
            return JSONUtil.parseObject(json, Fences.class);
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
        if (retry >= 3) {
            throw new RuntimeException("<信息收集方案>生成失败, 超出最大重试次数");
        }
        String prompt = request.getHook() ? PromptDeep.Information.DEVELOP_HOOK : PromptDeep.Information.DEVELOP;
        String result = this.callByResult(request, prompt.formatted(JSONSchemaUtil.generate(Actions.class)));
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
            return JSONUtil.parseObject(json, Actions.class);
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
    private List<String> informationExecute(PatternRequest request, Actions actions) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> results = new LinkedList<>();
        for (String action : actions.getActions()) {
            futures.add(CompletableFuture.runAsync(() -> {
                String result = this.callByResult(request, PromptDeep.Information.EXECUTE.formatted(action));
                results.add(result);
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return results;
    }

    /**
     * @description 事实核查
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String informationReview(PatternRequest request) {
        String result = this.callResultByFlag(request, PromptDeep.Information.REVIEW);
        if (result.contains(OutputKeyword.PASS)) {
            return null;
        }
        return result;
    }

    /**
     * @description 前瞻决策
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String getForwardLooking(PatternRequest request) {
        return this.callResultByFlag(request, PromptDeep.Plan.FORWARD_LOOKING);
    }

    /**
     * @description 制定执行计划
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String getPlan(PatternRequest request) {
        String prompt = request.getHook() ? PromptDeep.Plan.DEVELOP_HOOK : PromptDeep.Plan.DEVELOP;
        String result = this.callByResult(request, prompt);
        // 待补充检查
        if (request.getHook()) {
            request.getMessages().add(Message.assistant(result));
            String check = this.callByFlag(request, PromptDeep.Check.PLAN);
            if (check.contains(OutputKeyword.TODO)) {
                return null;
            }
        }
        return result;
    }

    /**
     * @description 安全围栏检查
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private List<String> fenceCheck(PatternRequest request, Fences fences) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> results = new LinkedList<>();
        for (String fence : fences.getFences()) {
            futures.add(CompletableFuture.runAsync(() -> {
                String result = this.callByResult(request, fence + PromptDeep.Fence.CHECK);
                if (result.contains(OutputKeyword.PASS)) {
                    return;
                }
                results.add(result);
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return results;
    }

}


