package com.matrix.service.service.tool.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Command;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.AgentRequest;
import com.matrix.service.service.agent.PatternService;
import com.matrix.service.service.agent.impl.SkillPatternService;
import com.matrix.service.service.tool.AbstractTool;
import jakarta.annotation.Resource;
import jdk.jfr.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Component
public class SkillTool extends AbstractTool<SkillTool.Request> {

    @Resource
    private SkillPatternService skillPatternService;

    @Override
    /** 获取组件名称 */
    public String name() {
        return "skill";
    }

    @Override
    /** 获取组件描述 */
    public String description() {
        return "可用 skill 调度工具，文件需要提供绝对路径。";
    }

    @Override
    /** 获取请求参数类型 */
    public Class<Request> requestType() {
        return Request.class;
    }

    @Override
    /** 执行工具核心逻辑 */
    public Flux<String> executePass(Long userId, Long sessionId, String toolCallId, Request request) {
        log.info("[技能执行开始] userId={}, sessionId={}, request={}",
                userId, sessionId, request);
        // 调用 Skill
        RegisterCommand.Skill skill = registerContext.getSkill(userId, request.getSkillName());
        if (null == skill) {
            return Flux.just("skill " + request.getSkillName() + " not exist");
        }
        // 用户授权
        String reject = authService.commandAuth(userId, sessionId,
                Command.Type.SKILL, request.getSkillName(), request.toString());
        if (StringUtils.isNotBlank(reject)) {
            return Flux.just(reject);
        }
        return this.call(skillPatternService, AgentRequest.builder()
                .userId(userId)
                .sessionId(sessionId)
                .agent(request.getSkillName())
                .messages(List.of(Message.user(request.getInput())))
                .model(Constant.Model.DEEPSEEK_V4_FLASH)
                .build());
    }

    /**
     * @description 调用 skill
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private <T extends AgentRequest> Flux<String> call(PatternService<T, Response> service, T request) {
        return service.call(request).map(JSON::toJSONString);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @Description(Constant.CLIENT_ID_DESCRIPTION)
        private String clientId;

        @Description("skill名称。")
        private String skillName;

        @Description("输入信息。")
        private String input;

        @Override
        public String toString() {
            return JSONObject.toJSONString(this);
        }
    }

}


