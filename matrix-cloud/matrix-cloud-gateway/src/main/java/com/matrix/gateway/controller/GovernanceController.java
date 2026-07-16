package com.matrix.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务治理控制器
 * <p>提供服务健康检查等治理功能</p>
 *
 * @author 陈晨
 */
@RestController
@Slf4j
public class GovernanceController {

    /**
     * 健康检查接口
     * <p>用于 Kubernetes 或负载均衡器进行服务存活检测</p>
     *
     * @return 检查结果
     */
    @Description("健康检查")
    @RequestMapping("/health/check")
    public Object check() {
        log.debug("[GovernanceController.check] 健康检查请求");
        return "success";
    }

}
