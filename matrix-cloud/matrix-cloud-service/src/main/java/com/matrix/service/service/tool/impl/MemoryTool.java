package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.service.agent.ModelService;
import com.matrix.service.service.agent.Prompt;
import com.matrix.service.service.tool.AbstractTool;
import jakarta.annotation.Resource;
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
public class MemoryTool extends AbstractTool<MemoryTool.Request> {

    @Resource
    private ModelService modelService;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public String description() {
        return "在任务执行过程中遇到问题并解决时，精简记录问题和解决方案。记忆格式：短期、长期、谏言";
    }

    @Override
    public Class<MemoryTool.Request> requestType() {
        return MemoryTool.Request.class;
    }

    @Override
    public String systemPrompt(Long userId, Long sessionId, String clientId) {
        try {
            return this.readMemory(clientId).block();
        } catch (Exception ignore) {
            return "";
        }
    }

    @Override
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // ClientId 检查
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }
        // 工具执行
        try {
            return this.readMemory(request.getClientId()).flatMapMany(currMemory -> {
                // 读取记忆
                if (request.getRead()) {
                    return Flux.just(currMemory);
                }

                // 生成记忆
                RegisterCommand.Model model = registerContext.getModel(userId, Constant.Model.DEEPSEEK_V4_FLASH);
                String input = Prompt.MEMORY_MANAGER.formatted(currMemory, request.getRequire());
                String newMemory = modelService.call(model, input);
                // 更新记忆
                try {
                    return this.writeMemory(request.getClientId(), newMemory).flux();
                } catch (Exception e) {
                    log.error("userId={}, request={}, 记忆更新异常: {}",
                            userId, request, e.getMessage(), e);
                    return Flux.just(e.getMessage());
                }
//                return Flux.just("记忆修改完成");
            });
        } catch (Exception e) {
            log.error("userId={}, request={}, 记忆操作异常: {}",
                    userId, request, e.getMessage(), e);
            return Flux.just(e.getMessage());
        }
    }

    /**
     * @description 读取记忆
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Mono<String> readMemory(String clientId)
            throws Exception {
        return executor.executeCommand(clientId, Constant.SYSTEM_COMMAND.READ_MEMORY);
    }

    /**
     * @description 写入记忆
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Mono<String> writeMemory(String clientId, String memory)
            throws Exception {
        String command = Constant.SYSTEM_COMMAND.WRITE_MEMORY + memory;
        return executor.executeCommand(clientId, command);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("是否读取, 默认: false")
        private Boolean read = false;

        @Description("修改要求")
        private String require;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


