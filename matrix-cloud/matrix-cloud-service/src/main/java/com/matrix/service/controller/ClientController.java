package com.matrix.service.controller;

import com.matrix.common.constant.ClientStatus;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.response.CommonResponse;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.service.user.ClientService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
     * @description 查询设备列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     * @date 2026/7/17 14:36
     */
    @GetMapping("/list")
    public ResponseEntity<CommonResponse<List<String>>> getList(@AuthenticationPrincipal UserResponse userInfo) {
        return ResponseEntity.ok(CommonResponse.success(clientService.getByUserId(userInfo.getUserId()).stream()
                .filter(clientInfo -> clientInfo != null && ClientStatus.ONLINE.equalsIgnoreCase(clientInfo.getStatus()))
                .map(ClientInfo::getOsInfo)
                .collect(Collectors.toList())));
    }
    
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


