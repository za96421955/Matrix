package com.matrix.service.service.agent.impl;

import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.AgentRequest;
import com.matrix.common.enums.ErrorCode;
import com.matrix.service.service.agent.AbstractPatternService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @description 智能体模式
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class SkillPatternService extends AbstractPatternService<AgentRequest> {

    @Override
    public Flux<Response> call(AgentRequest request) {
        if (request == null) {
            return Flux.just(Response.error(ErrorCode.SKILL_REQUEST_INVALID.getMessage()));
        }
        // 查询 skill
        RegisterCommand.Skill skill = serviceContext.getSkill(request.getUserId(), request.getAgent());
        if (skill == null) {
            return Flux.just(Response.error(ErrorCode.SKILL_NOT_FOUND.getMessage()));
        }
        if (skill.disabled()) {
            return Flux.just(Response.error(ErrorCode.SKILL_DISABLED.getMessage()));
        }
        // 工具
        request.setTools(this.buildTools());
        // 消息
        request.setMessages(this.buildMessages(request.getUserId(), request.getSessionId(),
                skill.getPrompt(), request.getMessages(), null));
        // ReAct Agent Call
        return this.call(request, true);
    }

}


