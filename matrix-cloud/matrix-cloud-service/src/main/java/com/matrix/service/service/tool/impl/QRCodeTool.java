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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(name = "matrix.tools.qrcode", havingValue = "true")
public class QRCodeTool extends AbstractTool<QRCodeTool.Request> {

    @Override
    /** 获取组件名称 */
    public String name() {
        return "qrcode";
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "将内容生成二维码，当需要扫一扫、扫码等功能时可以使用。微信扫码拒绝展示纯文字内容。";
    }

    @Override
    /** 获取请求参数类型 */
    public Class<Request> requestType() {
        return QRCodeTool.Request.class;
    }

    @Override
    /** 判断是否为应答模式 */
    public boolean isAnswer() {
        return true;
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
        String content = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
//        String command = "curl -s 'qrenco.de/" + content + "?t=p'";
        String command = "curl -s 'qrenco.de/" + content + "'";
        try {
            return executor.executeTask(userId, request.getClientId(), command)
                    .map(result -> "[QRCODE]\n" + result + "\n[/QRCODE]")
                    .onErrorResume(e -> {
                        log.error("[CLI 命令执行] command={}, 异常：{}", command, e.getMessage(), e);
                        return Mono.just("执行异常：" + e.getMessage());
                    })
                    .flux();
        } catch (Exception e) {
            log.error("[CLI 命令执行] command={}, 异常：{}", command, e.getMessage(), e);
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

        @Description("需要生成二维码的内容。")
        private String content;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


