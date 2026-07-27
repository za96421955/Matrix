package com.matrix.service.controller;

import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.dto.model.Response;
import com.matrix.common.dto.request.AgentRequest;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.response.CommonResponse;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.service.agent.impl.SkillPatternService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/agent")
public class AgentController {

    @Resource
    private SkillPatternService skillPatternService;
    @Resource
    private RegisterContext registerContext;

    @PostMapping(value = "/call", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    /** call操作 */
    public Flux<Response> call(@AuthenticationPrincipal UserResponse userInfo,
                               @RequestBody AgentRequest request) {
        log.info("Agent call request: {}", request);
        request.setUserId(userInfo.getUserId());
        return skillPatternService.call(request);
    }

    @GetMapping("/list")
    /** 获取List属性值 */
    public ResponseEntity<CommonResponse<List<String>>> getList(@AuthenticationPrincipal UserResponse userInfo) {
        List<String> response = registerContext.getSkills(userInfo.getUserId()).stream()
                .filter(skill -> Boolean.TRUE.equals(skill.getEnabled()))
                .map(RegisterCommand.Skill::getName)
                .toList();
        return ResponseEntity.ok(CommonResponse.success(response));
    }

}


