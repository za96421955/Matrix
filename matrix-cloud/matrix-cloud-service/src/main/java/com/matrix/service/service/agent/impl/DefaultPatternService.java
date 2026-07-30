package com.matrix.service.service.agent.impl;

import com.matrix.common.constant.Constant;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.PatternService;
import com.matrix.service.service.agent.Prompt;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @description 默认模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class DefaultPatternService extends AbstractPatternService<PatternRequest> {

    @Resource
    private PlanPatternService planPatternService;
    @Resource
    private ExecutePatternService executePatternService;
    @Resource
    private TaskPatternService taskPatternService;

    /**
     * @description 获取模式服务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public PatternService getPatternService(String pattern) {
        log.info("[默认模式] 获取模式, pattern={}", pattern);
        if (StringUtils.isBlank(pattern)) {
            pattern = Constant.Pattern.EXECUTE;
        }
        PatternService patternService;
        switch (pattern) {
            case Constant.Pattern.PLAN -> patternService = planPatternService;
            case Constant.Pattern.EXECUTE -> patternService = executePatternService;
            case Constant.Pattern.TASK -> patternService = taskPatternService;
            default -> patternService = this;
        }
        return patternService;
    }

    @Override
    public Flux<Response> call(PatternRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }

        // 获取模式缓存
        AtomicReference<PatternService> patternService = new AtomicReference<>(this.getPatternService(request));
        if (null != patternService.get()) {
            return patternService.get().call(request);
        }
        // 识别模式
        this.call(request, sink -> {
            int retry = 0;
            while (++retry <= 3) {
                String pattern = this.callNoToolByClone(sink, request, Prompt.Common.AUTO_PATTERN);
                log.info("[默认模式] 识别模式, userId={}, sessionId={}, pattern={}",
                        request.getUserId(), request.getSessionId(), pattern);
                patternService.set(this.getPatternService(pattern));
                if (null != patternService.get()) {
                    break;
                }
            }
        }).blockLast();
        if (null == patternService.get()) {
            log.error("[默认模式] 模式识别失败, userId={}, sessionId={}",
                    request.getUserId(), request.getSessionId());
            return Flux.just(Response.error(ErrorCode.SYSTEM_ERROR.getMessage()));
        }
        // 调用模式
        return patternService.get().call(request);
    }

    /**
     * @description 获取模式服务 (缓存)
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private PatternService getPatternService(PatternRequest request) {
        // 获取模式缓存
        String pattern = patternContext.getPattern(request.getUserId(), request.getSessionId());
        // 判断任务模式缓存是否重置
        if (Constant.Pattern.TASK.equals(pattern)) {
            AtomicReference<PatternService> patternService = new AtomicReference<>();
            String smart = patternContext.getSmart(request.getUserId(), request.getSessionId());
            String plan = patternContext.getPlan(request.getUserId(), request.getSessionId());
            this.call(request, sink -> {
                String reset = this.callNoToolByClone(sink, request, Prompt.Check.RESET.formatted(smart, plan));
                if (reset.contains("SMART")) {
                    patternContext.clear(request.getUserId(), request.getSessionId());
                    return;
                }
                if (reset.contains("PLAN")) {
                    patternContext.clearPlan(request.getUserId(), request.getSessionId());
                }
                log.info("[默认模式] 获取任务模式缓存, userId={}, sessionId={}",
                        request.getUserId(), request.getSessionId());
                patternService.set(taskPatternService);
            }).blockLast();
            return patternService.get();
        }
        return null;
    }

}
