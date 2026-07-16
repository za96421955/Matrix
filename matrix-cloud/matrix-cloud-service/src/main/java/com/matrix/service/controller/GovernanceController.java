package com.matrix.service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description 服务治理
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@RestController
@Slf4j
public class GovernanceController {

    @Description("健康检查")
    @RequestMapping("/health/check")
    public Object check() {
        return "success";
    }

}


