package com.matrix.service.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.service.dal.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户信息表 Mapper 接口
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 根据用户名查询用户
     */
    UserInfo selectByUsername(@Param("username") String username);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(@Param("username") String username);

    /**
     * 检查手机号是否存在
     */
    boolean existsByPhone(@Param("phone") String phone);

    /**
     * 根据手机号查询用户
     */
    UserInfo selectByPhone(@Param("phone") String phone);

}


