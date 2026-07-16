package com.matrix.service.service.chat.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.matrix.common.constant.Constant;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.exception.BusinessException;
import com.matrix.service.dal.entity.SessionInfo;
import com.matrix.service.dal.entity.UserInfo;
import com.matrix.service.dal.mapper.SessionInfoMapper;
import com.matrix.service.service.chat.MessageService;
import com.matrix.service.service.chat.SessionService;
import com.matrix.service.service.user.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/**
 * Session 服务实现类（操作清单 2.1）
 * 负责 CRUD、shard_id 设置、auth_level 管理
 */
@Slf4j
@Service
public class SessionServiceImpl implements SessionService {

    @Resource
    private SessionInfoMapper sessionInfoMapper;

    private final MessageService messageService;
    @Resource
    private UserService userService;

    public SessionServiceImpl(@Lazy MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SessionInfo getOrCreateSession(Long userId, Long sessionId, String input) {
        SessionInfo sessionInfo = null;
        if (sessionId != null) {
            sessionInfo = this.getById(userId, sessionId);
        }
        if (sessionInfo == null) {
            // 查询 userInfo，获取 userAuthLevel
            UserInfo userInfo = userService.getUserInfo(userId);
            if (null == userInfo) {
                throw new BusinessException(ErrorCode.SESSION_USER_NOT_FOUND);
            }
            sessionInfo = SessionInfo.builder()
                    .userId(userId)
                    .title(StringUtils.isBlank(input)
                            ? Constant.NEW_SESSION_TITLE
                            : input.substring(0, Math.min(10, input.length())))
                    .authLevel(userInfo.getAuthLevel())
                    .createTime(new Date())
                    .updateTime(new Date())
                    .creator(Constant.SYSTEM_USER)
                    .build();
            sessionInfoMapper.insert(sessionInfo);
        }
        return sessionInfo;
    }

    @Override
    public Page<SessionInfo> getPage(Long userId, Integer pageNum, Integer pageSize) {
        if (null == userId ) {
            return Page.of(0, 0, 0);
        }
        LambdaQueryWrapper<SessionInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionInfo::getUserId, userId)
                .eq(SessionInfo::getDeleted, false)
                .orderByDesc(SessionInfo::getUpdateTime)
                .orderByDesc(SessionInfo::getId);
        Page<SessionInfo> sessionPage = sessionInfoMapper.selectPage(
                new Page<>(pageNum, pageSize),
                wrapper);
        // 设置会话最后更新时间
//        this.setLastUpdateTime(sessionPage.getRecords());
        return sessionPage;
    }

    @Override
    public SessionInfo getById(Long userId, Long sessionId) {
        if (null == userId || null == sessionId) {
            return null;
        }
        LambdaQueryWrapper<SessionInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SessionInfo::getUserId, userId)
                .eq(SessionInfo::getId, sessionId)
                .eq(SessionInfo::getDeleted, false);
        List<SessionInfo> list = sessionInfoMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(list) || null == list.getFirst()) {
            return null;
        }
        // 设置会话最后更新时间
//        this.setLastUpdateTime(list);
        return list.getFirst();
    }

//    /**
//     * @description 设置会话最后更新时间
//     * <p> <功能详细描述> </p>
//     *
//     * @author 陈晨
//     */
//    private void setLastUpdateTime(List<SessionInfo> sessions) {
//        if (CollectionUtils.isEmpty(sessions)) {
//            return;
//        }
//        for (SessionInfo session : sessions) {
//            if (null == session) {
//                continue;
//            }
//            MessageInfo message = messageService.getLast(session.getUserId(), session.getId());
//            if (null == message) {
//                continue;
//            }
//            if (null == session.getUpdateTime() || session.getUpdateTime().getTime() < message.getCreateTime().getTime()) {
//                session.setUpdateTime(message.getCreateTime());
//            }
//        }
//    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTitle(Long userId, Long sessionId, String title) {
        SessionInfo session = this.getById(userId, sessionId);
        if (session == null) {
            return false;
        }
        SessionInfo update = new SessionInfo();
        update.setTitle(title);
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(session.getVersionNum());
        // 设置条件
        UpdateWrapper<SessionInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", session.getId())
                .eq("user_id", userId);
        int rows = sessionInfoMapper.update(update, wrapper);
        log.info("session title updated, id={}, rows={}", session.getId(), rows);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAgent(Long userId, Long sessionId, String agent) {
        SessionInfo session = this.getById(userId, sessionId);
        if (session == null) {
            return false;
        }
        SessionInfo update = new SessionInfo();
        update.setAgent(agent);
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(session.getVersionNum());
        // 设置条件
        UpdateWrapper<SessionInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", session.getId())
                .eq("user_id", userId);
        int rows = sessionInfoMapper.update(update, wrapper);
        log.info("session authLevel updated, id={}, rows={}", session.getId(), rows);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAuthLevel(Long userId, Long sessionId, Integer authLevel) {
        SessionInfo session = this.getById(userId, sessionId);
        if (session == null) {
            return false;
        }
        SessionInfo update = new SessionInfo();
        update.setAuthLevel(authLevel);
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(session.getVersionNum());
        // 设置条件
        UpdateWrapper<SessionInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", session.getId())
                .eq("user_id", userId);
        int rows = sessionInfoMapper.update(update, wrapper);
        log.info("session authLevel updated, id={}, rows={}", session.getId(), rows);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long sessionId) {
        SessionInfo session = this.getById(userId, sessionId);
        if (session == null) {
            return;
        }
        SessionInfo update = new SessionInfo();
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(session.getVersionNum());
        update.setDeleted(true);
        // 设置条件
        UpdateWrapper<SessionInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", session.getId())
                .eq("user_id", userId);
        int rows = sessionInfoMapper.update(update, wrapper);
        log.info("session delete, id={}, rows={}", session.getId(), rows);
    }

    public MessageService getMessageService() {
        return messageService;
    }
}


