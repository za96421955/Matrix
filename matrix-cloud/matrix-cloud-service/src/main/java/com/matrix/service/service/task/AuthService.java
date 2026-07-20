package com.matrix.service.service.task;

import com.matrix.common.constant.Constant;
import com.matrix.common.constant.RiskLevel;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.dal.entity.SessionInfo;
import com.matrix.service.service.chat.SessionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 授权服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class AuthService {

    @Resource
    private Executor executor;
    @Resource
    private RegisterContext registerContext;
    @Resource
    private SessionService sessionService;

    /**
     * @description 指令授权
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String commandAuth(Long userId, Long sessionId, String type, String command, String content) {
        int riskLevel = registerContext.getCommandLevel(userId, type, command);
        // 获取会话授权等级
        SessionInfo sessionInfo = sessionService.getById(userId, sessionId);
        int authLevel = null != sessionInfo && null != sessionInfo.getAuthLevel() ?
                sessionInfo.getAuthLevel() : RiskLevel.NONE;
        // 指令风险 < 0, 直接拒绝
        if (riskLevel < 0 || authLevel < 0) {
            return "系统拒绝执行";
        }
        // 指令风险 <= 默认授权, 直接授权
        if (riskLevel <= authLevel) {
            return null;
        }
        return this.userAuth(userId, content);
    }

    /**
     * @description 用户授权
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String userAuth(Long userId, String content) {
        try {
            String result = executor.executeAuth(userId, content).block();
            if (Constant.PASS.equalsIgnoreCase(result)) {
                return null;
            }
            return "用户拒绝执行: " + result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return "授权异常: " + e.getMessage();
        }
    }

}


