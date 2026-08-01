package com.matrix.service.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.matrix.common.constant.SystemParam;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.response.CommonResponse;
import com.matrix.service.context.ChatContext;
import com.matrix.service.dal.entity.SessionInfo;
import com.matrix.service.service.chat.SessionService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Session 控制器
 * 路径前缀：/v1/session
 */
@RestController
@RequestMapping("/v1/session")
public class SessionController {

    @Resource
    private SessionService sessionService;
    @Resource
    private ChatContext chatContext;

    @GetMapping("/page/{pageNum}/{pageSize}")
    /** 获取Page属性值 */
    public ResponseEntity<CommonResponse<Page<SessionInfo>>> getPage(@AuthenticationPrincipal UserResponse userInfo,
                                                                     @PathVariable("pageNum") Integer pageNum,
                                                                     @PathVariable("pageSize") Integer pageSize) {
        // TODO 固定查询所有
        pageNum = SystemParam.DEFAULT_PAGE_NUM;
        pageSize = SystemParam.DEFAULT_PAGE_SIZE;
        Page<SessionInfo> response = sessionService.getPage(userInfo.getUserId(), pageNum, pageSize);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @GetMapping("/{sessionId}")
    /** 获取ById属性值 */
    public ResponseEntity<CommonResponse<SessionInfo>> getById(@AuthenticationPrincipal UserResponse userInfo,
                                                               @PathVariable("sessionId") Long sessionId) {
        SessionInfo response = sessionService.getById(userInfo.getUserId(), sessionId);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @GetMapping(value = "/isCompletions/{sessionId}")
    /** 判断是否为Conversation */
    public ResponseEntity<CommonResponse<Boolean>> isConversation(@AuthenticationPrincipal UserResponse userInfo,
                                                                  @PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(CommonResponse.success(
                chatContext.isConversation(userInfo.getUserId(), sessionId)));
    }

    @GetMapping(value = "/stop/{sessionId}")
    /** stopConversation操作 */
    public ResponseEntity<CommonResponse<Void>> stopConversation(@AuthenticationPrincipal UserResponse userInfo,
                                                                 @PathVariable("sessionId") Long sessionId) {
        chatContext.stopConversation(userInfo.getUserId(), sessionId);
        return ResponseEntity.ok(CommonResponse.success());
    }

    @PutMapping("/updateTitle/{sessionId}")
    /** updateTitle操作 */
    public ResponseEntity<CommonResponse<Void>> updateTitle(@AuthenticationPrincipal UserResponse userInfo,
                                                            @PathVariable("sessionId") Long sessionId,
                                                            @RequestBody String title) {
        sessionService.updateTitle(userInfo.getUserId(), sessionId, title);
        return ResponseEntity.ok(CommonResponse.success());
    }

    @PutMapping("/updateAgent/{sessionId}")
    /** updateAuthLevel操作 */
    public ResponseEntity<CommonResponse<Void>> updateAuthLevel(@AuthenticationPrincipal UserResponse userInfo,
                                                                @PathVariable("sessionId") Long sessionId,
                                                                @RequestBody String agent) {
        sessionService.updateAgent(userInfo.getUserId(), sessionId, agent);
        return ResponseEntity.ok(CommonResponse.success());
    }

    @PutMapping("/updateAuthLevel/{sessionId}")
    /** updateAuthLevel操作 */
    public ResponseEntity<CommonResponse<Void>> updateAuthLevel(@AuthenticationPrincipal UserResponse userInfo,
                                                                @PathVariable("sessionId") Long sessionId,
                                                                @RequestBody Integer authLevel) {
        sessionService.updateAuthLevel(userInfo.getUserId(), sessionId, authLevel);
        return ResponseEntity.ok(CommonResponse.success());
    }

    @DeleteMapping("/{sessionId}")
    /** 递归删除目录或文件 */
    public ResponseEntity<CommonResponse<Void>> delete(@AuthenticationPrincipal UserResponse userInfo,
                                                       @PathVariable("sessionId") Long sessionId) {
        sessionService.delete(userInfo.getUserId(), sessionId);
        return ResponseEntity.ok(CommonResponse.success());
    }

}


