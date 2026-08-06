package com.matrix.service.service.agent;

import com.matrix.common.constant.Constant;
import com.matrix.service.service.agent.impl.ExecutePatternService;
import com.matrix.service.service.agent.impl.GoalPatternService;
import com.matrix.service.service.agent.impl.PlanPatternService;
import com.matrix.service.service.agent.impl.ReviewPatternService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @description 模式工厂
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class PatternFactory {

    @Resource
    private ExecutePatternService executePatternService;
    @Resource
    private PlanPatternService planPatternService;
    @Resource
    private ReviewPatternService reviewPatternService;
    @Resource
    private GoalPatternService goalPatternService;

    public PatternService getPatternService(String pattern) {
        if (Constant.Pattern.EXECUTE.equals(pattern)) {
            return executePatternService;
        }
        if (Constant.Pattern.PLAN.equals(pattern)) {
            return planPatternService;
        }
        if (Constant.Pattern.REVIEW.equals(pattern)) {
            return reviewPatternService;
        }
        if (Constant.Pattern.GOAL.equals(pattern)) {
            return goalPatternService;
        }
        // 模式直接执行
        return executePatternService;
    }

}


