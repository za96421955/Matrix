package com.matrix.service.service.chat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.matrix.common.dto.request.ChatRequest;
import com.matrix.service.dal.entity.SessionInfo;

/**
 * Session 服务接口
 */
public interface SessionService {

    /**
     * @description 查询、创建 session
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    SessionInfo getOrCreateSession(ChatRequest request, String input);

    /**
     * @description 分页查询会话
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    Page<SessionInfo> getPage(Long userId, Integer pageNum, Integer pageSize);

    /**
     * @description 获取会话信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    SessionInfo getById(Long userId, Long sessionId);

    /**
     * @description 更新会话标题
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean updateTitle(Long userId, Long sessionId, String title);

    /**
     * @description 更新会话智能体
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean updateAgent(Long userId, Long sessionId, String agent);

    /**
     * @description 更新会话默认授权登记
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean updateAuthLevel(Long userId, Long sessionId, Integer authLevel);

    /**
     * @description 删除会话
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    void delete(Long userId, Long sessionId);

}


