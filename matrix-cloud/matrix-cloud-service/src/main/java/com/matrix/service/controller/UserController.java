package com.matrix.service.controller;

import com.matrix.common.constant.SecurityHeader;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.response.CommonResponse;
import com.matrix.service.service.agent.ModelService;
import com.matrix.service.service.user.UserService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

/**
 * 用户登录控制器
 */
@RestController
@RequestMapping("/v1/user")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private ModelService modelService;

    /**
     * @description API Key 检查
     * <p> /v1/user/checkApiKey </p>
     *
     * @author 陈晨
     */
    @RequestMapping("/checkApiKey")
    public ResponseEntity<CommonResponse<Boolean>> checkApiKey(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(SecurityHeader.AUTHORIZATION_HEADER);
        String apiKey = userService.extractApiKey(authHeader);
        return ResponseEntity.ok(CommonResponse.success(modelService.checkApiKey(apiKey)));
    }

    /**
     * @description 获取用户默认授权登记
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    @GetMapping("/getAuthLevel")
    public ResponseEntity<CommonResponse<Integer>> getAuthLevel(
            @AuthenticationPrincipal UserResponse userInfo) {
        return ResponseEntity.ok(CommonResponse.success(userInfo.getAuthLevel()));
    }

    /**
     * 删除 API Key
     * @return 删除结果
     */
    @DeleteMapping("/ak")
    public ResponseEntity<CommonResponse<Boolean>> deleteApiKey(
            @AuthenticationPrincipal UserResponse userInfo) {
        boolean result = userService.deleteApiKey(userInfo.getApiKey());
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    /**
     * 启用 API Key
     * @return 启用结果
     */
    @PostMapping("/ak/enable")
    public ResponseEntity<CommonResponse<Boolean>> enableApiKey(
            @AuthenticationPrincipal UserResponse userInfo) {
        boolean result = userService.enableApiKey(userInfo.getApiKey());
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    /**
     * 禁用 API Key
     * @return 禁用结果
     */
    @PostMapping("/ak/disable")
    public ResponseEntity<CommonResponse<Boolean>> disableApiKey(
            @AuthenticationPrincipal UserResponse userInfo) {
        boolean result = userService.disableApiKey(userInfo.getApiKey());
        return ResponseEntity.ok(CommonResponse.success(result));
    }

}


