package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Command;
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
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class CLIExecutorTool extends AbstractTool<CLIExecutorTool.Request> {

    @Override
    /** 获取组件名称 */
    public String name() {
        return Constant.CLI_TOOL_NAME;
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "CLI 命令执行器。";
    }

    @Override
    /** 获取请求参数类型 */
    public Class<Request> requestType() {
        return CLIExecutorTool.Request.class;
    }

    @Override
    /** 执行工具核心逻辑 */
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        // 1. ClientId 检查
        String checkResult = this.checkClient(userId, request.getClientId());
        if (StringUtils.isNotBlank(checkResult)) {
            return Flux.just("执行失败: " + checkResult);
        }

        // 2. 同步构建命令（buildFinalCommand 应该是纯同步操作）
        final String finalCommand;
        try {
            finalCommand = this.buildFinalCommand(request);
        } catch (Exception e) {
            return Flux.just("构建命令失败: " + e.getMessage());
        }

        // 3. 用户授权
        String command = finalCommand.split(" ")[0].toLowerCase();
        String reject = authService.commandAuth(userId, sessionId,
                Command.Type.BASH, command, finalCommand);
        if (StringUtils.isNotBlank(reject)) {
            return Flux.just(reject);
        }

        // 4. 工具执行
        try {
            JSONObject json = new JSONObject();
            json.put("dir", request.getWorkingDirectory());
            json.put("command", finalCommand);
            return executor.executeTask(userId, request.getClientId(), json.toJSONString())
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

    /**
     * 构建指令字符串，支持写文件模式（base64编码）和普通命令模式（&&串联）。
     */
    private String buildFinalCommand(Request request) {
        String actionPart;
        if (StringUtils.isNotBlank(request.getFilePath())
                && StringUtils.isNotBlank(request.getFileContent())) {
            // 写文件模式：用 base64 将内容安全写入文件
            String safeFilePath = shellEscape(request.getFilePath());
            String base64Content = Base64.getEncoder()
                    .encodeToString(request.getFileContent().getBytes(StandardCharsets.UTF_8));
            actionPart = "echo " + base64Content + " | base64 -d > " + safeFilePath;
        } else if (!CollectionUtils.isEmpty(request.getCommands())) {
            // 普通命令模式：将多条命令用 && 串联
            actionPart = String.join(" && ", request.getCommands());
        } else {
            throw new IllegalArgumentException("必须提供 filePath/fileContent 或 commands 之一");
        }
        return actionPart;
    }

    /**
     * 使用单引号包裹实现 shell 安全转义，防止空格、特殊字符破坏命令结构。
     * 所有字符在单引号内均失去特殊含义，仅单引号自身需特殊处理。
     */
    private String shellEscape(String value) {
        if (value == null) {
            return "''";
        }
        // 单引号包裹：所有字符在单引号之间保持字面含义
        // 嵌入的单引号通过结束单引号+转义引号+恢复单引号的方式处理
        return "'" + value.replace("'", "'\\''") + "'";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("工作目录（绝对路径），所有命令都在此目录下执行。必填。")
        private String workingDirectory;

        @Description("要执行的普通CLI命令，一行一个完整命令。系统会 cd 至 workingDirectory 目录下执行CLI。")
        private List<String> commands;

        @Description("要写入文件与 workingDirectory 的相对路径，仅当需要写文件时提供。")
        private String filePath;

        @Description("要写入文件的完整内容。仅当 filePath 不为空时使用。")
        private String fileContent;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}
