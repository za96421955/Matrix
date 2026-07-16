package com.matrix.service.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.matrix.common.constant.ApiKeyStatus;
import com.matrix.common.constant.ClientType;
import com.matrix.common.constant.Constant;
import com.matrix.common.dto.response.UserResponse;
import com.matrix.common.enums.ErrorCode;
import com.matrix.common.exception.BusinessException;
import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.service.dal.entity.UserApiKey;
import com.matrix.service.dal.entity.UserInfo;
import com.matrix.service.dal.mapper.UserApiKeyMapper;
import com.matrix.service.dal.mapper.UserInfoMapper;
import com.matrix.service.service.agent.ModelService;
import com.matrix.service.service.user.ClientService;
import com.matrix.service.service.user.PasswordService;
import com.matrix.service.service.user.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 用户登录服务
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private UserApiKeyMapper userApiKeyMapper;

    @Resource
    private PasswordService passwordService;
    @Resource
    private ModelService modelService;
    @Resource
    private ClientService clientService;

    @Override
    public String extractApiKey(String authHeader) {
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.AUTH_HEADER_INVALID);
        }
        return authHeader.substring(7);
    }

    @Override
    public UserInfo getUserInfo(Long userId) {
        return userInfoMapper.selectById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse validateApiKey(String deviceId, String apiKey) {
        if (StringUtils.isBlank(apiKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "apiKey 不可为空");
        }
        // 查询 api key 信息
        String apiKeyHash = passwordService.generateStableHash(apiKey);
        UserApiKey userApiKey = userApiKeyMapper.selectByApiKey(apiKeyHash);
        // api key 不存在, 注册用户
        if (null == userApiKey) {
            return this.register(deviceId, apiKey);
        }
        if (ApiKeyStatus.DISABLED.equals(userApiKey.getStatus())) {
            throw new BusinessException(ErrorCode.API_KEY_DISABLED, "apiKey=" + apiKeyHash + ", API Key 已禁用");
        }
        // 返回用户登录信息
        return this.login(userApiKey.getUserId());
    }

    /**
     * @description 用户注册
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private synchronized UserResponse register(String deviceId, String apiKey) {
        // 模型检查
        if (!modelService.checkApiKey(apiKey)) {
            return null;
        }
        String apiKeyHash = passwordService.generateStableHash(apiKey);

        // 模型检查通过, 查询用户、终端信息, deviceId 查询全部分表
        UserInfo userInfo = userInfoMapper.selectByUsername(apiKeyHash);
        // 1. user 不存在
        // 1.1. client 存在, 新增 api key
        // 1.2. client 不存在, 新增 user、client、api key
        // 2. user 存在
        // 2.1. client 不存在, 新增 client
        // 2.2. 新增 api key
        // 注册用户
        if (null == userInfo) {
            userInfo = new UserInfo();
            userInfo.setUsername(apiKeyHash);
            userInfo.setPasswordHash(apiKeyHash);
            userInfo.setCreateTime(new Date());
            userInfo.setCreator(Constant.SYSTEM_USER);
            userInfoMapper.insert(userInfo);
            log.info("[用户注册] 用户注册成功，user_id={}, username={}", userInfo.getId(), apiKeyHash);
            userInfo = userInfoMapper.selectByUsername(apiKeyHash);
        }

        // 添加终端
        ClientInfo client = clientService.getById(userInfo.getId(), deviceId);
        if (null == client) {
            clientService.save(userInfo.getId(), deviceId, ClientType.UNKNOWN, "");
            client = clientService.getById(userInfo.getId(), deviceId);
            log.info("[用户注册] 终端添加成功，user_id={}, deviceId={}", userInfo.getId(), deviceId);
        }
        // 绑定 api key
        userApiKeyMapper.insert(UserApiKey.builder()
                .userId(client.getUserId())
                .apiKey(apiKeyHash)
                .status(ApiKeyStatus.ENABLED)
                .createTime(new Date())
                .creator(Constant.SYSTEM_USER)
                .build());
        log.info("[用户注册] API Key 绑定成功，user_id={}, apiKey={}", client.getUserId(), apiKeyHash);

        // 3. user 登录
        return this.login(client.getUserId());
    }

    /**
     * @description 用户登录
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private UserResponse login(long userId) {
        // 1. 查询用户信息
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo == null) {
            log.error("[用户登录] 用户登录失败，userId={}, 原因：用户不存在", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 2. 构建返回信息
        UserResponse response = UserResponse.builder()
                .userId(userInfo.getId())
                .username(userInfo.getUsername())
                .authLevel(userInfo.getAuthLevel())
                .email(userInfo.getEmail())
                .phone(userInfo.getPhone())
                .apiKeys(new ArrayList<>())
                .build();
        // 3. 获取 ApiKey
        List<UserApiKey> apiKeyList = userApiKeyMapper.selectByUserId(userInfo.getId());
        apiKeyList.forEach(apiKey -> response.getApiKeys().add(UserResponse.ApiKey.builder()
                .apiKey(apiKey.getApiKey())
                .status(apiKey.getStatus())
                .build()));
        log.debug("[用户登录] 用户登录成功，user_id={}", userInfo.getId());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteApiKey(String apiKey) {
        UserApiKey userApiKey = userApiKeyMapper.selectByApiKey(apiKey);
        if (null == userApiKey) {
            return false;
        }
        // 更新心跳时间
        UserApiKey update = new UserApiKey();
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(userApiKey.getVersionNum());
        update.setDeleted(false);
        // 设置条件
        UpdateWrapper<UserApiKey> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userApiKey.getId());
        return userApiKeyMapper.update(update, wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enableApiKey(String apiKey) {
        UserApiKey userApiKey = userApiKeyMapper.selectByApiKey(apiKey);
        if (null == userApiKey) {
            return false;
        }
        // 更新心跳时间
        UserApiKey update = new UserApiKey();
        update.setStatus(ApiKeyStatus.ENABLED);
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(userApiKey.getVersionNum());
        // 设置条件
        UpdateWrapper<UserApiKey> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userApiKey.getId());
        return userApiKeyMapper.update(update, wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableApiKey(String apiKey) {
        UserApiKey userApiKey = userApiKeyMapper.selectByApiKey(apiKey);
        if (null == userApiKey) {
            return false;
        }
        // 更新心跳时间
        UserApiKey update = new UserApiKey();
        update.setStatus(ApiKeyStatus.DISABLED);
        update.setUpdateTime(new Date());
        update.setUpdator(Constant.SYSTEM_USER);
        update.setVersionNum(userApiKey.getVersionNum());
        // 设置条件
        UpdateWrapper<UserApiKey> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userApiKey.getId());
        return userApiKeyMapper.update(update, wrapper) > 0;
    }

}


