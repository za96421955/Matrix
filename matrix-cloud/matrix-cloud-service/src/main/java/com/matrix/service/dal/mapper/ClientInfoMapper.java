package com.matrix.service.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.service.dal.entity.ClientInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 终端表 Mapper 接口
 */
@Mapper
public interface ClientInfoMapper extends BaseMapper<ClientInfo> {

    /**
     * 根据 client_id 查询客户端
     */
    ClientInfo selectByClientId(@Param("user_id") Long userId, @Param("client_id") String clientId);

    /**
     * 根据 user_id 查询客户端列表
     */
    List<ClientInfo> selectByUserId(@Param("user_id") Long userId);

}
