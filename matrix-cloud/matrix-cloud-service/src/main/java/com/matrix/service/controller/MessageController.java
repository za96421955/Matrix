package com.matrix.service.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.matrix.common.response.CommonResponse;
import com.matrix.service.dal.entity.MessageInfo;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.service.service.chat.MessageService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Message 控制器
 * 路径前缀：/v1/message
 */
@RestController
@RequestMapping("/v1/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    @GetMapping("/page/{sessionId}/{pageNum}/{pageSize}")
    /** 获取Page属性值 */
    public ResponseEntity<CommonResponse<Page<MessageInfo>>> getPage(@AuthenticationPrincipal UserResponse userInfo,
                                                                     @PathVariable("sessionId") Long sessionId,
                                                                     @PathVariable("pageNum") Integer pageNum,
                                                                     @PathVariable("pageSize") Integer pageSize) {
        Page<MessageInfo> response = messageService.getPage(userInfo.getUserId(), sessionId, pageNum, pageSize);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @GetMapping("/page/chat/{sessionId}/{pageNum}/{pageSize}")
    /** 获取ChatPage属性值 */
    public ResponseEntity<CommonResponse<Page<MessageInfo>>> getChatPage(@AuthenticationPrincipal UserResponse userInfo,
                                                                         @PathVariable("sessionId") Long sessionId,
                                                                         @PathVariable("pageNum") Integer pageNum,
                                                                         @PathVariable("pageSize") Integer pageSize) {
        Page<MessageInfo> response = messageService.getChatPage(userInfo.getUserId(), sessionId, pageNum, pageSize);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @GetMapping("/{sessionId}/{messageId}")
    /** 获取ById属性值 */
    public ResponseEntity<CommonResponse<MessageInfo>> getById(@AuthenticationPrincipal UserResponse userInfo,
                                                               @PathVariable("sessionId") Long sessionId,
                                                               @PathVariable("messageId") Long messageId) {
        MessageInfo response = messageService.getById(userInfo.getUserId(), sessionId, messageId);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @DeleteMapping("/{sessionId}/{messageId}")
    /** 递归删除目录或文件 */
    public ResponseEntity<CommonResponse<Void>> delete(@AuthenticationPrincipal UserResponse userInfo,
                                                       @PathVariable("sessionId") Long sessionId,
                                                       @PathVariable("messageId") Long messageId) {
        messageService.delete(userInfo.getUserId(), sessionId, messageId);
        return ResponseEntity.ok(CommonResponse.success());
    }

}


