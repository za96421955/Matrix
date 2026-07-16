package com.matrix.service.controller;

import com.matrix.common.dto.request.ChatRequest;
import com.matrix.common.dto.response.ChatResponse;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.response.CommonResponse;
import com.matrix.service.dal.entity.SessionInfo;
import com.matrix.service.service.chat.ChatService;
import com.matrix.service.service.chat.SessionService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Chat控制器
 * SSE端点：POST /v1/chat
 */
@RestController
@RequestMapping("/v1/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private SessionService sessionService;

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> completions(@AuthenticationPrincipal UserResponse userInfo,
                                          @RequestBody ChatRequest request) {
        request.setUserId(userInfo.getUserId());
        return chatService.completions(request).map(ChatResponse::new);
    }

    @PostMapping(value = "/submit")
    public ResponseEntity<CommonResponse<Long>> submit(@AuthenticationPrincipal UserResponse userInfo,
                                                       @RequestBody ChatRequest request) {
        request.setUserId(userInfo.getUserId());
        //先创建/获取 session，得到 sessionId
        String input = (request.getMessages() != null && !request.getMessages().isEmpty())
                ? request.getMessages().getFirst().getContent()
                : "";
        SessionInfo session = sessionService.getOrCreateSession(
                userInfo.getUserId(), request.getSessionId(), input);
        Long sessionId = session.getId();
        //设置 sessionId后异步启动 completions
        request.setSessionId(sessionId);
        chatService.completions(request).subscribe();
        //返回 sessionId供前端轮询
        return ResponseEntity.ok(CommonResponse.success(sessionId));
    }

}
