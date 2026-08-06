//package com.matrix.service.service.agent.impl;
//
//import com.matrix.common.constant.Constant;
//import com.matrix.common.constant.OutputKeyword;
//import com.matrix.common.dto.model.Response;
//import com.matrix.common.dto.request.PatternRequest;
//import com.matrix.common.enums.ErrorCode;
//import com.matrix.common.enums.TaskMode;
//import com.matrix.service.service.agent.AbstractPatternService;
//import com.matrix.service.service.agent.PatternService;
//import com.matrix.service.service.agent.Prompt;
//import com.matrix.service.service.agent.ScenarioClassifier;
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Flux;
//
///**
// * @description 默认模式
// * <p> <功能详细描述> </p>
// *
// * @author 陈晨
// */
//@Slf4j
//@Service
//public class DefaultPatternService extends AbstractPatternService<PatternRequest> {
//
//    @Resource
//    private ExecutePatternService executePatternService;
//    @Resource
//    private PlanPatternService planPatternService;
//    @Resource
//    private GoalPatternService goalPatternService;
//
//    private final ScenarioClassifier scenarioClassifier;
//
//    public DefaultPatternService(@Lazy ScenarioClassifier scenarioClassifier) {
//        this.scenarioClassifier = scenarioClassifier;
//    }
//
//    @Override
//    public Flux<Response> call(PatternRequest request) {
//        if (request == null) {
//            return Flux.just(Response.error(ErrorCode.AGENT_REQUEST_INVALID.getMessage()));
//        }
//        // 获取模式缓存
//        PatternService patternService = this.getPatternService(request);
//        if (null != patternService) {
//            return patternService.call(request);
//        }
//        // 判断是否交互式任务场景
//        String scenario = scenarioClassifier.getScenario(request);
//        if (Constant.Pattern.TASK.equals(scenario)) {
//            patternService = goalPatternService;
//        } else if (Constant.Pattern.EXECUTE.equals(scenario)) {
//            patternService = planPatternService;
//        } else {
//            patternService = executePatternService;
//        }
//        return patternService.call(request);
//    }
//
//}
//
//
