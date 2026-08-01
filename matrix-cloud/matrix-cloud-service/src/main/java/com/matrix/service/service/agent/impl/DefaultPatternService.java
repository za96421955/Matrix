package com.matrix.service.service.agent.impl;

import com.matrix.common.constant.Constant;
import com.matrix.common.constant.OutputKeyword;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.PatternRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.enums.TaskMode;
import com.matrix.service.service.agent.AbstractPatternService;
import com.matrix.service.service.agent.PatternService;
import com.matrix.service.service.agent.Prompt;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
    private ExecutePatternService executePatternService;
    @Resource
    private TaskPatternService taskPatternService;

    /**
     * @description 获取模式服务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private PatternService getPatternService(String pattern) {
        log.info("[默认模式] 获取模式, pattern={}", pattern);
        if (Constant.Pattern.TASK.equals(pattern)) {
            return taskPatternService;
        }
        return executePatternService;
    }

    @Override
    public Flux<Response> call(PatternRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }

        // 获取模式缓存
        PatternService patternService = this.getPatternService(request);
        if (null != patternService) {
            return patternService.call(request);
        }
        // 识别模式
        int retry = 0;
        while (++retry <= 3) {
            String pattern = this.callNoToolByClone(request, Prompt.Common.AUTO_PATTERN);
            log.info("[默认模式] 识别模式, userId={}, sessionId={}, pattern={}",
                    request.getUserId(), request.getSessionId(), pattern);
            patternService = this.getPatternService(pattern);
            if (null != patternService) {
                break;
            }
        }
        if (null == patternService) {
            log.error("[默认模式] 模式识别失败, userId={}, sessionId={}",
                    request.getUserId(), request.getSessionId());
            return Flux.just(Response.error(ErrorCode.SYSTEM_ERROR.getMessage()));
        }
        // 调用模式
        return patternService.call(request);
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
            String smart = patternContext.getSmart(request.getUserId(), request.getSessionId());
            String plan = patternContext.getPlan(request.getUserId(), request.getSessionId());
            String reset = this.callNoToolByClone(request, Prompt.Check.RESET.formatted(smart, plan));
            if (reset.contains(OutputKeyword.SMART)) {
                patternContext.clear(request.getUserId(), request.getSessionId());
                return taskPatternService;
            }
            if (reset.contains(TaskMode.PLAN.getValue())) {
                patternContext.clearPlan(request.getUserId(), request.getSessionId());
            }
            log.info("[默认模式] 获取任务模式缓存, userId={}, sessionId={}",
                    request.getUserId(), request.getSessionId());
            return taskPatternService;
        }
        return null;
    }

}


