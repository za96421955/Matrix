package com.matrix.service.service.user;

import com.matrix.service.dal.entity.UserInfo;
import com.matrix.common.dto.response.UserResponse;

/**
 * @description 用户服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface UserService {


    /**
     * 从 Authorization header 中提取 API Key
     * @param authHeader Authorization header 值
     * @return API Key
     */
    String extractApiKey(String authHeader);

    /**
     * @description 获取用户信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    UserInfo getUserInfo(Long userId);

    /**
     * @description 验证 API Key
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    UserResponse validateApiKey(String deviceId, String apiKey);

    /**
     * @description 删除 API Key
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean deleteApiKey(String apiKey);

    /**
     * @description 启用 API Key
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean enableApiKey(String apiKey);

    /**
     * @description 禁用 API Key
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    boolean disableApiKey(String apiKey);

}


