package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.service.service.agent.ModelService;
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
public class AssistantTool extends AbstractTool<AssistantTool.Request> {

    @Resource
    private ModelService modelService;

    @Override
    public String name() {
        return "assistant";
    }

    @Override
    public String description() {
        return "文件梳理或代码开发时使用，切换目录时读取目录的使用说明，或当使用说明需要更新时调用。";
    }

    @Override
    public Class<AssistantTool.Request> requestType() {
        return AssistantTool.Request.class;
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
            return this.read(request.getClientId(), request.getWorkingDirectory()).flatMapMany(currContent -> {
                // 读取
                if (request.getRead()) {
                    String content = """
                            目录: %s
                            
                            ## 使用说明
                            ```
                            %s
                            ```
                            """.formatted(request.getWorkingDirectory(), currContent);
                    return Flux.just(content);
                }

                // 获取模型
                RegisterCommand.Model model = registerContext.getModel(userId, Constant.Model.DEEPSEEK_V4_FLASH);
                // 生成更新内容
                String input = """
                        ## 使用说明
                        ```
                        %s
                        ```
                        
                        ## 更新内容
                        ```
                        %s
                        ```
                        
                        更新<使用说明>，<使用说明>为AI提供当前目录的操作参考，仅记录最核心的关键要点，杜绝冗长的描述或介绍性内容。
                        输出修改后的内容（仅输出合并结果，不要其他说明）：
                        """
                        .formatted(currContent, request.getContent());
                String newContent = modelService.call(model, input);
                // 更新
                try {
                    return this.write(request.getClientId(), request.getWorkingDirectory(), newContent).flux();
                } catch (Exception e) {
                    log.error("userId={}, request={}, 操作说明更新异常: {}",
                            userId, request, e.getMessage(), e);
                    return Flux.just(e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("userId={}, request={}, 操作说明更新异常: {}",
                    userId, request, e.getMessage(), e);
            return Flux.just(e.getMessage());
        }
    }

    /**
     * @description 读取
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Mono<String> read(String clientId, String filePath)
            throws Exception {
        JSONObject json = new JSONObject();
        json.put("filePath", filePath);
        String command = Constant.SYSTEM_COMMAND.READ_ASSISTANT + json.toJSONString();
        return executor.executeCommand(clientId, command);
    }

    /**
     * @description 写入
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private Mono<String> write(String clientId, String filePath, String content)
            throws Exception {
        JSONObject json = new JSONObject();
        json.put("filePath", filePath);
        json.put("content", content);
        String command = Constant.SYSTEM_COMMAND.WRITE_ASSISTANT + json.toJSONString();
        return executor.executeCommand(clientId, command);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("工作目录（绝对路径），读取或更新该目录的使用说明。必填。")
        private String workingDirectory;

        @Description("是否读取, 默认: false")
        private Boolean read = false;

        @Description("更新内容")
        private String content;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


