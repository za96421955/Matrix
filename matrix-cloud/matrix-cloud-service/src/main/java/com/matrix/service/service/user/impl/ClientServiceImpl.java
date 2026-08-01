package com.matrix.service.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.matrix.common.constant.ClientStatus;
import com.matrix.common.constant.Constant;
import com.matrix.common.constant.SystemParam;
import com.matrix.common.dto.command.RegisterCommand;
import com.matrix.common.util.ClientDetectUtil;
import com.matrix.service.context.RegisterContext;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.dal.mapper.ClientInfoMapper;
import com.matrix.service.service.task.Executor;
import com.matrix.service.service.user.ClientService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 终端服务实现类
 */
@Slf4j
@Service
public class ClientServiceImpl implements ClientService {

    @Resource
    private ClientInfoMapper clientInfoMapper;

    @Resource
    private RegisterContext registerContext;
    @Resource
    private Executor executor;

    @Override
    /** 获取ByUserId属性值 */
    public List<ClientInfo> getByUserId(Long userId) {
        return clientInfoMapper.selectByUserId(userId);
    }

    @Override
    /** 获取ById属性值 */
    public ClientInfo getById(Long userId, String clientId) {
        LambdaQueryWrapper<ClientInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClientInfo::getClientId, clientId)
                .eq(ClientInfo::getDeleted, false);
        if (null != userId) {
            wrapper.eq(ClientInfo::getUserId, userId);
        }
        List<ClientInfo> list = clientInfoMapper.selectList(wrapper);
        return CollectionUtils.isEmpty(list) ? null : list.getFirst();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 获取ByUserIdAndOnline属性值 */
    public List<ClientInfo> getByUserIdAndOnline(Long userId) {
        List<ClientInfo> list = this.getByUserId(userId);
        List<ClientInfo> onlineList = new ArrayList<>();
        for (ClientInfo client : list) {
            if (null == client) {
                continue;
            }
            if (ClientStatus.ONLINE.equalsIgnoreCase(client.getStatus())
                    && this.checkOnline(userId, client.getClientId())) {
                onlineList.add(client);
            }
        }
        return onlineList;
    }

    @Override
    /** 保存数据 */
    public void save(Long userId, String clientId, String clientType, String osInfo) {
        ClientInfo newClientInfo = ClientInfo.builder()
                .userId(userId)
                .clientId(clientId)
                .type(clientType)
                .osInfo(osInfo)
                .status(ClientStatus.ONLINE)
                .lastHeartbeat(new Date())
                .createTime(new Date())
                .creator(Constant.SYSTEM_USER)
                .build();
        clientInfoMapper.insert(newClientInfo);
        log.info("client auto register, userId={}, clientId={}, result=success", userId, clientId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 注册客户端或服务 */
    public void register(Long userId, String clientId, RegisterCommand registerCommand) {
//        log.info("client register start, userId={}, clientId={}", userId, clientId);
        // 查询设备是否已存在
        ClientInfo client = clientInfoMapper.selectByClientId(userId, clientId);
        if (client != null) {
            // 设备已注册，更新心跳时间
            log.info("client already exists, clientId={}", clientId);
            this.heartbeat(userId, clientId, registerCommand);
            return;
        }
        // 自动注册新设备
        this.save(userId, clientId,
                ClientDetectUtil.getClientType(registerCommand.getOsInfo()),
                registerCommand.getOsInfo());
        // 注册/刷新 用户缓存
        registerContext.register(userId, registerCommand);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** heartbeat操作 */
    public void heartbeat(Long userId, String clientId, RegisterCommand registerCommand) {
//        log.info("heartbeat received, userId={}, clientId={}", userId, clientId);
        // 验证设备是否存在
        ClientInfo client = clientInfoMapper.selectByClientId(userId, clientId);
        if (client == null) {
            log.warn("设备不存在，deviceId={}", clientId);
            return;
        }
        // 检查心跳频率（防刷）
        if (client.getLastHeartbeat() != null) {
            long interval = System.currentTimeMillis() - client.getLastHeartbeat().getTime();
            if (interval < SystemParam.HEARTBEAT_RATE_LIMIT_MS) {
                log.warn("心跳过于频繁，deviceId={}, interval={}ms", clientId, interval);
                return;
            }
        }
        // 更新心跳时间
        ClientInfo update = new ClientInfo();
        update.setType(ClientDetectUtil.getClientType(registerCommand.getOsInfo()));
        update.setOsInfo(registerCommand.getOsInfo());
        update.setStatus(ClientStatus.ONLINE);
        update.setLastHeartbeat(new Date());
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(client.getVersionNum());
        // 设置条件
        UpdateWrapper<ClientInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", client.getId())
                .eq("user_id", userId);
        int rows = clientInfoMapper.update(update, wrapper);
//        log.info("heartbeat updated, deviceId={}, rows={}", clientId, rows);
        // 注册/刷新 用户缓存
        registerContext.register(userId, registerCommand);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** checkOnline操作 */
    public boolean checkOnline(Long userId, String clientId) {
        ClientInfo client = clientInfoMapper.selectByClientId(userId, clientId);
        if (client == null) {
            return false;
        }
        boolean isOnline = ClientStatus.ONLINE.equals(client.getStatus());
        // 指令检查
        String osInfo = this.getOsInfo(clientId);
        boolean hasOsInfo = StringUtils.isNotBlank(osInfo);
        if (isOnline == hasOsInfo) {
            return isOnline;
        }
        // 更新终端状态
        ClientInfo update = new ClientInfo();
        update.setType(ClientDetectUtil.getClientType(osInfo));
        update.setOsInfo(osInfo);
        update.setStatus(hasOsInfo ? ClientStatus.ONLINE : ClientStatus.OFFLINE);
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(client.getVersionNum());
        // 设置条件
        UpdateWrapper<ClientInfo> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", client.getId())
                .eq("user_id", userId);
        clientInfoMapper.update(update, wrapper);
        return isOnline;
    }

    /**
     * @description 获取终端系统信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String getOsInfo(String clientId) {
        try {
            return executor.executeCommand(clientId, Constant.SYSTEM_COMMAND.OS_INFO).block();
        } catch (Exception e) {
            log.error("clientId={}, 终端系统信息获取失败: {}", clientId, e.getMessage(), e);
        }
        return null;
    }

}


