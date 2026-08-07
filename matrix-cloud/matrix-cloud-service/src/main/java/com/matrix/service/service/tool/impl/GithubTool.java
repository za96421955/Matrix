package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.service.service.tool.AbstractTool;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.tools.github", havingValue = "true")
public class GithubTool extends AbstractTool<GithubTool.Request> {

    @Value("${matrix.github.token}")
    private String githubToken;

    @Override
    /** 获取组件名称 */
    public String name() {
        return "github";
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "curl api.github.com, 检索开源项目信息时优先使用。searchToken: " + githubToken + "。";
    }

    @Override
    /** 获取请求参数类型 */
    public Class<Request> requestType() {
        return Request.class;
    }

    @Override
    /** 执行工具核心逻辑 */
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // 1. ClientId 检查
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        // 2. 工具执行
        try {
            return executor.executeTask(userId, request.getClientId(), request.getCommand())
                    .onErrorResume(e -> {
                        log.error("[CLI 命令执行] command={}, 异常：{}", request.getCommand(), e.getMessage(), e);
                        return Mono.just("执行异常：" + e.getMessage());
                    })
                    .flux();
        } catch (Exception e) {
            log.error("[CLI 命令执行] command={}, 异常：{}", request.getCommand(), e.getMessage(), e);
            return Flux.just("执行异常：" + e.getMessage());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("完整 curl 命令。")
        private String command;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


