package com.matrix.service.service.chat.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.model.Role;
import com.matrix.service.dal.entity.MessageInfo;
import com.matrix.service.dal.entity.SessionInfo;
import com.matrix.service.dal.mapper.MessageMapper;
import com.matrix.common.dto.model.Message;
import com.matrix.common.dto.model.Response;
import com.matrix.service.service.chat.MessageService;
import com.matrix.service.service.chat.SessionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Message 服务实现类。
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private SessionService sessionService;

    @Override
    /** 获取Page属性值 */
    public Page<MessageInfo> getPage(Long userId, Long sessionId, Integer pageNum, Integer pageSize) {
        SessionInfo session = sessionService.getById(userId, sessionId);
        if (null == session) {
            return Page.of(0, 0, 0);
        }
        LambdaQueryWrapper<MessageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageInfo::getUserId, userId)
                .eq(MessageInfo::getSessionId, sessionId)
                .eq(MessageInfo::getDeleted, false)
//                .orderByDesc(MessageInfo::getCreateTime)
                .orderByDesc(MessageInfo::getId);
        return messageMapper.selectPage(
                new Page<>(pageNum, pageSize),
                wrapper);
    }

    @Override
    /** 获取ChatPage属性值 */
    public Page<MessageInfo> getChatPage(Long userId, Long sessionId, Integer pageNum, Integer pageSize) {
        SessionInfo session = sessionService.getById(userId, sessionId);
        if (null == session) {
            return Page.of(0, 0, 0);
        }
        LambdaQueryWrapper<MessageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageInfo::getUserId, userId)
                .eq(MessageInfo::getSessionId, sessionId)
                .eq(MessageInfo::getDeleted, false)
                .and(wq ->
                        wq.eq(MessageInfo::getRole, Role.USER)
                        .or()
                        .eq(MessageInfo::getRole, Role.ASSISTANT))
                .and(wq ->
                        wq.isNull(MessageInfo::getTool_calls)
                        .or()
                        .eq(MessageInfo::getTool_calls, ""))
                .orderByDesc(MessageInfo::getId);
        return messageMapper.selectPage(
                new Page<>(pageNum, pageSize),
                wrapper);
    }

    @Override
    /** 获取ChatList属性值 */
    public List<MessageInfo> getChatList(Long userId, Long sessionId) {
        SessionInfo session = sessionService.getById(userId, sessionId);
        if (null == session) {
            return List.of();
        }
        LambdaQueryWrapper<MessageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageInfo::getUserId, userId)
                .eq(MessageInfo::getSessionId, sessionId)
                .in(MessageInfo::getRole, Arrays.asList(Role.SYSTEM, Role.USER, Role.ASSISTANT))
                .eq(MessageInfo::getDeleted, false)
                .orderByAsc(MessageInfo::getId);
        return messageMapper.selectList(wrapper);
    }

    @Override
    /** 获取Last属性值 */
    public MessageInfo getLast(Long userId, Long sessionId) {
        if (null == userId || null == sessionId) {
            return null;
        }
        LambdaQueryWrapper<MessageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageInfo::getUserId, userId)
                .eq(MessageInfo::getSessionId, sessionId)
                .eq(MessageInfo::getDeleted, false)
//                .orderByDesc(MessageInfo::getCreateTime)
                .orderByDesc(MessageInfo::getId)
                .last("LIMIT 1");
        List<MessageInfo> list = messageMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(list) || null == list.getFirst()) {
            return null;
        }
        return list.getFirst();
    }

    @Override
    /** 获取ById属性值 */
    public MessageInfo getById(Long userId, Long sessionId, Long messageId) {
        if (null == userId || null == sessionId || null == messageId) {
            return null;
        }
        LambdaQueryWrapper<MessageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageInfo::getUserId, userId)
                .eq(MessageInfo::getSessionId, sessionId)
                .eq(MessageInfo::getId, messageId)
                .eq(MessageInfo::getDeleted, false);
        List<MessageInfo> list = messageMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(list) || null == list.getFirst()) {
            return null;
        }
        return list.getFirst();
    }

    @Override
    /** 获取ByToolCallId属性值 */
    public MessageInfo getByToolCallId(Long userId, Long sessionId, String toolCallId) {
        if (null == userId || null == sessionId || StringUtils.isBlank(toolCallId)) {
            return null;
        }
        LambdaQueryWrapper<MessageInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageInfo::getUserId, userId)
                .eq(MessageInfo::getSessionId, sessionId)
                .eq(MessageInfo::getTool_call_id, toolCallId)
                .eq(MessageInfo::getDeleted, false);
        List<MessageInfo> list = messageMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(list) || null == list.getFirst()) {
            return null;
        }
        return list.getFirst();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 保存数据 */
    public MessageInfo save(MessageInfo message) {
        if (null == message) {
            return null;
        }
        message.setCreateTime(new Date());
        message.setCreator(Constant.SYSTEM_USER);
        messageMapper.insert(message);
        // 更新 session updatetime
        sessionService.updateAgent(message.getUserId(), message.getSessionId(), null);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 递归删除目录或文件 */
    public void delete(Long userId, Long sessionId, Long messageId) {
        MessageInfo message = this.getById(userId, sessionId, messageId);
        if (message == null) {
            return;
        }
        MessageInfo update = new MessageInfo();
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(message.getVersionNum());
        update.setDeleted(true);
        // 设置条件
        UpdateWrapper<MessageInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", message.getId())
                .eq("session_id", sessionId)
                .eq("user_id", userId);
        int rows = messageMapper.update(update, wrapper);
        log.info("message delete, id={}, rows={}", message.getId(), rows);
    }

    @Override
    /** 转换数据类型 */
    public Message convert(MessageInfo messageInfo) {
        return Message.builder()
                .role(messageInfo.getRole())
                .content(messageInfo.getContent())
                .reasoning_content(messageInfo.getReasoning_content())
                .tool_calls(this.convertToolCall(messageInfo.getTool_calls()))
                .tool_call_id(messageInfo.getTool_call_id())
                .build();
    }

    /**
     * @description 转换工具调用
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private List<Response.ToolCall> convertToolCall(String toolCallsJson) {
        if (StringUtils.isBlank(toolCallsJson)) {
            return null;
        }
        List<Response.ToolCall> toolCalls = new ArrayList<>();
        try {
            JSONArray toolCallsArray = JSONArray.parseArray(toolCallsJson);
            toolCallsArray.forEach(toolCall -> {
                toolCalls.add(JSONObject.parseObject(toolCall.toString(), Response.ToolCall.class));
            });
        } catch (Exception ignore) {}
        if (CollectionUtils.isEmpty(toolCalls)) {
            return null;
        }
        return toolCalls;
    }

    @Override
    /** 转换数据类型 */
    public MessageInfo convert(Message message) {
        MessageInfo messageInfo = MessageInfo.builder()
                .role(message.getRole())
                .content(message.getContent())
                .reasoning_content(message.getReasoning_content())
                .tool_call_id(message.getTool_call_id())
                .build();
        if (!CollectionUtils.isEmpty(message.getTool_calls())) {
            messageInfo.setTool_calls(JSONObject.toJSONString(message.getTool_calls()));
        }
        return messageInfo;
    }

}


