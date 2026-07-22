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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WeatherTool extends AbstractTool<WeatherTool.Request> {

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询天气、温度、风速信息。";
    }

    @Override
    public Class<Request> requestType() {
        return WeatherTool.Request.class;
    }

    @Override
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // 1. ClientId 检查
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        // 2. 工具执行
        String command = "curl 'wttr.in/" +
                request.getLocation() +
                "?lang=zh" +
                (request.getRealTime() ? "&format=4" : "") +
                "'";
        try {
            return executor.executeTask(userId, request.getClientId(), command)
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

        @Description("位置。示例：城市名（中文、英文、拼音）：上海、Paris; 机场 IATA 代码：PEK; 邮政编码（部分国家）：10001; 坐标：32.06,118.79; IP 地址。")
        private String location;

        @Description("实时或未来3天天气，默认：true。")
        private Boolean realTime;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


