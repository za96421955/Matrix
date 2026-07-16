package com.matrix.service.controller;

import com.matrix.common.response.CommonResponse;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.service.service.user.ClientService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 设备控制器
 */
@Slf4j
@RestController
@RequestMapping("/v1/client")
public class ClientController {

    @Resource
    private ClientService clientService;
    
    /**
     * 设备注册接口
     */
    @PostMapping("/register/{clientId}")
    public void register(@AuthenticationPrincipal UserResponse userInfo,
                         @PathVariable("clientId") String clientId,
                         @RequestBody RegisterCommand registerCommand) {
        log.info("client register request, userId={}, clientId={}", userInfo.getUserId(), clientId);
        clientService.register(userInfo.getUserId(), clientId, registerCommand);
    }
    
    /**
     * 心跳上报接口
     */
    @PostMapping("/heartbeat/{clientId}")
    public void heartbeat(@AuthenticationPrincipal UserResponse userInfo,
                          @PathVariable("clientId") String clientId,
                          @RequestBody RegisterCommand registerCommand) {
        log.info("client heartbeat request, userId={}, clientId={}", userInfo.getUserId(), clientId);
        clientService.heartbeat(userInfo.getUserId(), clientId, registerCommand);
    }

    /**
     * 在线检查
     */
    @PostMapping("/checkOnline/{clientId}")
    public CommonResponse<Boolean> checkOnline(@AuthenticationPrincipal UserResponse userInfo,
                                               @PathVariable("clientId") String clientId) {
        log.info("client checkOnline request, userId={}, clientId={}", userInfo.getUserId(), clientId);
        return CommonResponse.success(clientService.checkOnline(userInfo.getUserId(), clientId));
    }

}


