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
 * @description 执行模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class AutoPatternService extends AbstractPatternService<PatternRequest> {

    @Resource
    private ChatPatternService chatPatternService;
    @Resource
    private SkillPatternService skillPatternService;
    @Resource
    private PlanPatternService planPatternService;
    @Resource
    private ExecutePatternService executePatternService;
    @Resource
    private TaskChainPatternService taskChainPatternService;
    @Resource
    private TaskGraphPatternService taskGraphPatternService;
    @Resource
    private CodingPatternService codingPatternService;
    @Resource
    private InformationPatternService informationPatternService;

    /**
     * @description 获取模式服务
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public PatternService getPatternService(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            pattern = Constant.Pattern.CHAT;
        }
        PatternService patternService;
        switch (pattern) {
            case Constant.Pattern.AUTO -> patternService = this;
            case Constant.Pattern.AGENT -> patternService = skillPatternService;
            case Constant.Pattern.PLAN -> patternService = planPatternService;
            case Constant.Pattern.EXECUTE -> patternService = executePatternService;
            case Constant.Pattern.TASK_CHAIN -> patternService = taskChainPatternService;
            case Constant.Pattern.TASK_GRAPH -> patternService = taskGraphPatternService;
            case Constant.Pattern.CODING -> patternService = codingPatternService;
            case Constant.Pattern.INFORMATION -> patternService = informationPatternService;
            default -> patternService = chatPatternService;
        }
        return patternService;
    }

    @Override
    public Flux<Response> call(PatternRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
        }
        // 消息
        PatternRequest executorRequest = request.clone();
        executorRequest.setMessages(this.buildMessages(executorRequest, null, Prompt.Common.EXECUTE));
        // 根据意图获取 agent 模式
        AtomicReference<PatternService> patternService = new AtomicReference<>();
        this.call(executorRequest, true, sink -> {
            String pattern = this.callResultByClone(sink, executorRequest, Prompt.Common.AUTO_PATTERN);
            patternService.set(this.getPatternService(pattern));
        });
        // 调用模式
        return patternService.get().call(request);
    }

}
