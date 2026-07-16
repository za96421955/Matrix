package com.matrix.service.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.service.dal.entity.MessageInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息表 Mapper 接口
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageInfo> {
}
