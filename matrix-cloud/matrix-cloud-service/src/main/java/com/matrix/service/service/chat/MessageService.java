package com.matrix.service.service.chat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.matrix.service.dal.entity.MessageInfo;
import com.matrix.common.dto.model.Message;

import java.util.List;

/**
 * Message 服务接口
 */
public interface MessageService {

    /**
     * @description 分页查询消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Page<MessageInfo> getPage(Long userId, Long sessionId, Integer pageNum, Integer pageSize);

    /**
     * @description 分页查询对话消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Page<MessageInfo> getChatPage(Long userId, Long sessionId, Integer pageNum, Integer pageSize);

    /**
     * @description 查询消息集合
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    List<MessageInfo> getChatList(Long userId, Long sessionId);

    /**
     * @description 获取会话最后一条消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    MessageInfo getLast(Long userId, Long sessionId);

    /**
     * @description ID 查询消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    MessageInfo getById(Long userId, Long sessionId, Long messageId);

    /**
     * @description 工具调用ID 查询消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    MessageInfo getByToolCallId(Long userId, Long sessionId, String toolCallId);

    /**
     * @description 保存消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    MessageInfo save(MessageInfo messageInfo);

    /**
     * @description 删除消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    void delete(Long userId, Long sessionId, Long messageId);

    /**
     * @description DAL消息 转换 模型消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Message convert(MessageInfo messageInfo);

    /**
     * @description 模型消息 转换 DAL消息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    MessageInfo convert(Message message);
    
}


