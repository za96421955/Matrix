package com.matrix.service.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.service.dal.entity.UserApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户访问密钥 Mapper 接口
 *
 * @since 2026-02-12
 */
@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKey> {

    /**
     * 根据 userId 查询用户 API Key 列表
     *
     * @param userId 用户ID
     * @return 用户 API Key 列表
     */
    List<UserApiKey> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据 apiKey 查询用户 API Key
     *
     * @param apiKey API Key
     * @return 用户 API Key
     */
    UserApiKey selectByApiKey(@Param("apiKey") String apiKey);

    /**
     * 根据 userId 和 status 查询用户 API Key 列表
     *
     * @param userId 用户ID
     * @param status 状态
     * @return 用户 API Key 列表
     */
    List<UserApiKey> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
}
