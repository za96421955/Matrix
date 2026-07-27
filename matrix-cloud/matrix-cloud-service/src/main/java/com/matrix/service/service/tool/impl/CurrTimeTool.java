package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.service.service.tool.AbstractTool;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class CurrTimeTool extends AbstractTool<CurrTimeTool.Request> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    /** 获取组件名称 */
    public String name() {
        return "time";
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "获取系统当前时间、星期几、今年的第几天、是否闰年。";
    }

    @Override
    public Class<CurrTimeTool.Request> requestType() {
        return CurrTimeTool.Request.class;
    }

    @Override
    /** 获取系统提示信息 */
    public String systemPrompt(Long userId, Long sessionId, String clientId) {
        return this.getCurrentTime();
    }

    @Override
    /** 执行工具核心逻辑 */
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        return Flux.just(this.getCurrentTime());
    }

    private String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%s, %s, 第 %d 天, %s",
                now.format(FORMATTER),
                now.getDayOfWeek(),
                now.getDayOfYear(),
                now.toLocalDate().isLeapYear() ? "闰年" : "平年");
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class Request {
        // 无参数
        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


