package com.matrix.service.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.service.dal.entity.SessionInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话表 Mapper 接口
 */
@Mapper
public interface SessionInfoMapper extends BaseMapper<SessionInfo> {
}
